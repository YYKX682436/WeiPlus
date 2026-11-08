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
    FRIEND_LABEL("批量管理好友标签"),
    STAR_FRIEND("批量设置星标朋友"),
    FRIEND_PERMISSION("批量设置好友权限"),
    GROUP_SAVE_TO_CONTACTS("批量保存群聊到通讯录"),
    GROUP_NICKNAME("批量修改我在群里的昵称"),
    DELETE_CHATS("批量删除聊天"),
    DELETE_FRIENDS("批量删除好友")
}

internal enum class BatchFriendAccessChoice(val label: String) {
    UNCHANGED("不修改"),
    NORMAL("聊天、朋友圈、微信运动等"),
    ONLY_CHAT("仅聊天")
}

internal enum class BatchPermissionToggle(val label: String) {
    UNCHANGED("不修改"),
    ENABLE("开启"),
    DISABLE("关闭")
}

internal enum class BatchFriendPermissionSetting {
    ONLY_CHAT,
    DONT_LET_SEE_MOMENTS,
    DONT_SEE_MOMENTS
}

internal data class BatchFriendPermissionAction(
    val setting: BatchFriendPermissionSetting,
    val enabled: Boolean,
    val label: String
)

/** Request-only target choices; unchanged dimensions create no native operation. */
internal data class BatchFriendPermissionSelection(
    val access: BatchFriendAccessChoice = BatchFriendAccessChoice.UNCHANGED,
    val dontLetSee: BatchPermissionToggle = BatchPermissionToggle.UNCHANGED,
    val dontSee: BatchPermissionToggle = BatchPermissionToggle.UNCHANGED
) {
    fun actions(): List<BatchFriendPermissionAction> = buildList {
        when (access) {
            BatchFriendAccessChoice.UNCHANGED -> Unit
            BatchFriendAccessChoice.NORMAL -> add(BatchFriendPermissionAction(
                BatchFriendPermissionSetting.ONLY_CHAT, false, access.label
            ))
            BatchFriendAccessChoice.ONLY_CHAT -> add(BatchFriendPermissionAction(
                BatchFriendPermissionSetting.ONLY_CHAT, true, access.label
            ))
        }
        if (dontLetSee != BatchPermissionToggle.UNCHANGED) add(BatchFriendPermissionAction(
            BatchFriendPermissionSetting.DONT_LET_SEE_MOMENTS,
            dontLetSee == BatchPermissionToggle.ENABLE,
            "不让他（她）看：${dontLetSee.label}"
        ))
        if (dontSee != BatchPermissionToggle.UNCHANGED) add(BatchFriendPermissionAction(
            BatchFriendPermissionSetting.DONT_SEE_MOMENTS,
            dontSee == BatchPermissionToggle.ENABLE,
            "不看他（她）：${dontSee.label}"
        ))
    }
}

internal data class BatchTaskRequest(
    val operation: BatchOperation,
    val targetIds: List<String>,
    val expectedAccount: String,
    val enabled: Boolean,
    val labelId: String = "",
    val friendPermissionSelection: BatchFriendPermissionSelection = BatchFriendPermissionSelection(),
    val groupNickname: String = ""
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

internal fun interface BatchTaskCompletionNotifier {
    fun notify(snapshot: BatchTaskSnapshot)
}

/** Process-owned serial work queue. It never captures an Activity, Compose state, or UI callback. */
internal class BatchTaskManager private constructor(
    private val processor: BatchTaskProcessor,
    private val executor: ExecutorService,
    private val completionNotifier: BatchTaskCompletionNotifier
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
        var finishedSnapshot: BatchTaskSnapshot? = null
        var shouldNotify = false
        synchronized(lock) {
            if (current?.id != session.id) return
            current = null
            val snapshot = BatchTaskSnapshot(
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
            mutableSnapshot.value = snapshot
            finishedSnapshot = snapshot
            shouldNotify = !shutDown
        }
        if (shouldNotify) {
            finishedSnapshot?.let { snapshot ->
                runCatching { completionNotifier.notify(snapshot) }
            }
        }
    }

    private fun normalize(request: BatchTaskRequest): BatchTaskRequest? {
        if (request.expectedAccount.isBlank()) return null
        if (request.operation == BatchOperation.FRIEND_LABEL && request.labelId.isBlank()) return null
        if (request.operation == BatchOperation.FRIEND_PERMISSION && request.friendPermissionSelection.actions().isEmpty()) {
            return null
        }
        if (request.operation == BatchOperation.GROUP_NICKNAME) {
            val nickname = request.groupNickname.trim()
            val codePoints = nickname.codePointCount(0, nickname.length)
            if (nickname.isBlank() || codePoints > 16) return null
        }
        val ids = request.targetIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (ids.isEmpty()) return null
        return request.copy(
            targetIds = ids,
            expectedAccount = request.expectedAccount.trim(),
            labelId = request.labelId.trim(),
            groupNickname = request.groupNickname.trim()
        )
    }

    companion object {
        private val threadIds = AtomicLong()
        private val processManager = BatchTaskManager(
            processor = BatchOperationExecutor,
            executor = newExecutor("HchatBatchTask"),
            completionNotifier = BatchTaskCompletionToastNotifier
        )

        @JvmStatic
        fun processInstance(): BatchTaskManager = processManager

        @JvmStatic
        fun shutdownProcessManagerForHostInvalidation() = processManager.shutdownForHostInvalidation()

        internal fun createForTesting(
            processor: BatchTaskProcessor,
            completionNotifier: BatchTaskCompletionNotifier = BatchTaskCompletionNotifier { _ -> }
        ): BatchTaskManager =
            BatchTaskManager(processor, newExecutor("HchatBatchTest"), completionNotifier)

        private fun newExecutor(prefix: String): ExecutorService = Executors.newSingleThreadExecutor(
            ThreadFactory { runnable ->
                Thread(runnable, "$prefix-${threadIds.incrementAndGet()}").apply { isDaemon = true }
            }
        )
    }
}
