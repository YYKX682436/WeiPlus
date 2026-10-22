package h.Hchat.hooks.api.message

class WeChatMessageObserveApi {
    data class ObservedMessage(
        val isSend: Boolean,
        val rawContent: String?,
        val xml: String?,
        val rawMsgSource: String?,
        val rawType: Int?,
        val rawSendTalker: String?
    ) {
        fun getContent(): String = rawContent.orEmpty()
        fun getMsgSource(): String = rawMsgSource.orEmpty()
        fun getType(): Int = rawType ?: 0
        fun getSendTalker(): String = rawSendTalker.orEmpty()
    }
}
