package h.Hchat.hooks.api.sns

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SnsFriendMomentsPolicyTest {
    private val target = NativeSnsFeed(80L, "friend", 123L)

    @Test fun friendAlbumDistinguishesEmptyRestrictedAndTransportFailure() {
        val empty = NativeSnsPage(emptyList(), 0, 0, 0, downAll = true, errType = 0, errCode = 0)
        assertTrue(SnsHistoryCursor(isSelf = false).advance(empty).complete)
        assertFalse(SnsHistoryCursor().advance(empty).complete)
        assertEquals("privacy_boundary",
            SnsHistoryCursor(isSelf = false).advance(empty.copy(recentLimitId = 80)).failure)
        assertEquals("privacy_boundary",
            SnsHistoryCursor(isSelf = false).advance(empty.copy(ret = 203, errType = 4, errCode = 203)).failure)
        assertEquals("response",
            SnsHistoryCursor(isSelf = false).advance(empty.copy(errType = 2, errCode = -1, hasRecentTips = true)).failure)
    }

    @Test fun theNative207EndIsNeitherAnEmptyAlbumNorANetworkFailure() {
        val empty = NativeSnsPage(emptyList(), 0, 0, 207, downAll = true, errType = 4, errCode = 207)
        val boundary = empty.copy(feeds = listOf(target), count = 1, lastId = target.id)
        val noTip = SnsHistoryCursor(isSelf = false).advance(boundary)
        assertEquals("visible_history_boundary", noTip.failure)
        assertTrue(noTip.includePage)
        assertFalse(noTip.complete)
        assertNull(noTip.next)
        assertEquals("visible_history_boundary", SnsHistoryCursor(isSelf = false).advance(empty).failure)
        assertEquals("privacy_boundary", SnsHistoryCursor(isSelf = false).advance(boundary.copy(hasRecentTips = true)).failure)
        assertEquals("privacy_boundary", SnsHistoryCursor(isSelf = false).advance(empty.copy(recentLimitId = 80)).failure)
        val unverified = SnsHistoryCursor(isSelf = false).advance(boundary.copy(lastId = 0))
        assertFalse(unverified.includePage)
        assertEquals("boundary_payload_unverified", unverified.failure)
        assertEquals("response", SnsHistoryCursor(isSelf = false).advance(boundary.copy(errType = 2)).failure)
    }

    @Test fun aNormalFirstPageMayAlreadyCarryTheNativeRangeTip() {
        val first = NativeSnsPage(listOf(target), 1, 0, 0, lastId = target.id,
            errType = 0, errCode = 0, hasRecentTips = true)
        val step = SnsHistoryCursor(isSelf = false).advance(first)
        assertEquals("privacy_boundary", step.failure)
        assertTrue(step.includePage)
        assertNull(step.next)
        assertEquals(80L, SnsHistoryCursor().advance(first).next)
    }

    @Test fun stopAtTheServerLimitAndKeepTheVisibleBoundaryPage() {
        val cursor = SnsHistoryCursor(isSelf = false)
        val first = NativeSnsPage(listOf(target), 1, 75, 0, lastId = 80, errType = 0, errCode = 0)
        assertEquals(75L, cursor.advance(first.copy(recentLimitId = 70)).next)
        val boundary = first.copy(feeds = listOf(target.copy(id = 70)), continuation = 0, lastId = 70)
        val step = cursor.advance(boundary)
        assertEquals("privacy_boundary", step.failure)
        assertTrue(step.includePage)
        assertNull(step.next)
    }

    @Test fun nativeRangeResponsesCanCarryTheLastVisiblePage() {
        for (code in listOf(2001, 2004, 2005)) {
            val boundary = NativeSnsPage(listOf(target), 1, 0, code, lastId = target.id,
                errType = 4, errCode = code)
            val step = SnsHistoryCursor(isSelf = false).advance(boundary)
            assertEquals("privacy_boundary", step.failure)
            assertTrue(step.includePage)
            assertNull(step.next)
            assertFalse(SnsHistoryCursor(isSelf = false).advance(boundary.copy(lastId = 0)).includePage)
            assertEquals("boundary_payload_unverified",
                SnsHistoryCursor(isSelf = false).advance(boundary.copy(count = 2)).failure)
            assertFalse(SnsHistoryCursor(isSelf = false).advance(boundary.copy(errCode = 203)).includePage)
            assertFalse(SnsHistoryCursor(isSelf = false).advance(boundary.copy(ret = 203, errCode = 203)).includePage)
        }
    }

    @Test fun likeFlagAndCurrentUserMustAgreeAndPendingActionsAreNeverSelectable() {
        val liked = SnsLikeState.LIKED
        val notLiked = SnsLikeState.NOT_LIKED
        val unknown = SnsLikeState.UNKNOWN
        assertEquals(liked, SnsLikeEvidence(1, setOf("me")).state("me"))
        assertEquals(notLiked, SnsLikeEvidence(0, setOf("someone")).state("me"))
        assertEquals(unknown, SnsLikeEvidence(1, setOf("someone")).state("me"))
        assertEquals(unknown, SnsLikeEvidence(0, setOf("me")).state("me"))
        assertEquals(unknown, SnsLikeEvidence(2, emptySet()).state("me"))
        assertEquals(unknown, verifiedLikeState(notLiked, liked, 0, false))
        assertEquals(unknown, verifiedLikeState(notLiked, notLiked, 1, false))
        assertEquals(unknown, verifiedLikeState(notLiked, notLiked, 0, null))
        assertEquals(unknown, verifiedLikeState(notLiked, notLiked, 0, true))
        assertEquals(notLiked, verifiedLikeState(notLiked, notLiked, 0, false))
    }

    @Test fun submittedAcceptedAndConfirmedAreDifferent() {
        fun ack(errType: Int, errCode: Int, ret: Int?) =
            classifySnsLikeReply(SnsSceneResult.Completed(SnsSceneResponse(errType, errCode, protoRet = ret)))
        assertEquals(SnsLikeAck.NOT_SUBMITTED, classifySnsLikeReply(SnsSceneResult.NotSubmitted("queue")))
        assertEquals(SnsLikeAck.UNKNOWN, classifySnsLikeReply(SnsSceneResult.Unknown))
        assertEquals(SnsLikeAck.UNKNOWN, ack(2, -1, null))
        assertEquals(SnsLikeAck.UNKNOWN, ack(0, 0, null))
        assertEquals(SnsLikeAck.REJECTED, ack(4, -1, -1))
        assertEquals(SnsLikeAck.REJECTED, ack(0, 0, -1))
        assertEquals(SnsLikeAck.ACCEPTED, ack(0, 0, 0))

        val liked = SnsSceneResponse(0, 0, detail = target,
            detailEvidence = SnsObjectEvidence(SnsLikeEvidence(1, setOf("me")), false), protoRet = 0)
        assertEquals(SnsLikeConfirmation.CONFIRMED, likeConfirmation(target, liked, "me", cacheLiked = true))
        assertEquals(SnsLikeConfirmation.REFRESH_DETAIL, likeConfirmation(target, liked, "me", cacheLiked = false))
        assertEquals(SnsLikeConfirmation.CONFLICT,
            likeConfirmation(target, liked.copy(detail = target.copy(id = 81)), "me", cacheLiked = true))
        val unlike = liked.copy(detailEvidence = SnsObjectEvidence(SnsLikeEvidence(0, emptySet()), false))
        assertEquals(SnsLikeConfirmation.CONFLICT, likeConfirmation(target, unlike, "me", cacheLiked = true))
    }

    @Test fun autoLikeAndBatchCannotBothClaimTheSameAccountAndTarget() {
        val claims = SnsLikeClaims()
        val barrier = CyclicBarrier(2)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val contenders = List(2) {
                workers.submit<Boolean> { barrier.await(5, TimeUnit.SECONDS); claims.tryClaim("me", target.id) }
            }
            assertEquals(1, contenders.count { it.get(5, TimeUnit.SECONDS) })
            assertTrue(claims.tryClaim("other", target.id))
            assertTrue(claims.tryClaim("me", target.id + 1))
            claims.release("me", target.id)
            assertTrue(claims.tryClaim("me", target.id))
        } finally {
            workers.shutdownNow()
        }
    }
}
