package h.Hchat.hooks.api.media

internal enum class FavoritePaginationDecision { ADVANCE, COMPLETE, INVALID }

internal object FavoritePaginationPolicy {
    fun decide(currentCursor: Long, nextCursor: Long): FavoritePaginationDecision = when {
        currentCursor < 0L || nextCursor <= 0L -> FavoritePaginationDecision.INVALID
        currentCursor == 0L -> FavoritePaginationDecision.ADVANCE
        nextCursor == currentCursor -> FavoritePaginationDecision.COMPLETE
        nextCursor < currentCursor -> FavoritePaginationDecision.ADVANCE
        else -> FavoritePaginationDecision.INVALID
    }
}

internal object FavoriteSnapshotPolicy {
    fun marker(item: WeChatFavoriteItem): String = listOf(
        item.localId,
        item.type,
        item.updateTimeMillis,
        item.displayTitle(),
        item.summary
    ).joinToString("\u001f")

    fun hasConflictingDuplicate(existing: WeChatFavoriteItem, incoming: WeChatFavoriteItem): Boolean =
        marker(existing) != marker(incoming)
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
