package h.Hchat.hooks.items.hchatextra

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Values may point back to the weak key; only the row itself may own them strongly. */
internal class MessageDetailsBindingIndex<K : Any, V : Any> {
    private val bindings = WeakHashMap<K, WeakReference<V>>()
    @Synchronized operator fun get(key: K): V? = bindings[key]?.get()
    @Synchronized operator fun set(key: K, value: V) { bindings[key] = WeakReference(value) }
    @Synchronized fun remove(key: K) { bindings.remove(key) }
    @Synchronized fun clear() { bindings.clear() }
    @Synchronized fun snapshot(): List<Pair<K, V>> =
        bindings.entries.mapNotNull { (key, ref) -> ref.get()?.let { key to it } }
}
