package h.Hchat.hooks.api.message

import h.Hchat.hooks.api.model.WeChatMessageTypes

internal class NativeSystemMessageInsertReceipt(
    private val talker: String,
    private val expectedCreateTime: Long?
) {
    data class InsertedMessage(val messageId: Long, val content: String)

    @Volatile private var insertedMessage: InsertedMessage? = null

    @Synchronized
    fun record(
        actualTalker: String?,
        actualContent: String?,
        actualType: Int,
        actualCreateTime: Long,
        insertedMessageId: Long
    ) {
        if (insertedMessage != null || insertedMessageId <= 0L) return
        if (actualTalker != talker || actualContent == null || actualType != WeChatMessageTypes.SYSTEM) return
        if (expectedCreateTime != null && actualCreateTime != expectedCreateTime) return
        insertedMessage = InsertedMessage(insertedMessageId, actualContent)
    }

    fun insertedMessage(): InsertedMessage? = insertedMessage
}
