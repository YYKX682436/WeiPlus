package h.Hchat.hooks.items.autoreply

import java.util.concurrent.ConcurrentHashMap

/** The fallback event path stays active until a usable observer subscription is attached. */
internal class AutoReplyObservationState {
    private enum class Mode { STOPPED, FALLBACK, OBSERVER }

    @Volatile private var mode = Mode.STOPPED

    @Synchronized
    fun startFallback(): Boolean {
        if (mode != Mode.STOPPED) return false
        mode = Mode.FALLBACK
        return true
    }

    @Synchronized
    fun activateObserver(): Boolean {
        if (mode == Mode.STOPPED) return false
        mode = Mode.OBSERVER
        return true
    }

    fun acceptsFallback(): Boolean = mode == Mode.FALLBACK

    fun acceptsObserved(): Boolean = mode == Mode.OBSERVER

    @Synchronized
    fun stop() {
        mode = Mode.STOPPED
    }
}

/** Bounded duplicate suppression across the raw-event and observed-message routes. */
internal class AutoReplyMessageDeduplicator(
    private val ttlMillis: Long = 30_000L,
    private val maxEntries: Int = 512
) {
    private val seenAt = ConcurrentHashMap<String, Long>()

    fun claim(key: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (key.isNullOrBlank()) return true
        while (true) {
            val previous = seenAt.putIfAbsent(key, now)
            if (previous == null) break
            if (now - previous < ttlMillis) return false
            if (seenAt.replace(key, previous, now)) break
        }
        if (seenAt.size > maxEntries) {
            seenAt.entries.removeIf { now - it.value >= ttlMillis }
            if (seenAt.size > maxEntries) {
                seenAt.entries.sortedBy { it.value }
                    .take(seenAt.size - maxEntries)
                    .forEach { seenAt.remove(it.key, it.value) }
            }
        }
        return true
    }

    fun clear() = seenAt.clear()
}

/** A finite retry budget prevents a missing Dex API from creating a permanent timer. */
internal class AutoReplyObservationRetryBudget(private val maximum: Int) {
    private var scheduled = 0

    @Synchronized
    fun reserve(): Int? {
        if (scheduled >= maximum) return null
        return scheduled++
    }

    @Synchronized
    fun reset() {
        scheduled = 0
    }
}
