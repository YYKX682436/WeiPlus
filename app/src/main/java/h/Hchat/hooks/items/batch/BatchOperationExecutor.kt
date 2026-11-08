package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.contact.WeChatContactApi
import h.Hchat.hooks.api.contact.WeChatNativeMutationResult
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageStoreApi
import java.util.concurrent.TimeUnit

/**
 * Stateless process-side executor. It uses only WeiPlus APIs and immutable task data.
 * On the verified WeChat 8.0.80 storage path, conversation changes post observers through a
 * Looper-backed queue (registered Looper or main Looper); the native write itself stays here on
 * the serial worker instead of synchronously blocking the UI thread.
 */
internal object BatchOperationExecutor : BatchTaskProcessor {
    private const val FRIEND_DELETE_OBSERVATION_TIMEOUT_MS = 15_000L
    override fun execute(
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ): BatchTaskRunner.Report {
        if (request.operation == BatchOperation.FRIEND_LABEL) {
            return executeLabelBatch(request, stopSignal)
        }
        return BatchTaskRunner.run(
            request.targetIds,
            request.expectedAccount,
            { accountIdentity() },
            stopSignal,
            0L,
            { id, signal -> executeOne(request, id, signal) },
            null
        )
    }

    private fun executeLabelBatch(
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ): BatchTaskRunner.Report {
        val contacts = WeChatApis.contacts()
        if (contacts == null || !contacts.isAvailable) {
            return failedReport(request.targetIds, "微信联系人 API 不可用")
        }
        val results = contacts.setContactLabelMembershipsById(
            request.targetIds,
            request.labelId,
            request.enabled,
            WeChatContactApi.BatchSubmissionGuard {
                !stopSignal.isStopped() && isAccountCurrent(request.expectedAccount)
            }
        )
        val taskResults = results.map { item ->
            val taskResult = when (item.outcome) {
                WeChatContactApi.ContactLabelMutationResult.ALREADY_SATISFIED ->
                    result(BatchTaskRunner.Outcome.SKIPPED, item.message)
                WeChatContactApi.ContactLabelMutationResult.INVALID_TARGET ->
                    result(BatchTaskRunner.Outcome.SKIPPED, item.message)
                WeChatContactApi.ContactLabelMutationResult.CONFIRMED ->
                    result(BatchTaskRunner.Outcome.SUCCESS, item.message)
                WeChatContactApi.ContactLabelMutationResult.SERVER_ACCEPTED ->
                    result(BatchTaskRunner.Outcome.UNCONFIRMED, item.message)
                WeChatContactApi.ContactLabelMutationResult.SUBMITTED_UNCONFIRMED,
                WeChatContactApi.ContactLabelMutationResult.SUBMITTED ->
                    result(BatchTaskRunner.Outcome.UNCONFIRMED, item.message)
                WeChatContactApi.ContactLabelMutationResult.SERVER_REJECTED ->
                    result(BatchTaskRunner.Outcome.FAILED, item.message)
                WeChatContactApi.ContactLabelMutationResult.NOT_SUBMITTED -> {
                    if (stopSignal.isStopped() || !isAccountCurrent(request.expectedAccount)) {
                        result(BatchTaskRunner.Outcome.SKIPPED, item.message)
                    } else {
                        result(BatchTaskRunner.Outcome.FAILED, item.message)
                    }
                }
            }
            taskResult
        }
        val processedIds = results.map { it.username }
        val remainingIds = request.targetIds.filterNot(processedIds.toSet()::contains)
        val completeResults = taskResults + remainingIds.map { id ->
            result(BatchTaskRunner.Outcome.SKIPPED,
                if (stopSignal.isStopped()) "停止后续提交" else "账号已切换，未提交")
        }
        val stopReason = when {
            stopSignal.isStopped() -> "已停止后续操作"
            !isAccountCurrent(request.expectedAccount) -> "账号已切换或账号状态不可用"
            else -> ""
        }
        return BatchTaskRunner.fromResults(request.targetIds, completeResults, stopReason)
    }

