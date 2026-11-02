package h.Hchat.hooks.core

import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import java.lang.ref.WeakReference

/** Owns one replaceable business subscription to the shared message observer. */
internal class MessageObserveLease {
    private val lock = Any()
    private var observer: WeChatMessageObserveApi? = null
    private var subscriptionObserver: WeChatMessageObserveApi? = null
    private var callback: WeChatMessageObserveApi.Listener? = null
    private var subscription: WeChatMessageObserveApi.Subscription? = null
    private var requested = false
    private var closed = false
    private var generation = 0L

    fun sync(
        currentObserver: WeChatMessageObserveApi?,
        enabled: Boolean,
        listener: WeChatMessageObserveApi.Listener?
    ) {
        synchronized(lock) {
            if (closed) return
            if (currentObserver != null) observer = currentObserver
            if (!enabled || listener == null) {
                detachLocked()
                observer = null
                return
            }
            val target = observer ?: return
            if (requested &&
                callback === listener &&
                subscription?.isActive == true &&
                subscriptionObserver === target
            ) return

            detachLocked()
            requested = true
            callback = listener
            val callbackGeneration = ++generation
            val callbackReference = WeakReference(listener)
            val created = target.subscribe(WeChatMessageObserveApi.Listener { message ->
                val receiver = callbackReference.get() ?: return@Listener
                val isCurrent = synchronized(lock) {
                    !closed && requested && generation == callbackGeneration && callback === receiver
                }
                if (isCurrent) receiver.onObservedMessage(message)
            })
            if (created?.isActive == true) {
                subscription = created
                subscriptionObserver = target
            } else {
                // Keep only the desired callback. A later DexReady/configuration update can retry.
                subscription = null
                subscriptionObserver = null
            }
        }
    }

    fun isActive(): Boolean = synchronized(lock) {
        !closed && requested && subscription?.isActive == true
    }

    fun unsubscribe() {
        synchronized(lock) {
            if (closed) return
            closed = true
            detachLocked()
            observer = null
        }
    }

    private fun detachLocked() {
        requested = false
        generation++
        val old = subscription
        subscription = null
        subscriptionObserver = null
        callback = null
        old?.unsubscribe()
    }
}
