package h.Hchat.hooks.api.sns

internal enum class SnsLikeState { LIKED, NOT_LIKED, UNKNOWN }

internal data class SnsLikeEvidence(val flag: Int, val users: Set<String>) {
    fun state(account: String): SnsLikeState = when {
        account.isBlank() -> SnsLikeState.UNKNOWN
        flag == 1 && account in users -> SnsLikeState.LIKED
        flag == 0 && account !in users -> SnsLikeState.NOT_LIKED
        else -> SnsLikeState.UNKNOWN
    }
}

internal fun verifiedLikeState(
    server: SnsLikeState, stored: SnsLikeState, localFlag: Int?, nativePending: Boolean?
): SnsLikeState = when {
    nativePending != false || server != stored -> SnsLikeState.UNKNOWN
    server == SnsLikeState.LIKED && localFlag == 1 -> server
    server == SnsLikeState.NOT_LIKED && localFlag == 0 -> server
    else -> SnsLikeState.UNKNOWN
}

internal enum class SnsLikeOutcome { SUCCESS, SKIPPED, NOT_SUBMITTED, REJECTED, UNCONFIRMED }

internal data class SnsLikeResult(val outcome: SnsLikeOutcome, val message: String)

internal enum class SnsLikeAck { NOT_SUBMITTED, REJECTED, ACCEPTED, UNKNOWN }

internal fun classifySnsLikeReply(result: SnsSceneResult): SnsLikeAck = when (result) {
    is SnsSceneResult.NotSubmitted -> SnsLikeAck.NOT_SUBMITTED
    SnsSceneResult.Unknown -> SnsLikeAck.UNKNOWN
    is SnsSceneResult.Completed -> with(result.response) {
        when {
            errType == 0 && errCode == 0 && protoRet == 0 -> SnsLikeAck.ACCEPTED
            (errType == 4 && errCode != 0) || (errType == 0 && (errCode != 0 || protoRet != null && protoRet != 0)) ->
                SnsLikeAck.REJECTED
            else -> SnsLikeAck.UNKNOWN
        }
    }
}

internal enum class SnsLikeConfirmation { CONFIRMED, REFRESH_DETAIL, CONFLICT }

internal fun likeConfirmation(
    target: NativeSnsFeed, reply: SnsSceneResponse, self: String, cacheLiked: Boolean
): SnsLikeConfirmation {
    val echoed = reply.detail
    if (echoed != null && (echoed.id != target.id ||
            echoed.author.isNotBlank() && echoed.author != target.author ||
            echoed.time != 0L && echoed.time != target.time)
    ) return SnsLikeConfirmation.CONFLICT
    val state = reply.detailEvidence?.likes?.state(self)
    return when {
        echoed == target && state == SnsLikeState.LIKED && cacheLiked -> SnsLikeConfirmation.CONFIRMED
        echoed == target && state == SnsLikeState.NOT_LIKED -> SnsLikeConfirmation.CONFLICT
        else -> SnsLikeConfirmation.REFRESH_DETAIL
    }
}

/** Batch and automatic likes share a single account/SNS-ID claim, including while verifying state. */
internal class SnsLikeClaims {
    private val active = HashSet<Pair<String, Long>>()

    @Synchronized fun tryClaim(account: String, id: Long): Boolean = active.add(account to id)
    @Synchronized fun release(account: String, id: Long) { active.remove(account to id) }
}
