package h.Hchat.hooks.items.batch

/** Shared destructive-operation outcome gates, kept pure so ordering and uncertainty can be tested. */
internal object BatchDeleteOutcomePolicy {
    enum class ChatClearState { COMPLETED, NOT_SUBMITTED, UNCONFIRMED, STOPPED }
    enum class NativeHomeDeleteState {
        CONFIRMED_REMOVED, ROW_REMAINS, ROW_RECREATED, HOME_STILL_CONTAINS,
        SCOPE_CHANGED, NOT_SUBMITTED, UNCONFIRMED
    }

    /** A native response code of zero confirms the server mutation, but not local cleanup yet. */
    fun friendNativeResult(resultCode: Int?): BatchTaskRunner.TaskResult? = when {
        resultCode == null -> result(BatchTaskRunner.Outcome.UNCONFIRMED,
            "删除请求已提交；微信未返回删除结果")
        resultCode != 0 -> result(BatchTaskRunner.Outcome.FAILED,
            "微信原生删除好友返回失败码 $resultCode")
        else -> null
    }

    /** The conversation remover is unreachable unless the native message stage is confirmed complete. */
    fun deleteChat(
        clearMessages: () -> ChatClearState,
        mayDeleteConversation: () -> Boolean?,
        deleteConversation: () -> NativeHomeDeleteState
    ): BatchTaskRunner.TaskResult {
        when (clearMessages()) {
            ChatClearState.NOT_SUBMITTED -> return result(BatchTaskRunner.Outcome.FAILED,
                "原生聊天记录清理未提交；会话保留")
            ChatClearState.UNCONFIRMED -> return result(BatchTaskRunner.Outcome.UNCONFIRMED,
                "聊天记录清理未确认；会话保留")
            ChatClearState.STOPPED -> return result(BatchTaskRunner.Outcome.UNCONFIRMED,
                "清理请求可能已提交；被停止或账号已切换，结果未确认；会话保留")
            ChatClearState.COMPLETED -> Unit
        }
        when (mayDeleteConversation()) {
            null -> return result(BatchTaskRunner.Outcome.UNCONFIRMED,
                "聊天记录已清理；会话范围状态未确认")
            false -> return result(BatchTaskRunner.Outcome.FAILED,
                "聊天记录已清理；会话删除前任务已停止、账号已切换或会话范围已变化")
            true -> Unit
        }
        return when (deleteConversation()) {
            NativeHomeDeleteState.CONFIRMED_REMOVED ->
                result(BatchTaskRunner.Outcome.SUCCESS, "聊天与聊天记录已删除；关系保留")
            NativeHomeDeleteState.ROW_REMAINS ->
                result(BatchTaskRunner.Outcome.FAILED, "聊天记录已清理，但原生删除后会话行仍存在")
            NativeHomeDeleteState.ROW_RECREATED ->
                result(BatchTaskRunner.Outcome.FAILED, "聊天记录已清理，但原生删除后会话行被重新创建")
            NativeHomeDeleteState.HOME_STILL_CONTAINS ->
                result(BatchTaskRunner.Outcome.UNCONFIRMED, "聊天记录已清理；原生首页仍显示该会话")
            NativeHomeDeleteState.SCOPE_CHANGED ->
                result(BatchTaskRunner.Outcome.FAILED, "聊天记录已清理；会话范围变化，原生删除未执行")
            NativeHomeDeleteState.NOT_SUBMITTED ->
                result(BatchTaskRunner.Outcome.SKIPPED, "聊天记录已清理；任务已停止或账号已切换，会话删除未提交")
            NativeHomeDeleteState.UNCONFIRMED ->
                result(BatchTaskRunner.Outcome.UNCONFIRMED, "聊天记录已清理；原生会话移除状态未确认")
        }
    }

    /** null means the deletion is still pending and should be observed again. */
    fun friendState(validFriend: Boolean?, messagesRemain: Boolean?): BatchTaskRunner.TaskResult? {
        if (validFriend == null) return result(BatchTaskRunner.Outcome.UNCONFIRMED,
            "删除请求已提交；好友关系状态读取失败")
        if (validFriend) return null
        return when (messagesRemain) {
            false -> result(BatchTaskRunner.Outcome.SUCCESS, "好友关系与聊天记录已删除")
            true -> null
            null -> result(BatchTaskRunner.Outcome.UNCONFIRMED, "好友关系已删除；聊天记录状态未确认")
        }
    }

    private fun result(outcome: BatchTaskRunner.Outcome, message: String) =
        BatchTaskRunner.TaskResult.of(outcome, message)
}
