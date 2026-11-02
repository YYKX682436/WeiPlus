package h.Hchat.hooks.items.customfriendavatar

import java.lang.ref.WeakReference
import java.util.LinkedHashMap

/** Coalesces a bitmap load while retaining only weak, binding-aware redraw waiters. */
internal class AvatarLoadCoordinator<T : Any>(
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
    private val maxFailureEntries: Int = DEFAULT_MAX_FAILURE_ENTRIES
) {
    private data class Waiter<T : Any>(
        val target: WeakReference<T>,
        val bindingToken: Long,
        val isCurrent: (T, Long) -> Boolean,
        val invalidate: (T) -> Unit
    )

    private val lock = Any()
    private val inFlight = HashMap<String, MutableList<Waiter<T>>>()
    private val failedUntil = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > maxFailureEntries.coerceAtLeast(1)
    }

    /** Returns true only for the caller that should perform this key's I/O. */
    fun request(
        key: String,
        target: T,
        bindingToken: Long,
        isCurrent: (T, Long) -> Boolean,
        invalidate: (T) -> Unit,
        nowMillis: Long = monotonicNowMillis()
    ): Boolean = synchronized(lock) {
        if (key.isBlank()) return@synchronized false
        val waiter = Waiter(WeakReference(target), bindingToken, isCurrent, invalidate)
        val existing = inFlight[key]
        if (existing != null) {
            existing.removeAll { it.target.get() == null }
            val targetIndex = existing.indexOfFirst { it.target.get() === target }
            if (targetIndex >= 0) existing[targetIndex] = waiter else existing += waiter
            return@synchronized false
        }

        val retryAt = failedUntil[key]
        if (retryAt != null && nowMillis < retryAt) return@synchronized false
        failedUntil.remove(key)
        inFlight[key] = mutableListOf(waiter)
        true
    }

    /** Returns short-lived callbacks for dispatch after releasing the coordinator lock. */
    fun complete(
        key: String,
        succeeded: Boolean,
        nowMillis: Long = monotonicNowMillis()
    ): List<() -> Unit> = synchronized(lock) {
        val waiters = inFlight.remove(key).orEmpty()
        if (succeeded) {
            failedUntil.remove(key)
        } else {
            failedUntil[key] = nowMillis + retryDelayMillis.coerceAtLeast(0L)
        }
        waiters.mapNotNull { waiter ->
            val target = waiter.target.get() ?: return@mapNotNull null
            {
                if (waiter.isCurrent(target, waiter.bindingToken)) waiter.invalidate(target)
            }
        }
    }

    fun clear() = synchronized(lock) {
        inFlight.clear()
        failedUntil.clear()
    }

    private fun monotonicNowMillis(): Long = System.nanoTime() / NANOS_PER_MILLI

    private companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 2_000L
        const val DEFAULT_MAX_FAILURE_ENTRIES = 128
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
