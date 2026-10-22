package h.Hchat.hooks.items.sendbutton

import java.util.concurrent.CopyOnWriteArrayList

/** Low-allocation, thread-safe subscription list for native send-button consumers. */
internal class NativeSendButtonHandlers(
    private val onError: (String, Throwable) -> Unit
) {
    private val entries = CopyOnWriteArrayList<Entry>()

    fun register(id: String, handler: (String) -> Boolean): () -> Unit {
        require(id.isNotBlank()) { "Handler id must not be blank" }
        entries.removeAll { it.id == id }
        val entry = Entry(id, handler)
        entries += entry
        return { entries.remove(entry) }
    }

    fun dispatch(text: String): Boolean {
        for (entry in entries) {
            val handled = try {
                entry.handler(text)
            } catch (error: Throwable) {
                onError(entry.id, error)
                false
            }
            if (handled) return true
        }
        return false
    }

    fun clear() = entries.clear()

    private data class Entry(val id: String, val handler: (String) -> Boolean)
}
