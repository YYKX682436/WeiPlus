package h.Hchat.hooks.items.payment.transfer

import java.util.concurrent.atomic.AtomicLong

/** Rejects delayed work and query callbacks owned by a previous enable/destroy cycle. */
internal class AutoTransferRunSession {
    private val generation = AtomicLong(0L)
    private val activeGeneration = AtomicLong(0L)
    private val operationLock = Any()

    fun activate(): Long = synchronized(operationLock) {
        activeGeneration.get().takeIf { it != 0L } ?: generation.incrementAndGet().also(activeGeneration::set)
    }

    fun current(): Long? = activeGeneration.get().takeIf { it != 0L }

    fun isCurrent(token: Long): Boolean = token != 0L && activeGeneration.get() == token

    fun invalidate() = synchronized(operationLock) {
        activeGeneration.set(0L)
        generation.incrementAndGet()
    }

    /** Serializes only the final action boundary with disable/destroy. */
    fun runIfCurrent(token: Long, action: () -> Unit): Boolean = synchronized(operationLock) {
        if (activeGeneration.get() != token || token == 0L) return@synchronized false
        action()
        true
    }
}