    private fun executeOne(
        request: BatchTaskRequest,
        id: String,
        stopSignal: BatchTaskRunner.StopSignal
    ): BatchTaskRunner.TaskResult {
        val conversations = WeChatApis.conversations()
        val contacts = WeChatApis.contacts()
        val chatrooms = WeChatApis.chatrooms()
        return when (request.operation) {
            BatchOperation.HIDE -> {
                if (conversations == null) return result(BatchTaskRunner.Outcome.FAILED, "微信会话 API 不可用")
                if (!conversations.isBatchOperableConversation(id)) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, "会话不存在或不可操作")
                }
                if (conversations.getConversationHomeHiddenState(id) == true) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, "会话已经隐藏")
                }
                if (!mayStartNativeOperation(request, stopSignal)) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, stopReason(stopSignal))
                }
                if (!conversations.hideConversationFromHome(id)) {
                    return result(BatchTaskRunner.Outcome.FAILED, "微信原生隐藏入口未提交")
                }
                return when (conversations.getConversationHomeHiddenState(id)) {
                    true -> result(BatchTaskRunner.Outcome.SUCCESS, "会话已隐藏，聊天记录保留")
                    false -> result(BatchTaskRunner.Outcome.FAILED, "隐藏状态未生效")
                    null -> result(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
                }
            }
            BatchOperation.DO_NOT_DISTURB -> {
                if (conversations == null) return result(BatchTaskRunner.Outcome.FAILED, "微信会话 API 不可用")
                val isGroup = conversations.isGroupConversation(id)
                val validTarget = if (isGroup) contacts?.isValidGroupContact(id) == true
                    else contacts?.isFriend(id) == true
                if (!validTarget) return result(BatchTaskRunner.Outcome.SKIPPED, "好友或群聊已失效")
                if (BatchStatePolicy.alreadySatisfied(
                        conversations.getWechatDoNotDisturbState(id), request.enabled
                    )) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, "免打扰已符合目标状态")
                }
                if (!mayStartNativeOperation(request, stopSignal)) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, stopReason(stopSignal))
                }
                if (!conversations.setWechatDoNotDisturb(id, request.enabled)) {
                    return result(BatchTaskRunner.Outcome.FAILED, "微信原生免打扰入口未提交")
                }
                return if (BatchStatePolicy.verified(
                        conversations.getWechatDoNotDisturbState(id), request.enabled
                    )) {
                    result(BatchTaskRunner.Outcome.SUCCESS, "免打扰状态已生效")
                } else {
                    result(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
                }
            }
            BatchOperation.PIN -> {
                if (conversations == null) return result(BatchTaskRunner.Outcome.FAILED, "微信会话 API 不可用")
                if (!conversations.isBatchOperableConversation(id)) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, "会话记录不存在，未创建聊天")
                }
                if (BatchStatePolicy.alreadySatisfied(
                        conversations.getWechatPlacedTopState(id), request.enabled
                    )) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, "置顶状态已符合目标")
                }
                if (!mayStartNativeOperation(request, stopSignal)) {
                    return result(BatchTaskRunner.Outcome.SKIPPED, stopReason(stopSignal))
                }
                if (!conversations.setWechatPlacedTop(id, request.enabled)) {
                    return result(BatchTaskRunner.Outcome.FAILED, "微信原生置顶入口未提交")
                }
                return if (BatchStatePolicy.verified(
                        conversations.getWechatPlacedTopState(id), request.enabled
                    )) {
                    result(BatchTaskRunner.Outcome.SUCCESS, "置顶状态已生效")
                } else {
                    result(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
                }
            }
            BatchOperation.FRIEND_LABEL -> return result(BatchTaskRunner.Outcome.FAILED, "标签任务未进入批量入口")
            BatchOperation.STAR_FRIEND -> {
                val contactApi = contacts ?: return result(BatchTaskRunner.Outcome.FAILED, "微信联系人 API 不可用")
                return mapNativeResult(contactApi.setStarredFriend(
                    id,
                    request.enabled,
                    nativeSubmissionGuard(request, stopSignal)
                ), request, stopSignal)
            }
            BatchOperation.FRIEND_PERMISSION -> {
                val contactApi = contacts ?: return result(BatchTaskRunner.Outcome.FAILED, "微信联系人 API 不可用")
                return BatchFriendPermissionExecution.executeOne(
                    id = id,
                    expectedAccount = request.expectedAccount,
                    selection = request.friendPermissionSelection,
                    stopSignal = stopSignal,
                    currentAccount = { accountIdentity() },
                    validateTarget = {
                        val state = contactApi.getContactStateResult(id)
                        when {
                            !state.success -> result(
                                BatchTaskRunner.Outcome.FAILED,
                                state.error.ifBlank { "读取好友关系失败，未提交权限修改" }
                            )
                            !state.found || !state.validFriend -> result(
                                BatchTaskRunner.Outcome.SKIPPED,
                                "好友关系已失效，未提交权限修改"
                            )
                            else -> null
                        }
                    },
                    mutate = { setting, desiredState, guard ->
                        contactApi.setFriendPermission(id, setting.toApiPermission(), desiredState, guard)
                    }
                )
            }
            BatchOperation.GROUP_SAVE_TO_CONTACTS -> {
                val api = chatrooms ?: return result(BatchTaskRunner.Outcome.FAILED, "微信群 API 不可用")
                return mapNativeResult(api.setGroupSavedToContacts(
                    id,
                    request.enabled,
                    nativeSubmissionGuard(request, stopSignal)
                ), request, stopSignal)
            }
            BatchOperation.GROUP_NICKNAME -> {
                val api = chatrooms ?: return result(BatchTaskRunner.Outcome.FAILED, "微信群 API 不可用")
                return mapNativeResult(api.setMyGroupNickname(
                    id,
                    request.groupNickname,
                    accountSelfId(),
                    nativeSubmissionGuard(request, stopSignal),
                    WeChatContactApi.BatchSubmissionGuard { isAccountCurrent(request.expectedAccount) }
                ), request, stopSignal)
            }
            BatchOperation.DELETE_CHATS -> deleteChat(id, request, stopSignal)
            BatchOperation.DELETE_FRIENDS -> deleteFriend(id, request, stopSignal)
        }
    }

    private fun deleteChat(
        id: String,
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ): BatchTaskRunner.TaskResult {
        val conversations = WeChatApis.conversations()
            ?: return result(BatchTaskRunner.Outcome.FAILED, "微信会话 API 不可用")
        val home = conversations.getNativeHomeConversationUsernames()
            ?: return result(BatchTaskRunner.Outcome.FAILED, "无法读取微信首页当前聊天列表")
        if (!home.contains(id) || !conversations.isBatchOperableConversation(id)) {
            return result(BatchTaskRunner.Outcome.SKIPPED, "聊天已隐藏、分组或失效，未删除")
        }
        if (!mayStartNativeOperation(request, stopSignal)) {
            return result(BatchTaskRunner.Outcome.SKIPPED, stopReason(stopSignal))
        }
        val messages = WeChatApis.messageStore()
            ?: return result(BatchTaskRunner.Outcome.FAILED, "微信消息清理 API 不可用")
        if (!messages.isAvailable) return result(BatchTaskRunner.Outcome.FAILED, "微信消息清理 API 不可用")
        return BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = {
                when (messages.clearConversationMessagesAndWait(id) {
                    stopSignal.isStopped() || !isAccountCurrent(request.expectedAccount)
                }) {
                    WeChatMessageStoreApi.ConversationClearResult.COMPLETED ->
                        BatchDeleteOutcomePolicy.ChatClearState.COMPLETED
                    WeChatMessageStoreApi.ConversationClearResult.NOT_SUBMITTED ->
                        BatchDeleteOutcomePolicy.ChatClearState.NOT_SUBMITTED
                    WeChatMessageStoreApi.ConversationClearResult.UNCONFIRMED ->
                        BatchDeleteOutcomePolicy.ChatClearState.UNCONFIRMED
                    WeChatMessageStoreApi.ConversationClearResult.STOPPED ->
                        BatchDeleteOutcomePolicy.ChatClearState.STOPPED
                }
            },
            mayDeleteConversation = {
                if (!mayStartNativeOperation(request, stopSignal)) false
                else conversations.getNativeHomeConversationUsernames()?.let { it.contains(id) }
            },
            deleteConversation = {
                when (conversations.deleteConversationFromNativeHome(id)) {
                    h.Hchat.hooks.api.conversation.WeChatConversationApi.NativeHomeDeleteState.CONFIRMED_REMOVED ->
                        BatchDeleteOutcomePolicy.NativeHomeDeleteState.CONFIRMED_REMOVED
                    h.Hchat.hooks.api.conversation.WeChatConversationApi.NativeHomeDeleteState.ROW_REMAINS ->
                        BatchDeleteOutcomePolicy.NativeHomeDeleteState.ROW_REMAINS
                    h.Hchat.hooks.api.conversation.WeChatConversationApi.NativeHomeDeleteState.ROW_RECREATED ->
                        BatchDeleteOutcomePolicy.NativeHomeDeleteState.ROW_RECREATED
                    h.Hchat.hooks.api.conversation.WeChatConversationApi.NativeHomeDeleteState.HOME_STILL_CONTAINS ->
                        BatchDeleteOutcomePolicy.NativeHomeDeleteState.HOME_STILL_CONTAINS
                    h.Hchat.hooks.api.conversation.WeChatConversationApi.NativeHomeDeleteState.SCOPE_CHANGED ->
                        BatchDeleteOutcomePolicy.NativeHomeDeleteState.SCOPE_CHANGED
                    h.Hchat.hooks.api.conversation.WeChatConversationApi.NativeHomeDeleteState.UNCONFIRMED ->
                        BatchDeleteOutcomePolicy.NativeHomeDeleteState.UNCONFIRMED
                }
            }
        )
    }

    private fun deleteFriend(
        id: String,
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ): BatchTaskRunner.TaskResult {
        val contacts = WeChatApis.contacts()
            ?: return result(BatchTaskRunner.Outcome.FAILED, "微信联系人 API 不可用")
        val messages = WeChatApis.messageStore()
            ?: return result(BatchTaskRunner.Outcome.FAILED, "微信消息清理 API 不可用")
        if (!messages.isAvailable) return result(BatchTaskRunner.Outcome.FAILED, "微信消息清理 API 不可用")
        val state = contacts.getContactStateResult(id)
        if (!state.success) return result(BatchTaskRunner.Outcome.FAILED, state.error.ifBlank { "读取好友关系失败" })
        val self = accountSelfId()
        if (!state.found || !state.validFriend || id == self || id.startsWith("gh_") ||
            id.endsWith("@chatroom") || id.endsWith("@im.chatroom")) {
            return result(BatchTaskRunner.Outcome.SKIPPED, "不是有效好友或对象已失效")
        }
        if (!contacts.supportsDeleteFriendRecordClear()) {
            return result(BatchTaskRunner.Outcome.FAILED, "当前微信未提供带聊天记录清理选项的原生删除接口")
        }
        if (!mayStartNativeOperation(request, stopSignal)) {
            return result(BatchTaskRunner.Outcome.SKIPPED, stopReason(stopSignal))
        }
        val nativeDelete = contacts.submitDeleteFriendWithResult(id, true) {
            !stopSignal.isStopped() && isAccountCurrent(request.expectedAccount)
        } ?: return if (!mayStartNativeOperation(request, stopSignal)) {
            result(BatchTaskRunner.Outcome.SKIPPED, stopReason(stopSignal))
        } else {
            result(BatchTaskRunner.Outcome.FAILED, "微信原生删除好友请求未提交或结果回调不可用")
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FRIEND_DELETE_OBSERVATION_TIMEOUT_MS)
        var lastRelationState = "unread"
        var lastHistoryState = "unread"
        try {
            while (true) {
                if (stopSignal.isStopped() || !isAccountCurrent(request.expectedAccount)) {
                    return result(BatchTaskRunner.Outcome.UNCONFIRMED, "删除请求可能已提交；因停止或账号切换未确认")
                }
                val nativeResult = nativeDelete.nativeResult()
                BatchDeleteOutcomePolicy.friendNativeResult(nativeResult)
                    ?.takeIf { nativeResult != null }?.let { return it }

                val current = contacts.getContactStateResult(id)
                if (current.success && (!current.found || !current.validFriend)) {
                    lastRelationState = "removed"
                    val historyState = messages.hasConversationMessagesState(id)
                    lastHistoryState = historyState?.toString() ?: "unreadable"
                    val outcome = BatchDeleteOutcomePolicy.friendState(false, historyState)
                    if (outcome?.outcome == BatchTaskRunner.Outcome.SUCCESS) return outcome
                } else if (current.success) {
                    lastRelationState = "still-friend"
                } else {
                    lastRelationState = "unreadable"
                }
                if (System.nanoTime() >= deadline) {
                    contacts.logDeleteFriendObservation(
                        id,
                        "relation=$lastRelationState history=$lastHistoryState " +
                            "responseHook=${nativeDelete.hasResponseHook()} " +
                            "receiverMismatch=${nativeDelete.receiverMismatch()} " +
                            "nativeResult=${nativeResult ?: "missing"}"
                    )
                    val stateDetail = when {
                        nativeResult == null && nativeDelete.receiverMismatch() ->
                            "原生回执由其他Service实例处理；好友或聊天记录状态未确认"
                        nativeResult == null && !nativeDelete.hasResponseHook() ->
                            "原生结果Hook不可用，好友或聊天记录状态未确认"
                        nativeResult == null -> "请求已提交但未收到原生回执，好友或聊天记录状态未确认"
                        else -> "微信返回成功码，但好友或聊天记录状态未确认"
                    }
                    return result(BatchTaskRunner.Outcome.UNCONFIRMED, stateDetail)
                }
                try {
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return result(BatchTaskRunner.Outcome.UNCONFIRMED, "删除请求可能已提交；任务中断，结果未确认")
                }
            }
        } finally {
            nativeDelete.close()
        }
    }

    private fun accountSelfId(): String =
        runCatching { WeChatApis.account()?.selfWxId().orEmpty().trim() }.getOrDefault("")

    private fun nativeSubmissionGuard(
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ) = WeChatContactApi.BatchSubmissionGuard {
        !stopSignal.isStopped() && isAccountCurrent(request.expectedAccount)
    }

    private fun mapNativeResult(
        native: WeChatNativeMutationResult,
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ): BatchTaskRunner.TaskResult = when (native.outcome) {
        WeChatNativeMutationResult.Outcome.CONFIRMED -> result(BatchTaskRunner.Outcome.SUCCESS, native.message)
        WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED,
        WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED ->
            result(BatchTaskRunner.Outcome.UNCONFIRMED, native.message.ifBlank { "已提交／未确认" })
        WeChatNativeMutationResult.Outcome.SERVER_REJECTED -> result(BatchTaskRunner.Outcome.FAILED, native.message)
        WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED -> result(BatchTaskRunner.Outcome.SKIPPED, native.message)
        WeChatNativeMutationResult.Outcome.INVALID_TARGET -> result(BatchTaskRunner.Outcome.SKIPPED, native.message)
        WeChatNativeMutationResult.Outcome.READ_FAILED -> result(BatchTaskRunner.Outcome.FAILED, native.message)
        WeChatNativeMutationResult.Outcome.NOT_SUBMITTED -> {
            if (stopSignal.isStopped() || !isAccountCurrent(request.expectedAccount)) {
                result(BatchTaskRunner.Outcome.SKIPPED, native.message.ifBlank { stopReason(stopSignal) })
            } else {
                result(BatchTaskRunner.Outcome.FAILED, native.message)
            }
        }
    }

    private fun BatchFriendPermissionSetting.toApiPermission() = when (this) {
        BatchFriendPermissionSetting.ONLY_CHAT -> WeChatContactApi.FriendPermissionSetting.ONLY_CHAT
        BatchFriendPermissionSetting.DONT_SEE_MOMENTS -> WeChatContactApi.FriendPermissionSetting.DONT_SEE_MOMENTS
        BatchFriendPermissionSetting.DONT_LET_SEE_MOMENTS -> WeChatContactApi.FriendPermissionSetting.DONT_LET_SEE_MOMENTS
    }

    private fun failedReport(ids: List<String>, message: String): BatchTaskRunner.Report =
        BatchTaskRunner.fromResults(
            ids,
            ids.map { result(BatchTaskRunner.Outcome.FAILED, message) },
            ""
        )

    private fun result(outcome: BatchTaskRunner.Outcome, message: String) =
        BatchTaskRunner.TaskResult.of(outcome, message)

    private fun accountIdentity(): String =
        runCatching { WeChatApis.account()?.sessionIdentity().orEmpty().trim() }.getOrDefault("")

    private fun isAccountCurrent(expectedAccount: String): Boolean =
        expectedAccount.isNotBlank() && accountIdentity() == expectedAccount

    private fun mayStartNativeOperation(
        request: BatchTaskRequest,
        stopSignal: BatchTaskRunner.StopSignal
    ): Boolean = !stopSignal.isStopped() && isAccountCurrent(request.expectedAccount)

    private fun stopReason(stopSignal: BatchTaskRunner.StopSignal): String =
        if (stopSignal.isStopped()) "任务已停止，未提交" else "账号已切换，未提交"
}

