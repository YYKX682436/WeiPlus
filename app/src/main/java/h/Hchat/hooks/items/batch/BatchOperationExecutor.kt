package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.contact.WeChatContactApi
import h.Hchat.hooks.api.core.WeChatApis

/** Stateless process-side executor. It uses only WeiPlus APIs and immutable task data. */
internal object BatchOperationExecutor : BatchTaskProcessor {
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
            ?: return result(BatchTaskRunner.Outcome.FAILED, "微信会话 API 不可用")
        val contacts = WeChatApis.contacts()
        when (request.operation) {
            BatchOperation.HIDE -> {
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
        }
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
