package h.Hchat.hooks.items.batch

/** Shared destructive-operation outcome gates, kept pure so ordering and uncertainty can be tested. */
internal object BatchDeleteOutcomePolicy {
    enum class ChatClearState { COMPLETED, NOT_SUBMITTED, UNCONFIRMED, STOPPED }

    /** The conversation remover is unreachable unless the native message stage is confirmed complete. */
    fun deleteChat(
        clearMessages: () -> ChatClearState,
        mayDeleteConversation: () -> Boolean?,
        deleteConversation: () -> Boolean?
    ): BatchTaskRunner.TaskResult {
        when (clearMessages()) {
            ChatClearState.NOT_SUBMITTED -> return result(BatchTaskRunner.Outcome.FAILED,
                "原生聊天记录清理未提交；会话保留")
            ChatClearState.UNCONFIRMED -> return result(BatchTaskRunner.Outcome.UNCONFIRMED,
                "聊天记录清理未确认；会话保留")
            ChatClearState.STOPPED -> return result(BatchTaskRunner.Outcome.FAILED,
                "清理已提交但被停止或账号已切换；会话保留")
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
            true -> result(BatchTaskRunner.Outcome.SUCCESS, "聊天与聊天记录已删除；关系保留")
            false -> result(BatchTaskRunner.Outcome.FAILED, "聊天记录已清理，但会话删除失败")
            null -> result(BatchTaskRunner.Outcome.UNCONFIRMED, "聊天记录已清理；会话删除状态未确认")
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
