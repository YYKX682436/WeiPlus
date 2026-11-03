package h.Hchat.hooks.items.antirecall

import h.Hchat.hooks.api.model.WeChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

internal class RecallEventGate(private val maxEntries: Int) {
    private val handled = LinkedHashSet<String>()
    private val inFlight = HashSet<String>()

    @Synchronized
    fun begin(key: String): Boolean {
        if (key.isBlank() || key in handled || key in inFlight) return false
        inFlight += key
        return true
    }

    @Synchronized
    fun complete(key: String) {
        inFlight.remove(key)
        if (key.isBlank()) return
        handled += key
        while (handled.size > maxEntries) {
            val iterator = handled.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    @Synchronized
    fun abort(key: String) {
        inFlight.remove(key)
    }

    @Synchronized
    fun clear() {
        handled.clear()
        inFlight.clear()
    }
}

internal object RecallMessageResolver {
    fun resolve(
        talker: String,
        serverIds: List<Long>,
        hinted: WeChatMessage?,
        cachedByServerId: (String, Long) -> WeChatMessage?,
        storedByServerId: (String, Long) -> WeChatMessage?
    ): WeChatMessage? {
        val ids = serverIds.asSequence().filter { it > 0L }.distinct().toList()
        if (matches(hinted, talker, ids)) return hinted
        for (id in ids) {
            val cached = cachedByServerId(talker, id)
            if (matches(cached, talker, listOf(id))) return cached
            val stored = storedByServerId(talker, id)
            if (matches(stored, talker, listOf(id))) return stored
        }
        return null
    }

    fun matches(message: WeChatMessage?, talker: String, serverIds: List<Long>): Boolean {
        return message != null && talker.isNotBlank() &&
            message.talker == talker &&
            message.msgSvrId > 0L && message.msgSvrId in serverIds &&
            !message.isRecalled()
    }
}

internal object RecallNoticePolicy {
    data class EventDecision(
        val suppressOriginalRecall: Boolean,
        val markEventHandled: Boolean
    )

    fun shouldPreserve(antiRecallEnabled: Boolean, selfRecall: Boolean, keepSelfRecall: Boolean): Boolean {
        return antiRecallEnabled && (!selfRecall || keepSelfRecall)
    }

    fun decideEvent(
        antiRecallEnabled: Boolean,
        selfRecall: Boolean,
        keepSelfRecall: Boolean,
        showNotice: Boolean,
        noticeInserted: Boolean
    ): EventDecision {
        val suppressOriginal = shouldPreserve(antiRecallEnabled, selfRecall, keepSelfRecall)
        return EventDecision(
            suppressOriginalRecall = suppressOriginal,
            markEventHandled = suppressOriginal && (!showNotice || noticeInserted)
        )
    }

    fun shouldInsertNotice(
        antiRecallEnabled: Boolean,
        showNotice: Boolean,
        selfRecall: Boolean,
        keepSelfRecall: Boolean
    ): Boolean {
        return shouldPreserve(antiRecallEnabled, selfRecall, keepSelfRecall) && showNotice
    }

    fun normalizeMillis(createTime: Long): Long {
        return if (createTime > 0L && createTime < 10_000_000_000L) createTime * 1000L else createTime
    }

    fun formatTime(time: Long, pattern: String, fallbackPattern: String): String {
        if (time <= 0L) return ""
        return runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
        }.getOrElse {
            SimpleDateFormat(fallbackPattern, Locale.getDefault()).format(Date(time))
        }
    }
}
