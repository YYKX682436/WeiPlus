package h.Hchat.hooks.items.batch

/** Keeps network acceptance, actual membership and local reconciliation as separate facts. */
internal object BatchGroupQuitOutcomePolicy {
    enum class Stage {
        NOT_SUBMITTED,
        SERVER_REJECTED,
        REQUEST_RESULT_UNKNOWN,
        SERVER_ACCEPTED_STATE_UNKNOWN,
        SERVER_ACCEPTED_GROUP_DISBANDED,
        LEFT_CONFIRMED,
        LEFT_CONFIRMED_CLEANUP_UNKNOWN,
        ACCOUNT_CHANGED_UNKNOWN
    }

    data class Decision(
        val stage: Stage,
        val outcome: BatchTaskRunner.Outcome,
        val message: String
    )

    fun classify(
        requestSubmitted: Boolean,
        serverAccepted: Boolean,
        state: BatchGroupStatePolicy.State,
        cleanupFailed: Boolean,
        transportUnknown: Boolean,
        accountChanged: Boolean
    ): Decision {
        if (accountChanged) {
            return if (requestSubmitted || serverAccepted) {
                Decision(Stage.ACCOUNT_CHANGED_UNKNOWN, BatchTaskRunner.Outcome.UNCONFIRMED,
                    "退群请求可能已提交；账号切换后无法确认结果，未重试")
            } else {
                Decision(Stage.NOT_SUBMITTED, BatchTaskRunner.Outcome.SKIPPED,
                    "账号已切换，未提交退群请求")
            }
        }
        if (serverAccepted) {
            return when (state) {
                BatchGroupStatePolicy.State.NOT_MEMBER,
                BatchGroupStatePolicy.State.CONTACT_MISSING -> if (cleanupFailed) {
                    Decision(Stage.LEFT_CONFIRMED_CLEANUP_UNKNOWN,
                        BatchTaskRunner.Outcome.UNCONFIRMED,
                        "已确认退出；微信原生残留清理未确认")
                } else {
                    Decision(Stage.LEFT_CONFIRMED, BatchTaskRunner.Outcome.SUCCESS,
                        "微信服务器已接受且确认当前账号已离开群聊")
                }
                BatchGroupStatePolicy.State.DISBANDED -> Decision(
                    Stage.SERVER_ACCEPTED_GROUP_DISBANDED,
                    BatchTaskRunner.Outcome.UNCONFIRMED,
                    "退群请求已接受，但该群同时显示为已解散，无法归因本次操作"
                )
                else -> Decision(Stage.SERVER_ACCEPTED_STATE_UNKNOWN,
                    BatchTaskRunner.Outcome.UNCONFIRMED,
                    "微信服务器已接受退群请求；当前成员状态尚未确认，未重试")
            }
        }
        if (requestSubmitted && transportUnknown) {
            return Decision(Stage.REQUEST_RESULT_UNKNOWN, BatchTaskRunner.Outcome.UNCONFIRMED,
                "请求可能已提交，结果未知；未重试")
        }
        if (requestSubmitted) {
            return Decision(Stage.SERVER_REJECTED, BatchTaskRunner.Outcome.FAILED,
                "微信原生退群请求已返回失败，未确认服务器接受")
        }
        return Decision(Stage.NOT_SUBMITTED, BatchTaskRunner.Outcome.SKIPPED, "未提交退群请求")
    }
}
