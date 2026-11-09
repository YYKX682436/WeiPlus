package h.Hchat.hooks.api.sns

internal data class SnsPageAdvance(val next: Long? = null, val complete: Boolean = false, val failure: String = "",
    val includePage: Boolean = false)

/** One cursor chain per open/reload. A new chain never inherits results or cursors. */
internal class SnsHistoryCursor(private val isSelf: Boolean = true) {
    var cursor: Long = 0
        private set
    private val used = hashSetOf(0L)
    private var recentLimitId = 0L

    fun advance(page: NativeSnsPage): SnsPageAdvance {
        val normal = page.errType == 0 && page.errCode == 0 && page.ret == 0
        if (page.ret == 203) return SnsPageAdvance(failure = "privacy_boundary")
        if (!isSelf) {
            val albumEnd = page.ret == 207 && page.errType == 4 && page.errCode == 207
            val nativeRangeCode = page.ret in restrictedAlbumCodes ||
                page.errType == 4 && page.errCode in restrictedAlbumCodes
            val nativeRangeTip = normal && page.hasRecentTips && (cursor == 0L || page.downAll)
            val rangeLimited = page.privacy || nativeRangeCode || nativeRangeTip ||
                albumEnd && (page.hasRecentTips || page.recentLimitId != 0L || recentLimitId != 0L)
            if (rangeLimited || albumEnd) {
                // r3 feeds 207/2001/2004/2005 through the same J() storage branch. Confirm
                // that this scene processed the returned list before retaining a boundary page.
                val trustedPayload = albumEnd || nativeRangeTip ||
                    page.ret in rangeCodesWithPayload && page.errType == 4 && page.errCode == page.ret
                val visibleBoundary = !page.privacy && trustedPayload && page.feeds.isNotEmpty() &&
                    page.count == page.feeds.size && page.lastId == page.feeds.last().id
                return SnsPageAdvance(
                    failure = if (rangeLimited) "privacy_boundary" else "visible_history_boundary",
                    includePage = visibleBoundary
                )
            }
            if (normal && java.lang.Long.compareUnsigned(page.recentLimitId, recentLimitId) > 0) {
                recentLimitId = page.recentLimitId
            }
            if (normal && page.count == 0 && page.feeds.isEmpty() && page.continuation == 0L) {
                if (recentLimitId != 0L) return SnsPageAdvance(failure = "privacy_boundary")
                if (page.downAll && page.lastId == cursor) return SnsPageAdvance(complete = true)
            }
        }
        if (!normal) return SnsPageAdvance(failure = "response")
        if (page.count != page.feeds.size) return SnsPageAdvance(failure = "count")
        // 3220's normal next-page branch sets s/isDownAll on an empty result
        // with no ContinueID. i() can also reflect local cache checks; it alone
        // does not prove that an unqualified empty first page ended the album.
        if (page.downAll && page.feeds.isEmpty() && page.continuation == 0L &&
            (cursor != 0L && page.lastId == cursor)) {
            return SnsPageAdvance(complete = true)
        }
        val next = page.continuation.takeIf { it != 0L } ?: page.lastId
        if (page.continuation == 0L && (page.feeds.isEmpty() || next != page.feeds.last().id)) {
            return SnsPageAdvance(failure = "empty_or_unmatched_cursor")
        }
        if (next == 0L || !used.add(next) ||
            (cursor != 0L && java.lang.Long.compareUnsigned(next, cursor) >= 0)) {
            return SnsPageAdvance(failure = "cursor_not_advanced")
        }
        if (!isSelf && recentLimitId != 0L &&
            java.lang.Long.compareUnsigned(next, recentLimitId) <= 0
        ) return SnsPageAdvance(failure = "privacy_boundary", includePage = true)
        cursor = next
        return SnsPageAdvance(next = next)
    }

    private companion object {
        val restrictedAlbumCodes = setOf(203, 2001, 2003, 2004, 2005)
        val rangeCodesWithPayload = setOf(2001, 2004, 2005)
    }
}

internal fun SnsSceneResponse.detailState(id: Long, owner: String): SnsDetailState {
    if (errType != 0 || errCode != 0) return SnsDetailState.UNKNOWN
    if (detail?.id == id && detail.author.isNotBlank()) {
        return if (detail.author == owner) SnsDetailState.OWN_PRESENT else SnsDetailState.OTHER_PRESENT
    }
    return if (detail == null && unavailableReason != 0) SnsDetailState.UNAVAILABLE else SnsDetailState.UNKNOWN
}

/** A self post can keep its local publish time in SnsInfo even after the server assigns CreateTime. */
internal fun NativeSnsFeed.matchesCachedIdentity(row: NativeSnsFeed, timeline: () -> NativeSnsFeed?): Boolean {
    if (id == 0L || author.isBlank() || time <= 0 || row.id != id || row.author != author) return false
    return row.time == time || timeline() == this
}

internal enum class OwnSnsDeleteKind {
    SUCCESS, NOT_SUBMITTED, SERVER_REJECTED, NO_CALLBACK, REMOTE_PRESENT, LOCAL_PENDING, UNCONFIRMED, SKIPPED
}

internal fun classifyAcceptedSnsDelete(detail: SnsDetailState, localPresent: Boolean?): OwnSnsDeleteKind = when {
    detail == SnsDetailState.OWN_PRESENT || detail == SnsDetailState.OTHER_PRESENT -> OwnSnsDeleteKind.REMOTE_PRESENT
    detail == SnsDetailState.UNAVAILABLE && localPresent == false -> OwnSnsDeleteKind.SUCCESS
    localPresent != false -> OwnSnsDeleteKind.LOCAL_PENDING
    else -> OwnSnsDeleteKind.UNCONFIRMED
}


internal enum class SnsDeleteAck { NOT_SUBMITTED, NO_CALLBACK, REJECTED, ACCEPTED, TRANSPORT_UNKNOWN }

internal fun classifySnsDeleteReply(result: SnsSceneResult): SnsDeleteAck = when (result) {
    is SnsSceneResult.NotSubmitted -> SnsDeleteAck.NOT_SUBMITTED
    SnsSceneResult.Unknown -> SnsDeleteAck.NO_CALLBACK
    is SnsSceneResult.Completed -> with(result.response) {
        when {
            errType == 0 && errCode == 0 -> SnsDeleteAck.ACCEPTED
            (errType == 4 || errType == 0) && errCode != 0 -> SnsDeleteAck.REJECTED
            else -> SnsDeleteAck.TRANSPORT_UNKNOWN
        }
    }
}
