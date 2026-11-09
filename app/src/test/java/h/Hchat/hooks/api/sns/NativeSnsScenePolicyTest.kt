package h.Hchat.hooks.api.sns

import org.junit.Assert.*
import org.junit.Test

class NativeSnsScenePolicyTest {
    private val owner = "owner"

    @Test fun routesOnlyTheExactNativeRequestAndDiscardsLateResults() {
        data class Scene(val author: String)
        val first = Scene(owner)
        val second = Scene(owner)
        val tickets = SnsSceneTickets<String>()
        tickets.put(first, "old")
        assertNull(tickets.get(second))
        tickets.remove(first)
        tickets.put(second, "new")
        assertNull(tickets.get(first))
        assertEquals("new", tickets.get(second))
    }

    @Test fun continuesThroughIntermediateMonthsBeforeUsingNativeLastId() {
        val cursor = SnsHistoryCursor()
        assertEquals(90L, cursor.advance(page(listOf(100, 91), continuation = 90)).next)
        assertEquals(70L, cursor.advance(page(listOf(89, 81, 70))).next)
        assertEquals(60L, cursor.advance(page(listOf(69, 60))).next)
        assertEquals("privacy_boundary", cursor.advance(page(emptyList(), ret = 203, end = true, errType = 4, errCode = 203)).failure)
        assertTrue(cursor.advance(page(emptyList(), end = true).copy(lastId = 60)).complete)
        assertEquals(0L, SnsHistoryCursor().cursor)
    }

    @Test fun emptyOrCachedResponseDoesNotProveEndAndRepeatedCursorFails() {
        assertFalse(SnsHistoryCursor().advance(page(emptyList())).complete)
        assertNotEquals("", SnsHistoryCursor().advance(page(emptyList(), ret = 207)).failure)
        assertNotEquals("", SnsHistoryCursor().advance(page(listOf(1)).copy(count = 2)).failure)
        val cursor = SnsHistoryCursor()
        cursor.advance(page(listOf(100, 90)))
        assertEquals("cursor_not_advanced", cursor.advance(page(listOf(90))).failure)
        assertEquals("cursor_not_advanced", cursor.advance(page(listOf(95))).failure)
    }

    @Test fun normalTerminalPageMustConfirmTheCursorAndNativeEnd() {
        val cursor = SnsHistoryCursor()
        assertFalse(cursor.advance(page(emptyList(), end = true)).complete)
        cursor.advance(page(listOf(75, 60)))
        assertFalse(cursor.advance(page(emptyList(), end = true).copy(lastId = 75)).complete)
        assertTrue(cursor.advance(page(emptyList(), end = true).copy(lastId = 60)).complete)
    }

    @Test fun paginationUsesServerRowsWithoutRequiringLocalMapping() {
        val cursor = SnsHistoryCursor()
        assertEquals(50L, cursor.advance(page(listOf(60, 50))).next)
        // A server page with zero locally available media/records still advances the cursor.
        assertEquals(40L, cursor.advance(page(listOf(49, 40))).next)
    }

    @Test fun verifiesServerTimestampInTheTimelineWhenTheLocalPublishClockDiffers() {
        val server = NativeSnsFeed(-76L, owner, 1_789_998_018L)
        val local = server.copy(time = server.time - 5)
        assertTrue(server.matchesCachedIdentity(local) { server })
        assertTrue(server.matchesCachedIdentity(server) { error("No second lookup is needed") })
        assertFalse(server.matchesCachedIdentity(local) { null })
        assertFalse(server.matchesCachedIdentity(local) { server.copy(time = server.time - 1) })
        assertFalse(server.matchesCachedIdentity(local) { server.copy(id = -77) })
        assertFalse(server.matchesCachedIdentity(local) { server.copy(author = "other") })
        assertFalse(server.matchesCachedIdentity(local.copy(author = "other")) { server })
        assertFalse(server.matchesCachedIdentity(local.copy(id = -77)) { server })
    }

    @Test fun targetDetailRequiresAnExactObjectOrAnExplicitUnavailableResponse() {
        assertEquals(SnsDetailState.OWN_PRESENT, SnsSceneResponse(0, 0,
            detail = NativeSnsFeed(7, owner, 9)).detailState(7, owner))
        assertEquals(SnsDetailState.OTHER_PRESENT, SnsSceneResponse(0, 0,
            detail = NativeSnsFeed(7, "other", 9)).detailState(7, owner))
        assertEquals(SnsDetailState.UNKNOWN, SnsSceneResponse(0, 0,
            detail = NativeSnsFeed(8, owner, 9)).detailState(7, owner))
        assertEquals(SnsDetailState.UNKNOWN, SnsSceneResponse(0, 0).detailState(7, owner))
        assertEquals(SnsDetailState.UNKNOWN, SnsSceneResponse(2, -1, unavailableReason = 1).detailState(7, owner))
        assertEquals(SnsDetailState.UNAVAILABLE, SnsSceneResponse(0, 0, unavailableReason = 1).detailState(7, owner))
    }

    @Test fun localAbsenceDoesNotOverrideRemotePresenceOrUnknownResult() {
        assertEquals(OwnSnsDeleteKind.REMOTE_PRESENT, classifyAcceptedSnsDelete(SnsDetailState.OWN_PRESENT, false))
        assertEquals(OwnSnsDeleteKind.UNCONFIRMED, classifyAcceptedSnsDelete(SnsDetailState.UNKNOWN, false))
        assertEquals(OwnSnsDeleteKind.LOCAL_PENDING, classifyAcceptedSnsDelete(SnsDetailState.UNAVAILABLE, true))
        assertEquals(OwnSnsDeleteKind.LOCAL_PENDING, classifyAcceptedSnsDelete(SnsDetailState.UNAVAILABLE, null))
        assertEquals(OwnSnsDeleteKind.SUCCESS, classifyAcceptedSnsDelete(SnsDetailState.UNAVAILABLE, false))
    }

    @Test fun queueRejectionServerRejectionTimeoutAndTransportErrorAreDifferent() {
        assertEquals(SnsDeleteAck.NOT_SUBMITTED, classifySnsDeleteReply(SnsSceneResult.NotSubmitted("queue")))
        assertEquals(SnsDeleteAck.NO_CALLBACK, classifySnsDeleteReply(SnsSceneResult.Unknown))
        assertEquals(SnsDeleteAck.REJECTED, classifySnsDeleteReply(SnsSceneResult.Completed(SnsSceneResponse(4, -1))))
        assertEquals(SnsDeleteAck.TRANSPORT_UNKNOWN, classifySnsDeleteReply(SnsSceneResult.Completed(SnsSceneResponse(2, -1))))
        assertEquals(SnsDeleteAck.ACCEPTED, classifySnsDeleteReply(SnsSceneResult.Completed(SnsSceneResponse(0, 0))))
    }

    private fun page(ids: List<Long>, continuation: Long = 0, ret: Int = 0, end: Boolean = false,
        errType: Int = 0, errCode: Int = 0) = NativeSnsPage(
        ids.map { NativeSnsFeed(it, owner, it) }, ids.size, continuation, ret,
        ids.lastOrNull() ?: 0, end, errType, errCode
    )
}
