package h.Hchat.hooks.api.runtime

import h.Hchat.hooks.api.model.DatabaseChange

class WeChatDatabaseListenerApi {
    val isAvailable: Boolean = true
    private var listener: ((DatabaseChange?) -> Unit)? = null
    var installCalls: Int = 0
        private set

    fun install() { installCalls++ }

    fun subscribe(callback: (DatabaseChange?) -> Unit): Subscription {
        listener = callback
        return Subscription { if (listener === callback) listener = null }
    }

    fun emit(change: DatabaseChange?) { listener?.invoke(change) }

    class Subscription(private val remove: () -> Unit) {
        fun unsubscribe() = remove()
    }
}
