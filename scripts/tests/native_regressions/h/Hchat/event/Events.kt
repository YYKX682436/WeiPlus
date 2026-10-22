package h.Hchat.event

object Events {
    data class MessageReceived(
        val outgoing: Boolean,
        val content: String? = null,
        val xml: String? = null,
        val msgSource: String? = null,
        val msgType: String? = null,
        val sender: String? = null
    )
}
