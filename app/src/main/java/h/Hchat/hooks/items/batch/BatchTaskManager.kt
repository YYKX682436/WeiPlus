package h.Hchat.hooks.items.batch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

internal enum class BatchOperation(val title: String) {
    HIDE("批量不显示聊天"),
    DO_NOT_DISTURB("批量设置免打扰"),
    PIN("批量设置置顶"),
    FRIEND_LABEL("批量管理好友标签")
}

internal data class BatchTaskRequest(
    val operation: BatchOperation,
    val targetIds: List<String>,
    val expectedAccount: String,
    val enabled: Boolean,
    val labelId: String = ""
)

internal enum class BatchTaskStatus {
    IDLE,
    RUNNING,
    STOPPING,
    COMPLETED,
    STOPPED,
    FAILED
}

internal data class BatchTaskSnapshot(
    val status: BatchTaskStatus = BatchTaskStatus.IDLE,
    val operation: BatchOperation? = null,
    val total: Int = 0,
    val report: BatchTaskRunner.Report? = null,
    val failure: String = ""
)

internal enum class BatchTaskSubmitResult { STARTED, BUSY, UNAVAILABLE, INVALID }

internal fun interface BatchTaskProcessor {
    fun execute(request: BatchTaskRequest, stopSignal: BatchTaskRunner.StopSignal): BatchTaskRunner.Report
}

/** Process-owned serial work queue. It never captures an Activity, Compose state, or UI callback. */
internal class BatchTaskManager private constructor(
    private val processor: BatchTaskProcessor,
    private val executor: ExecutorService
) {
    private data class Session(
        val id: Long,
        val request: BatchTaskRequest,
        val stopSignal: BatchTaskRunner.StopSignal
    )

    private val lock = Any()
    private val mutableSnapshot = MutableStateFlow(BatchTaskSnapshot())
    val snapshot: StateFlow<BatchTaskSnapshot> = mutableSnapshot.asStateFlow()

    private var nextId = 0L
    private var current: Session? = null
    private var shutDown = false

    fun submit(request: BatchTaskRequest): BatchTaskSubmitResult {
        val normalized = normalize(request) ?: return BatchTaskSubmitResult.INVALID
        synchronized(lock) {
            if (shutDown) return BatchTaskSubmitResult.UNAVAILABLE
            if (current != null) return BatchTaskSubmitResult.BUSY
            val session = Session(++nextId, normalized, BatchTaskRunner.StopSignal())
            current = session
            mutableSnapshot.value = BatchTaskSnapshot(
                status = BatchTaskStatus.RUNNING,
                operation = normalized.operation,
                total = normalized.targetIds.size
            )
            try {
                executor.execute { run(session) }
            } catch (_: RejectedExecutionException) {
                current = null
                mutableSnapshot.value = BatchTaskSnapshot(
                    status = BatchTaskStatus.FAILED,
                    operation = normalized.operation,
                    total = normalized.targetIds.size,
                    failure = "后台任务队列不可用"
                )
                return BatchTaskSubmitResult.UNAVAILABLE
            }
            return BatchTaskSubmitResult.STARTED
        }
    }

    fun stopLaterTasks(): Boolean = synchronized(lock) {
        val session = current ?: return@synchronized false
        if (session.stopSignal.isStopped()) return@synchronized false
        session.stopSignal.stop()
        mutableSnapshot.value = mutableSnapshot.value.copy(status = BatchTaskStatus.STOPPING)
        true
    }

    /** Called when the host runtime identity is invalidated. The in-flight call may finish. */
    fun shutdownForHostInvalidation() {
        synchronized(lock) {
            if (shutDown) return
            shutDown = true
            current?.stopSignal?.stop()
            current?.let { mutableSnapshot.value = mutableSnapshot.value.copy(status = BatchTaskStatus.STOPPING) }
            executor.shutdown()
        }
    }

    internal fun shutdownForTesting() = shutdownForHostInvalidation()

    private fun run(session: Session) {
        val (report, failure) = try {
            processor.execute(session.request, session.stopSignal) to ""
        } catch (failure: Throwable) {
            null to (failure.message ?: failure.javaClass.simpleName)
        }
        synchronized(lock) {
            if (current?.id != session.id) return
            current = null
            mutableSnapshot.value = BatchTaskSnapshot(
                status = when {
                    report == null -> BatchTaskStatus.FAILED
                    report.stopReason.isNotBlank() -> BatchTaskStatus.STOPPED
                    else -> BatchTaskStatus.COMPLETED
                },
                operation = session.request.operation,
                total = session.request.targetIds.size,
                report = report,
                failure = failure
            )
        }
    }

    private fun normalize(request: BatchTaskRequest): BatchTaskRequest? {
        if (request.expectedAccount.isBlank()) return null
        if (request.operation == BatchOperation.FRIEND_LABEL && request.labelId.isBlank()) return null
        val ids = request.targetIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (ids.isEmpty()) return null
        return request.copy(targetIds = ids, expectedAccount = request.expectedAccount.trim(), labelId = request.labelId.trim())
    }

    companion object {
        private val threadIds = AtomicLong()
        private val processManager = BatchTaskManager(
            processor = BatchOperationExecutor,
            executor = newExecutor("HchatBatchTask")
        )

        @JvmStatic
        fun processInstance(): BatchTaskManager = processManager

        @JvmStatic
        fun shutdownProcessManagerForHostInvalidation() = processManager.shutdownForHostInvalidation()

        internal fun createForTesting(processor: BatchTaskProcessor): BatchTaskManager =
            BatchTaskManager(processor, newExecutor("HchatBatchTest"))

        private fun newExecutor(prefix: String): ExecutorService = Executors.newSingleThreadExecutor(
            ThreadFactory { runnable ->
                Thread(runnable, "$prefix-${threadIds.incrementAndGet()}").apply { isDaemon = true }
            }
        )
    }
}