/** Serial per-friend permission plan; no-op dimensions never become off/clear operations. */
internal object BatchFriendPermissionExecution {
    fun executeOne(
        id: String,
        expectedAccount: String,
        selection: BatchFriendPermissionSelection,
        stopSignal: BatchTaskRunner.StopSignal,
        currentAccount: () -> String,
        validateTarget: () -> BatchTaskRunner.TaskResult?,
        mutate: (
            BatchFriendPermissionSetting,
            Boolean,
            WeChatContactApi.BatchSubmissionGuard
        ) -> WeChatNativeMutationResult
    ): BatchTaskRunner.TaskResult {
        val actions = selection.actions()
        if (actions.isEmpty()) return taskResult(BatchTaskRunner.Outcome.FAILED, "未选择要修改的朋友权限")

        fun accountIsCurrent(): Boolean = runCatching {
            expectedAccount.isNotBlank() && currentAccount().trim() == expectedAccount.trim()
        }.getOrDefault(false)

        fun mayContinue(): Boolean = !stopSignal.isStopped() && accountIsCurrent()

        if (!mayContinue()) return taskResult(
            BatchTaskRunner.Outcome.SKIPPED,
            if (stopSignal.isStopped()) "任务已停止，未提交权限修改" else "账号已切换，未提交权限修改"
        )
        validateTarget()?.let { return it }

        val completed = mutableListOf<Pair<BatchFriendPermissionAction, WeChatNativeMutationResult>>()
        var interrupted = false
        for (action in actions) {
            if (!mayContinue()) {
                interrupted = true
                break
            }
            val native = try {
                mutate(
                    action.setting,
                    action.enabled,
                    WeChatContactApi.BatchSubmissionGuard { mayContinue() }
                )
            } catch (failure: Throwable) {
                WeChatNativeMutationResult.of(
                    WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    failure.message ?: failure.javaClass.simpleName
                )
            }
            completed += action to native
            if (!mayContinue() && (completed.size < actions.size ||
                    native.outcome == WeChatNativeMutationResult.Outcome.NOT_SUBMITTED)) {
                interrupted = true
                break
            }
        }

        val remaining = actions.drop(completed.size)
        val outcomes = completed.map { it.second.outcome }
        val allInvalid = outcomes.isNotEmpty() && outcomes.all {
            it == WeChatNativeMutationResult.Outcome.INVALID_TARGET
        }
        val failed = outcomes.any {
            it == WeChatNativeMutationResult.Outcome.SERVER_REJECTED ||
                it == WeChatNativeMutationResult.Outcome.READ_FAILED ||
                (it == WeChatNativeMutationResult.Outcome.NOT_SUBMITTED && !interrupted)
        } || (WeChatNativeMutationResult.Outcome.INVALID_TARGET in outcomes && !allInvalid)
        val unconfirmed = outcomes.any {
            it == WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED ||
                it == WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED
        }
        val nativeRequestMayHaveBeenSent = outcomes.any {
            it == WeChatNativeMutationResult.Outcome.CONFIRMED ||
                it == WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED ||
                it == WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED ||
                it == WeChatNativeMutationResult.Outcome.SERVER_REJECTED
        }
        val confirmed = outcomes.count { it == WeChatNativeMutationResult.Outcome.CONFIRMED }
        val skipped = outcomes.count {
            it == WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED ||
                it == WeChatNativeMutationResult.Outcome.INVALID_TARGET ||
                (it == WeChatNativeMutationResult.Outcome.NOT_SUBMITTED && interrupted)
        }

        val details = completed.map { (action, native) ->
            "${action.label}：${permissionOutcomeText(native)}"
        }.toMutableList()
        if (remaining.isNotEmpty()) {
            details += "${if (stopSignal.isStopped()) "停止后" else "账号切换后"}未提交：" +
                remaining.joinToString("、") { it.label }
        }
        val message = "朋友权限逐项结果：${details.joinToString("；")}".ifBlank { "权限修改未执行" }

        val outcome = when {
            failed -> BatchTaskRunner.Outcome.FAILED
            unconfirmed || (interrupted && nativeRequestMayHaveBeenSent) -> BatchTaskRunner.Outcome.UNCONFIRMED
            confirmed > 0 -> BatchTaskRunner.Outcome.SUCCESS
            skipped == actions.size -> BatchTaskRunner.Outcome.SKIPPED
            interrupted -> BatchTaskRunner.Outcome.SKIPPED
            else -> BatchTaskRunner.Outcome.FAILED
        }
        return taskResult(outcome, message)
    }

    private fun permissionOutcomeText(result: WeChatNativeMutationResult): String = when (result.outcome) {
        WeChatNativeMutationResult.Outcome.CONFIRMED -> "已确认"
        WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED -> "服务端已接受，状态未确认"
        WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED -> "已提交／未确认"
        WeChatNativeMutationResult.Outcome.SERVER_REJECTED -> "服务端拒绝"
        WeChatNativeMutationResult.Outcome.NOT_SUBMITTED -> result.message.ifBlank { "未提交" }
        WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED -> "已符合目标"
        WeChatNativeMutationResult.Outcome.INVALID_TARGET -> result.message.ifBlank { "对象已失效" }
        WeChatNativeMutationResult.Outcome.READ_FAILED -> result.message.ifBlank { "状态读取失败" }
    }

    private fun taskResult(outcome: BatchTaskRunner.Outcome, message: String) =
        BatchTaskRunner.TaskResult.of(outcome, message)
}
