package h.Hchat.hooks.items.customfriendavatar

import java.lang.ref.WeakReference
import java.util.LinkedHashMap

internal fun interface AvatarRetryHandle {
    fun cancel()
}

/** Implementations must enqueue asynchronously and return a cancellation handle. */
internal fun interface AvatarRetryScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AvatarRetryHandle
}

/** Coalesces a bitmap load while retaining only weak, binding-aware redraw waiters. */
internal class AvatarLoadCoordinator<T : Any>(
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
    private val maxRetryDelayMillis: Long = DEFAULT_MAX_RETRY_DELAY_MILLIS,
    private val maxFailureEntries: Int = DEFAULT_MAX_FAILURE_ENTRIES,
    private val maxPendingRetries: Int = DEFAULT_MAX_PENDING_RETRIES,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val scheduler: AvatarRetryScheduler
) {
    private data class Waiter<T : Any>(
        val target: WeakReference<T>,
        val bindingToken: Long,
        val isCurrent: (T, Long) -> Boolean,
        val invalidate: (T) -> Unit,
        val retry: (T, Long) -> Unit
    )

    private data class FailureState(val attempts: Int, val retryAtMillis: Long)

    private data class RetryEntry<T : Any>(
        val generation: Long,
        val waiters: MutableList<Waiter<T>>,
        val handle: AvatarRetryHandle
    )

    private val lock = Any()
    private val inFlight = HashMap<String, MutableList<Waiter<T>>>()
    private val retries = LinkedHashMap<String, RetryEntry<T>>(16, 0.75f, true)
    private var nextRetryGeneration = 0L
    private val failures = object : LinkedHashMap<String, FailureState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FailureState>?): Boolean =
            size > maxFailureEntries.coerceAtLeast(1)
    }

    /** Returns true only for the caller that should perform this key's I/O. */
    fun request(
        key: String,
        target: T,
        bindingToken: Long,
        isCurrent: (T, Long) -> Boolean,
        invalidate: (T) -> Unit,
        retry: (T, Long) -> Unit,
        nowMillis: Long = clock()
    ): Boolean = synchronized(lock) {
        if (key.isBlank()) return@synchronized false
        val waiter = Waiter(WeakReference(target), bindingToken, isCurrent, invalidate, retry)
        val existing = inFlight[key]
        if (existing != null) {
            upsertWaiter(existing, waiter)
            return@synchronized false
        }

        val pendingRetry = retries[key]
        if (pendingRetry != null) {
            upsertWaiter(pendingRetry.waiters, waiter)
            val failure = failures[key]
            if (failure == null || nowMillis < failure.retryAtMillis) return@synchronized false
            retries.remove(key)
            pendingRetry.handle.cancel()
            inFlight[key] = pendingRetry.waiters
            return@synchronized true
        }

        val failure = failures[key]
        if (failure != null && nowMillis < failure.retryAtMillis) return@synchronized false
        inFlight[key] = mutableListOf(waiter)
        true
    }

    /** Returns short-lived callbacks for dispatch after releasing the coordinator lock. */
    fun complete(
        key: String,
        succeeded: Boolean,
        nowMillis: Long = clock()
    ): List<() -> Unit> = synchronized(lock) {
        val waiters = inFlight.remove(key) ?: return@synchronized emptyList()
        if (succeeded) {
            failures.remove(key)
        } else {
            scheduleRetryLocked(key, waiters, nowMillis)
        }
        waiters.mapNotNull(::invalidationCallback)
    }

    /** Cancels queued work for a rebound Drawable without affecting other waiters for its key. */
    fun cancelTarget(target: T) = synchronized(lock) {
        inFlight.values.forEach { waiters ->
            waiters.removeAll { it.target.get() == null || it.target.get() === target }
        }
        inFlight.entries.removeIf { it.value.isEmpty() }

        val emptyRetries = mutableListOf<String>()
        retries.forEach { (key, entry) ->
            entry.waiters.removeAll { it.target.get() == null || it.target.get() === target }
            if (entry.waiters.isEmpty()) {
                entry.handle.cancel()
                failures.remove(key)
                emptyRetries += key
            }
        }
        emptyRetries.forEach(retries::remove)
    }

    /** Cancels all avatar keys for a changed/deleted owner. */
    fun cancelKeysWithPrefix(prefix: String) = synchronized(lock) {
        inFlight.keys.filter { it.startsWith(prefix) }.forEach(inFlight::remove)
        retries.keys.filter { it.startsWith(prefix) }.forEach { key ->
            retries.remove(key)?.handle?.cancel()
            failures.remove(key)
        }
        failures.keys.filter { it.startsWith(prefix) }.forEach(failures::remove)
    }

    fun clear() = synchronized(lock) {
        retries.values.forEach { it.handle.cancel() }
        retries.clear()
        inFlight.clear()
        failures.clear()
    }

    private fun scheduleRetryLocked(key: String, waiters: MutableList<Waiter<T>>, nowMillis: Long) {
        if (waiters.isEmpty()) return
        retries.remove(key)?.let { previous ->
            previous.handle.cancel()
            previous.waiters.forEach { upsertWaiter(waiters, it) }
        }
        evictRetryIfNeededLocked(key)
        val attempts = (failures[key]?.attempts ?: 0) + 1
        val delayMillis = retryDelayFor(attempts)
        failures[key] = FailureState(attempts, saturatingAdd(nowMillis, delayMillis))
        val generation = ++nextRetryGeneration
        val handle = scheduler.schedule(delayMillis) { retryDue(key, generation) }
        retries[key] = RetryEntry(generation, waiters.toMutableList(), handle)
    }

    private fun evictRetryIfNeededLocked(incomingKey: String) {
        val limit = maxPendingRetries.coerceAtLeast(1)
        if (incomingKey in retries || retries.size < limit) return
        val eldestKey = retries.keys.firstOrNull() ?: return
        retries.remove(eldestKey)?.handle?.cancel()
        failures.remove(eldestKey)
    }

    private fun retryDue(key: String, generation: Long) {
        val waiters = synchronized(lock) {
            val entry = retries[key]?.takeIf { it.generation == generation } ?: return
            retries.remove(key)
            val current = entry.waiters.filter { waiter ->
                waiter.target.get()?.let { target -> waiter.isCurrent(target, waiter.bindingToken) } == true
            }
            if (current.isEmpty()) {
                failures.remove(key)
                emptyList()
            } else current
        }

        waiters.forEach { waiter ->
            runCatching {
                waiter.target.get()?.let { target ->
                    if (waiter.isCurrent(target, waiter.bindingToken)) {
                        waiter.retry(target, waiter.bindingToken)
                    }
                }
            }
        }
    }

    private fun retryDelayFor(attempts: Int): Long {
        val cap = maxRetryDelayMillis.coerceAtLeast(1L)
        var delay = retryDelayMillis.coerceIn(1L, cap)
        repeat((attempts - 1).coerceIn(0, MAX_BACKOFF_STEPS)) {
            delay = if (delay >= cap / 2L) cap else delay * 2L
        }
        return delay.coerceAtMost(cap)
    }

    private fun upsertWaiter(waiters: MutableList<Waiter<T>>, waiter: Waiter<T>) {
        waiters.removeAll { it.target.get() == null }
        val targetIndex = waiters.indexOfFirst { it.target.get() === waiter.target.get() }
        if (targetIndex >= 0) waiters[targetIndex] = waiter else waiters += waiter
    }

    private fun invalidationCallback(waiter: Waiter<T>): (() -> Unit)? {
        val target = waiter.target.get() ?: return null
        return {
            if (waiter.isCurrent(target, waiter.bindingToken)) waiter.invalidate(target)
        }
    }

    private fun saturatingAdd(value: Long, delta: Long): Long =
        if (delta > 0L && value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta

    private companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 2_000L
        const val DEFAULT_MAX_RETRY_DELAY_MILLIS = 60_000L
        const val DEFAULT_MAX_FAILURE_ENTRIES = 128
        const val DEFAULT_MAX_PENDING_RETRIES = 128
        const val MAX_BACKOFF_STEPS = 30
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
