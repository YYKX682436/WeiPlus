package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.media.WeChatFavoriteApi

internal object BatchFavoriteDeleteExecutionPolicy {
    data class Decision(val stopLater: Boolean, val reason: String)

    fun afterNativeBatch(
        outcomes: List<WeChatFavoriteApi.FavoriteDeleteStatus>,
        accountCurrent: Boolean,
        stopRequested: Boolean
    ): Decision = when {
        !accountCurrent -> Decision(true, "账号已切换，已停止后续收藏删除")
        outcomes.any { it == WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED } ->
            Decision(true, "上一批删除结果未知，已停止后续收藏删除")
        stopRequested -> Decision(true, "已停止后续操作")
        else -> Decision(false, "")
    }
}
