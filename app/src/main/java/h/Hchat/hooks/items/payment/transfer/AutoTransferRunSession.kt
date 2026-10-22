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

    /** Admit an action under the lifecycle lock, then execute it without holding that lock. */
    fun runIfCurrent(token: Long, action: () -> Unit): Boolean {
        val admitted = synchronized(operationLock) {
            activeGeneration.get() == token && token != 0L
        }
        if (!admitted) return false
        action()
        return true
    }
}
