package h.Hchat.hooks.items.payment.transfer

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** Queues payment work off message/UI callbacks and rechecks its generation when dequeued. */
internal class AutoTransferActionDispatcher(
    private val runSession: AutoTransferRunSession,
    private val executor: Executor
) {
    fun dispatch(token: Long, action: () -> Unit): Boolean {
        if (!runSession.isCurrent(token)) return false
        return try {
            executor.execute { runSession.runIfCurrent(token, action) }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }
}
