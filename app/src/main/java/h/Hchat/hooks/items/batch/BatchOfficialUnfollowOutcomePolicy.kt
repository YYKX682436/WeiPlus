package h.Hchat.hooks.items.batch

internal object BatchOfficialUnfollowOutcomePolicy {
    data class Decision(
        val outcome: BatchTaskRunner.Outcome,
        val message: String,
        val stopLater: Boolean
    )

    fun classify(
        submitted: Boolean,
        responseObserved: Boolean,
        accountCurrent: Boolean,
        serverResult: Int?,
        nativeLocalResult: Boolean,
        nativeCleanupFailed: Boolean,
        finalStateReadSucceeded: Boolean,
        stillFollowed: Boolean
    ): Decision = when {
        !submitted -> Decision(
            BatchTaskRunner.Outcome.FAILED,
            "微信原生取消关注请求未提交",
            true
        )
        !accountCurrent -> Decision(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            "请求已提交，但账号已切换，结果未确认",
            true
        )
        !responseObserved || serverResult == null -> Decision(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            "请求已提交，微信服务端结果未知；未重试",
            true
        )
        serverResult != 0 -> Decision(
            BatchTaskRunner.Outcome.FAILED,
            "微信服务端拒绝取消关注（$serverResult）",
            true
        )
        nativeCleanupFailed || !nativeLocalResult -> Decision(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            "微信服务端已响应，但原生本地结果未确认",
            true
        )
        !finalStateReadSucceeded -> Decision(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            "微信已响应，但无法复核公众号关注状态",
            true
        )
        stillFollowed -> Decision(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            "微信已响应，但关注关系仍存在；未重试",
            true
        )
        else -> Decision(
            BatchTaskRunner.Outcome.SUCCESS,
            "公众号关注关系已解除",
            false
        )
    }
}
