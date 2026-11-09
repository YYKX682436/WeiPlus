package h.Hchat.hooks.api.sns

internal data class SnsPageAdvance(val next: Long? = null, val complete: Boolean = false, val failure: String = "")

/** One cursor chain per open/reload. A new chain never inherits results or cursors. */
internal class SnsHistoryCursor {
    var cursor: Long = 0
        private set
    private val used = hashSetOf(0L)

    fun advance(page: NativeSnsPage): SnsPageAdvance {
        val normal = page.errType == 0 && page.errCode == 0 && page.ret == 0
        if (page.ret == 203) return SnsPageAdvance(failure = "privacy_boundary")
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
        cursor = next
        return SnsPageAdvance(next = next)
    }
}

internal fun SnsSceneResponse.detailState(id: Long, owner: String): SnsDetailState {
    if (errType != 0 || errCode != 0) return SnsDetailState.UNKNOWN
    if (detail?.id == id && detail.author.isNotBlank()) {
        return if (detail.author == owner) SnsDetailState.OWN_PRESENT else SnsDetailState.OTHER_PRESENT
    }
    return if (detail == null && unavailableReason != 0) SnsDetailState.UNAVAILABLE else SnsDetailState.UNKNOWN
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
