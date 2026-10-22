package h.Hchat.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class EventBus {
    internal data class Entry(val callback: (Any) -> Unit)
    private val listeners = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Entry>>()
    private val sticky = ConcurrentHashMap<Class<*>, Any>()

    fun <E : Any> subscribe(type: Class<E>, handler: EventHandler<E>): Subscription {
        val entry = Entry { raw -> handler.handleEvent(type.cast(raw)) }
        listeners.computeIfAbsent(type) { CopyOnWriteArrayList() }.add(entry)
        sticky[type]?.let(entry.callback)
        return Subscription(type, entry)
    }

    fun unsubscribe(subscription: Subscription) {
        listeners[subscription.type]?.remove(subscription.entry)
    }

    fun <E : Any> post(event: E) {
        listeners[event.javaClass]?.forEach { it.callback(event) }
    }

    fun <E : Any> postSticky(event: E) {
        sticky[event.javaClass] = event
        post(event)
    }

    fun subscriberCount(type: Class<*>): Int = listeners[type]?.size ?: 0

    class Subscription internal constructor(internal val type: Class<*>, internal val entry: Entry) {
        companion object {
            fun empty() = Subscription(Any::class.java, Entry {})
        }
    }
}
