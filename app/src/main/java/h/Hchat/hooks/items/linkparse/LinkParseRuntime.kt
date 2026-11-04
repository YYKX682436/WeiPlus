package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.media.WeChatModernVideoSender
import h.Hchat.hooks.api.message.WeChatMessageChangeApi
import h.Hchat.hooks.core.FeatureContext
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
        val timestamp = LinkParseMessageGate.eligibleTime(message, System.currentTimeMillis()) ?: return
        val group = message.isGroupChat()
        val self = if (group && settings.groupMode() == LinkParseSettings.GROUP_AT) {
            WeChatApis.contact().account()?.selfWxId().orEmpty()
        } else ""
        val atMe = self.isNotEmpty() && message.isAtMe(self)
        val content = message.bodyContent()
        val match = WorkLinkMatcher.find(content) ?: return
        if (!settings.allows(match.platform, group, atMe)) return
        val key = key(message.talker, message.msgId)
        synchronized(pending) {
            if (stopped || !pending.add(key)) return
        }
        try {
            executor.execute {
                try {
                    if (!stopped && claims.claim(key, timestamp + LinkParseMessageGate.MAX_AGE + LinkParseMessageGate.FUTURE_TOLERANCE, System.currentTimeMillis())) {
                        process(message.talker, match, group, atMe)
                    }
                } catch (_: Throwable) {
                    // Parsing and sending failures are intentionally silent.
                } finally {
                    synchronized(pending) { pending.remove(key) }
                }
            }
        } catch (_: RejectedExecutionException) {
            synchronized(pending) { pending.remove(key) }
        }
    }

    private fun process(talker: String, match: WorkLinkMatcher.Match, group: Boolean, atMe: Boolean) {
        if (stopped || !settings.allows(match.platform, group, atMe)) return
        val dir = File(host.cacheDir, CACHE_DIR)
        if ((!dir.isDirectory && !dir.mkdirs()) || !dir.canWrite()) return
        cleanup(dir)
        if ((dir.listFiles()?.sumOf { it.length().coerceAtLeast(0) } ?: 0L) > MAX_CACHE_BYTES - MAX_VIDEO_BYTES) return
        val url = client.resolve(match) ?: return
        val target = File(dir, "video_${System.currentTimeMillis()}_${System.nanoTime()}.mp4")
        var submitted = false
        try {
            if (!client.download(url, match.platform, target) || stopped || !settings.allows(match.platform, group, atMe)) return
            val video = WeChatApis.media()?.videos() ?: return
            submitted = if (video.canSendSilently()) video.send(talker, target.absolutePath) else {
                val sender = modern ?: WeChatModernVideoSender(context.dexBridgeHolder(), video).also { modern = it }
                sender.send(talker, target.absolutePath)
            }
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
