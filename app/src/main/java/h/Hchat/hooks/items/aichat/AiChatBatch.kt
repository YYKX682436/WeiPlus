package h.Hchat.hooks.items.aichat

import h.Hchat.hooks.api.model.WeChatMessage

/** One conversation stays serial; group messages from different members are never merged. */
internal class AiChatBatch {
    private data class Entry(val message: WeChatMessage, val addedAt: Long)
    private val entries = mutableListOf<Entry>()

    fun add(message: WeChatMessage, now: Long) { entries += Entry(message, now) }
    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    private fun head(): List<Entry> {
        val first = entries.firstOrNull() ?: return emptyList()
        if (!first.message.isGroupChat()) return entries.toList()
        val sender = AiImageContext.sender(first.message) ?: return listOf(first)
        return entries.takeWhile { AiImageContext.sender(it.message) == sender }
    }

    fun waitMillis(now: Long): Long {
        val batch = head()
        if (batch.isEmpty()) return 0
        val first = batch.first().addedAt
        val last = batch.last().addedAt
        val image = batch.lastOrNull { it.message.isImage() }?.addedAt
        return AiChatRuntime.debounceWait(first, last, now, image)
    }

    fun take(): List<WeChatMessage> {
        val batch = head()
        repeat(batch.size) { entries.removeAt(0) }
        return batch.map { it.message }
    }
}
