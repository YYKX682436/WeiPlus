package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.media.WeChatModernVideoSender
import h.Hchat.hooks.api.message.WeChatMessageChangeApi
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.sendbutton.NativeSendButtonHook
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
        track(NativeSendButtonHook.registerHandler(LinkParseFeature.ID, ::onSendButton))
        HLog.i("[Hchat:LinkParse] 监听已注册")
        WeChatApis.message().changes()?.let { changes ->
            runCatching { changes.install() }
            changes.subscribe({ it.isInsert() && settings.enabled() }, ::onChange)?.let(track)
        }
    }

    fun destroy() {
        stopped = true
        executor.shutdownNow()
        cleaner.shutdownNow()
        synchronized(pending) { pending.clear() }
    }

    private fun onChange(change: WeChatMessageChangeApi.MessageChange) {
        val message = change.message ?: return
        if (!change.isInsert) return
        if (!LinkParseEntryPolicy.acceptDatabase(message.isOutgoing())) return
        val timestamp = LinkParseMessageGate.eligibleTime(message, System.currentTimeMillis()) ?: return
        val group = message.isGroupChat()
        val self = if (group && settings.groupMode() == LinkParseSettings.GROUP_AT) {
            WeChatApis.contact().account()?.selfWxId().orEmpty()
        } else ""
        val atMe = self.isNotEmpty() && message.isAtMe(self)
        val content = message.bodyContent()
        HLog.i("[Hchat:LinkParse] 收到数据库文本")
        val match = WorkLinkMatcher.find(content) ?: run { HLog.i("[Hchat:LinkParse] 未识别作品链接"); return }
        if (!settings.allows(match.platform, group, atMe)) return
        val key = key(message.talker, message.msgId)
        submit(message.talker, match, group, atMe, key, timestamp + LinkParseMessageGate.MAX_AGE + LinkParseMessageGate.FUTURE_TOLERANCE)
    }

    private fun onSendButton(text: String): Boolean {
        if (!settings.enabled() || text.length > LinkParseMessageGate.MAX_TEXT) return false
        HLog.i("[Hchat:LinkParse] 收到发送按钮文本")
        val talker = WeChatApis.interaction().chatPage()?.currentTalker().orEmpty()
        if (talker.isBlank()) { HLog.i("[Hchat:LinkParse] 未取得当前聊天"); return false }
        val match = WorkLinkMatcher.find(text) ?: run { HLog.i("[Hchat:LinkParse] 未识别作品链接"); return false }
        val group = talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom")
        if (settings.allows(match.platform, group, true)) submit(talker, match, group, true, null, 0L)
        return false
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
        val url = client.resolve(match) ?: run { HLog.i("[Hchat:LinkParse] 解析失败"); return }
        val target = File(dir, "video_${System.currentTimeMillis()}_${System.nanoTime()}.mp4")
        var submitted = false
        try {
            if (!client.download(url, match.platform, target)) { HLog.i("[Hchat:LinkParse] 下载失败"); return }
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
