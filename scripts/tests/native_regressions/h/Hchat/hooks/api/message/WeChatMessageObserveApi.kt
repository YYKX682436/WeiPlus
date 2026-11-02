package h.Hchat.hooks.api.message

import h.Hchat.hooks.api.model.WeChatMessage
import java.util.concurrent.CopyOnWriteArrayList

class WeChatMessageObserveApi(var isAvailable: Boolean = true) {
    fun interface Listener {
        fun onObservedMessage(message: ObservedMessage)
    }

    object Kind {
        const val TEXT = "text"
        const val IMAGE = "image"
        const val VOICE = "voice"
        const val VIDEO = "video"
        const val EMOJI = "emoji"
        const val APP = "app"
        const val LOCATION = "location"
        const val SYSTEM = "system"
        const val UNKNOWN = "unknown"
    }

    class ObservedMessage(
        val isSend: Boolean,
        val rawContent: String?,
        val xml: String?,
        val rawMsgSource: String?,
        val rawType: Int?,
        val rawSendTalker: String?,
        val source: String = "add_msg",
        val kind: String = Kind.UNKNOWN,
        val talker: String = "",
        val sender: String = "",
        val group: Boolean = false,
        val message: WeChatMessage? = null
    ) {
        fun getContent(): String = rawContent.orEmpty()
        fun getMsgSource(): String = rawMsgSource.orEmpty()
        fun getType(): Int = rawType ?: 0
        fun getSendTalker(): String = rawSendTalker.orEmpty()

        companion object {
            fun transientMessage(
                source: String,
                kind: String,
                talker: String,
                sender: String,
                content: String,
                xml: String,
                nativeUrl: String,
                group: Boolean,
                outgoing: Boolean,
                message: WeChatMessage
            ) = ObservedMessage(outgoing, content, xml, message.msgSource, message.type,
                message.sendTalker(), source, kind, talker, sender, group, message)
        }
    }

    private val listeners = CopyOnWriteArrayList<Subscription>()
    var subscribeCalls = 0
        private set
    val subscriberCount: Int get() = listeners.size

    fun subscribe(listener: Listener): Subscription {
        subscribeCalls++
        val subscription = Subscription(listener)
        listeners += subscription
        return subscription
    }

    fun emit(message: ObservedMessage) = listeners.forEach { it.listener.onObservedMessage(message) }

    inner class Subscription(internal val listener: Listener) {
        @Volatile private var active = true
        val isActive: Boolean get() = active
        fun unsubscribe() {
            if (!active) return
            active = false
            listeners.remove(this)
        }
    }
}
