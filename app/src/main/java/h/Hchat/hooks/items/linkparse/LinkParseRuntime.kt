package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.media.WeChatModernVideoSender
import h.Hchat.hooks.api.message.WeChatMessageChangeApi
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.utils.KavaReflector
import io.github.libxposed.api.XposedInterface
import h.Hchat.utils.HLog
import h.Hchat.preferences.HchatStorage
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class LinkParseRuntime(private val context: FeatureContext) {
    private val host = context.hostContext().applicationContext ?: context.hostContext()
    private val settings = LinkParseSettings(host)
    private val claims = MessageClaims(HchatStorage.preferences(host, HISTORY_PREFS))
    private val client = WorkVideoClient()
    private val pending = HashSet<String>()
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(3), { r ->
        Thread(r, "HchatLinkParse").apply { isDaemon = true }
    }, ThreadPoolExecutor.AbortPolicy())
    private val cleaner = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "HchatLinkParseCleanup").apply { isDaemon = true }
    }
    @Volatile private var stopped = false
    private var modern: WeChatModernVideoSender? = null

    fun install(track: (Any) -> Unit) {
        cleaner.scheduleWithFixedDelay({ runCatching { cleanup(File(host.cacheDir, CACHE_DIR)) } }, 0, 30, TimeUnit.MINUTES)
        DexInstallScheduler.schedule("link_parse_message_write", "链接解析消息写入", DexInstallScheduler.Stage.WARMUP) { installMessageWriteHook() }
        val changes = WeChatApis.message().changes()
        if (changes == null) HLog.i("[Hchat:LinkParse] 数据库监听不可用") else {
            runCatching { changes.install() }.onFailure { HLog.i("[Hchat:LinkParse] 数据库监听安装失败") }
            val subscription = changes.subscribe({ (it.isInsert() || it.isUpdate()) && settings.enabled() }, ::onChange)
            if (subscription == null) HLog.i("[Hchat:LinkParse] 数据库订阅失败") else { track(subscription); HLog.i("[Hchat:LinkParse] 数据库订阅已注册") }
        }
    }

    fun destroy() {
        stopped = true
        DexInstallScheduler.cancel("link_parse_message_write")
        executor.shutdownNow()
        cleaner.shutdownNow()
        synchronized(pending) { pending.clear() }
    }

    private fun onChange(change: WeChatMessageChangeApi.MessageChange) {
        val message = change.message ?: return
        val values = change.databaseChange?.values
        val status = if (values?.containsKey("status") == true) runCatching { values.getAsInteger("status") }.getOrNull() else null
        if (!LinkParseEntryPolicy.acceptDatabase(change.isInsert, change.isUpdate, message.isOutgoing(), message.status, status)) return
        handleMessage(message)
    }

    private fun installMessageWriteHook(): Boolean {
        if (stopped) return false
        val classes = context.dexBridgeHolder().findClassesByStrings(
            "CREATE INDEX IF NOT EXISTS messageSendCreateTimeIndex ON message ( status,isSend,createTime )"
        ).mapNotNull { runCatching { context.hostClassLoader().loadClass(it) }.getOrNull() }.distinct()
        val candidates = classes.flatMap { cls -> cls.declaredMethods.filter { method ->
            val p = method.parameterTypes
            p.size == 3 && p[0] == Long::class.javaPrimitiveType && p[2] == Boolean::class.javaPrimitiveType &&
                method.returnType == Int::class.javaPrimitiveType &&
                KavaReflector.findFieldRecursive(p[1], "field_status") != null &&
                KavaReflector.findFieldRecursive(p[1], "field_isSend") != null
        } }
        if (candidates.size != 1) { HLog.i("[Hchat:LinkParse] 消息写入定位不可用"); return false }
        HookRegistry.get().hookOnce("link_parse:message_write", candidates.single(), XposedInterface.Hooker { chain ->
            val args = chain.args
            val result = chain.proceed()
            try {
                if (!stopped && settings.enabled()) {
                    val id = (args.getOrNull(0) as? Number)?.toLong() ?: 0L
                    LinkParseWriteGate.observe((result as? Number)?.toInt(), id, args.getOrNull(1), { key ->
                        WeChatApis.message().store()?.getMessageById(key)
                    }, ::handleMessage)
                }
            } catch (_: Throwable) { HLog.i("[Hchat:LinkParse] 消息写入观察失败") }
            result
        })
        HLog.i("[Hchat:LinkParse] 消息写入观察已安装")
        return true
    }

    private fun handleMessage(message: WeChatMessage) {
        val timestamp = LinkParseMessageGate.eligibleTime(message, System.currentTimeMillis()) ?: return
        val group = message.isGroupChat()
        val self = if (group && settings.groupMode() == LinkParseSettings.GROUP_AT) {
            WeChatApis.contact().account()?.selfWxId().orEmpty()
        } else ""
        val atMe = message.isOutgoing() || (self.isNotEmpty() && message.isAtMe(self))
        val content = message.bodyContent()
        HLog.i("[Hchat:LinkParse] 收到数据库文本")
        val match = WorkLinkMatcher.find(content) ?: run { HLog.i("[Hchat:LinkParse] 未识别作品链接"); return }
        if (!settings.allows(match.platform, group, atMe)) return
        val key = key(message.talker, message.msgId)
        submit(message.talker, match, group, atMe, key, timestamp + LinkParseMessageGate.MAX_AGE + LinkParseMessageGate.FUTURE_TOLERANCE)
    }

    private fun submit(talker: String, match: WorkLinkMatcher.Match, group: Boolean, atMe: Boolean, key: String?, expiry: Long) {
        if (key != null) synchronized(pending) {
            if (stopped || !pending.add(key)) return
        }
        try {
            executor.execute {
                try {
                    if (!stopped && (key == null || claims.claim(key, expiry, System.currentTimeMillis()))) {
                        process(talker, match, group, atMe)
                    }
                } catch (_: Throwable) {
                    HLog.i("[Hchat:LinkParse] 处理异常")
                } finally {
                    if (key != null) synchronized(pending) { pending.remove(key) }
                }
            }
        } catch (_: RejectedExecutionException) {
            if (key != null) synchronized(pending) { pending.remove(key) }
            HLog.i("[Hchat:LinkParse] 队列已满")
        }
    }

    private fun process(talker: String, match: WorkLinkMatcher.Match, group: Boolean, atMe: Boolean) {
        if (stopped || !settings.allows(match.platform, group, atMe)) return
        val dir = File(host.cacheDir, CACHE_DIR)
        if ((!dir.isDirectory && !dir.mkdirs()) || !dir.canWrite()) return
        cleanup(dir)
        if ((dir.listFiles()?.sumOf { it.length().coerceAtLeast(0) } ?: 0L) > MAX_CACHE_BYTES - MAX_VIDEO_BYTES) return
        val urls = client.resolve(match) ?: run { HLog.i("[Hchat:LinkParse] 解析失败"); return }
        val target = File(dir, "video_${System.currentTimeMillis()}_${System.nanoTime()}.mp4")
        var submitted = false
        try {
            if (!urls.any { url -> !stopped && client.download(url, match.platform, target) }) { HLog.i("[Hchat:LinkParse] 下载失败"); return }
            if (stopped || !settings.allows(match.platform, group, atMe)) return
            val video = WeChatApis.media()?.videos() ?: run { HLog.i("[Hchat:LinkParse] 发送接口不可用"); return }
            submitted = if (video.canSendSilently()) video.send(talker, target.absolutePath) else {
                val sender = modern ?: WeChatModernVideoSender(context.dexBridgeHolder(), video).also { modern = it }
                sender.send(talker, target.absolutePath)
            }
            if (!submitted) HLog.i("[Hchat:LinkParse] 发送失败")
        } finally {
            // Host sending is asynchronous; keep successful files for later age-based cleanup.
            if (!submitted && target.exists()) target.delete()
        }
    }

    private fun cleanup(dir: File) {
        val cutoff = System.currentTimeMillis() - CACHE_TTL
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith("video_") && f.lastModified() in 1 until cutoff) f.delete()
        }
    }

    private fun key(talker: String, id: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("$talker\u0000$id".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val HISTORY_PREFS = "Hchat_link_parse_history"
        private const val CACHE_DIR = "Hchat_link_parse"
        private const val CACHE_TTL = 6 * 60 * 60 * 1000L
        private const val MAX_CACHE_BYTES = 256L * 1024 * 1024
        private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024
    }
}
