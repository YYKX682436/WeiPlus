package h.Hchat.hooks.api.media

internal object FavoriteSnapshotPolicy {
    fun isComplete(hasMore: Boolean, error: String?): Boolean = !hasMore && error.isNullOrBlank()

    fun marker(item: WeChatFavoriteItem): String = listOf(
        item.localId,
        item.serverId,
        item.type,
        item.updateTimeMillis,
        item.displayTitle(),
        item.summary
    ).joinToString("\u001f")

    fun hasConflictingDuplicate(existing: WeChatFavoriteItem, incoming: WeChatFavoriteItem): Boolean =
        marker(existing) != marker(incoming)

    fun changed(previous: Collection<WeChatFavoriteItem>, current: Collection<WeChatFavoriteItem>): Boolean =
        previous.mapTo(linkedSetOf(), ::marker) != current.mapTo(linkedSetOf(), ::marker)

    fun isSelectableForBatchDelete(item: WeChatFavoriteItem): Boolean =
        item.localId > 0L && item.serverId > 0 && item.type >= 0 && item.supportsNativeDelete

    fun hasUniqueDeleteIds(targets: List<FavoriteDeleteTarget>): Boolean =
        targets.map { it.localId }.distinct().size == targets.size &&
            targets.map { it.serverId }.distinct().size == targets.size

    fun matchesDeleteTarget(
        target: FavoriteDeleteTarget,
        actualLocalId: Long?,
        actualServerId: Int,
        actualType: Int,
        actualUpdateTimeMillis: Long,
        specialNativeEntry: Boolean
    ): Boolean = !specialNativeEntry &&
        actualLocalId == target.localId &&
        actualServerId == target.serverId &&
        actualType == target.type &&
        actualUpdateTimeMillis == target.updateTimeMillis
}

internal object FavoriteCacheInvalidationPolicy {
    fun clear(
        pickerItems: MutableMap<Long, WeChatFavoriteItem>,
        nativeItems: MutableMap<Long, Any>
    ) {
        pickerItems.clear()
        synchronized(nativeItems) { nativeItems.clear() }
    }
}

internal object FavoriteDeleteOutcomePolicy {
    data class Decision(
        val status: WeChatFavoriteApi.FavoriteDeleteStatus,
        val message: String,
        val stopLater: Boolean
    )

    fun classify(
        submitted: Boolean,
        invocationUnknown: Boolean,
        errorType: Int?,
        errorCode: Int?,
        itemResponseCode: Int?,
        duplicateResponse: Boolean,
        accountCurrent: Boolean,
        localStateReadable: Boolean,
        localItemStillPresent: Boolean
    ): Decision = when {
        !submitted -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.NOT_SUBMITTED,
            "微信原生收藏删除请求未提交",
            false
        )
        invocationUnknown -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "原生请求可能已提交，但调用返回异常；未重试",
            true
        )
        errorType == null || errorCode == null -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "删除请求已提交，微信未返回可确认结果；未重试",
            true
        )
        errorType != 0 -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "删除请求已提交，网络结果未知；未重试",
            true
        )
        errorCode != 0 -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.REJECTED,
            "微信拒绝了收藏删除请求（$errorCode）",
            false
        )
        !accountCurrent -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "微信已响应，但账号已切换，最终状态未确认",
            true
        )
        duplicateResponse -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "微信响应包含重复收藏 ID，结果未确认",
            true
        )
        itemResponseCode == null -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "微信响应未包含此收藏的删除结果；未重试",
            true
        )
        itemResponseCode < 0 -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.REJECTED,
            "微信未删除此收藏",
            false
        )
        !localStateReadable -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "微信已接受删除，但本地收藏状态无法复核",
            true
        )
        localItemStillPresent -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED,
            "微信已接受删除，但本地收藏仍可读取",
            true
        )
        else -> Decision(
            WeChatFavoriteApi.FavoriteDeleteStatus.CONFIRMED,
            "微信已删除收藏并移除本地条目",
            false
        )
    }
}
