package h.Hchat.hooks.api.media

import h.Hchat.ui.miuix.FavoritePickerFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteBatchPoliciesTest {
    @Test
    fun `only an error-free terminal page proves an empty or complete range`() {
        assertTrue(FavoriteSnapshotPolicy.isComplete(hasMore = false, error = null))
        assertFalse(FavoriteSnapshotPolicy.isComplete(hasMore = true, error = null))
        assertFalse(FavoriteSnapshotPolicy.isComplete(hasMore = false, error = "page failed"))
    }

    @Test
    fun `repeated identical page items deduplicate but conflicting IDs fail closed`() {
        val original = item(localId = 10, serverId = 500, type = 1, update = 1000)
        val repeated = item(localId = 10, serverId = 500, type = 1, update = 1000)
        val conflictingRemoteId = item(localId = 10, serverId = 501, type = 1, update = 1000)
        assertFalse(FavoriteSnapshotPolicy.hasConflictingDuplicate(original, repeated))
        assertTrue(FavoriteSnapshotPolicy.hasConflictingDuplicate(original, conflictingRemoteId))
        assertTrue(FavoriteSnapshotPolicy.changed(listOf(original), listOf(conflictingRemoteId)))
        assertFalse(FavoriteSnapshotPolicy.changed(listOf(original), listOf(repeated)))
    }

    @Test
    fun `delete targets require unique local and server IDs`() {
        val one = FavoriteDeleteTarget(1, 101, 1, 1000)
        val two = FavoriteDeleteTarget(2, 102, 2, 2000)
        assertTrue(FavoriteSnapshotPolicy.hasUniqueDeleteIds(listOf(one, two)))
        assertFalse(FavoriteSnapshotPolicy.hasUniqueDeleteIds(listOf(one, one.copy(localId = 3))))
        assertFalse(FavoriteSnapshotPolicy.hasUniqueDeleteIds(listOf(one, two.copy(serverId = 101))))
        assertTrue(FavoriteSnapshotPolicy.matchesDeleteTarget(one, 1, 101, 1, 1000, false))
        assertFalse(FavoriteSnapshotPolicy.matchesDeleteTarget(one, 1, 102, 1, 1000, false))
        assertFalse(FavoriteSnapshotPolicy.matchesDeleteTarget(one, 1, 101, 1, 1000, true))
    }

    @Test
    fun `all native favorite content types remain visible in default filter`() {
        val nativeTypes = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 14, 18, 19)
        nativeTypes.forEach { assertTrue(FavoritePickerFilter.ALL.matches(it)) }
        assertTrue(FavoritePickerFilter.OTHER.matches(8))
        assertFalse(FavoritePickerFilter.OTHER.matches(2))
    }

    @Test
    fun `special and serverless entries cannot enter destructive selection`() {
        assertTrue(FavoriteSnapshotPolicy.isSelectableForBatchDelete(item(1, 2, 1)))
        assertTrue(FavoriteSnapshotPolicy.isSelectableForBatchDelete(item(1, 2, 0)))
        assertFalse(FavoriteSnapshotPolicy.isSelectableForBatchDelete(item(1, 2, 1, supports = false)))
        assertFalse(FavoriteSnapshotPolicy.isSelectableForBatchDelete(item(1, 0, 1)))
    }

    @Test
    fun `native and picker caches are invalidated after any submitted batch`() {
        val pickerCache = linkedMapOf(1L to item(1, 11, 1))
        val nativeCache = linkedMapOf<Long, Any>(1L to Any())
        FavoriteCacheInvalidationPolicy.clear(pickerCache, nativeCache)
        assertTrue(pickerCache.isEmpty())
        assertTrue(nativeCache.isEmpty())
    }

    @Test
    fun `response and local verification are separate delete outcomes`() {
        val acceptedButStillLocal = FavoriteDeleteOutcomePolicy.classify(
            submitted = true,
            invocationUnknown = false,
            errorType = 0,
            errorCode = 0,
            itemResponseCode = 0,
            duplicateResponse = false,
            accountCurrent = true,
            localStateReadable = true,
            localItemStillPresent = true
        )
        assertEquals(WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED, acceptedButStillLocal.status)
        assertTrue(acceptedButStillLocal.stopLater)

        val rejected = FavoriteDeleteOutcomePolicy.classify(
            true, false, 0, 0, -1, false, true, true, false
        )
        assertEquals(WeChatFavoriteApi.FavoriteDeleteStatus.REJECTED, rejected.status)

        val timeout = FavoriteDeleteOutcomePolicy.classify(
            true, false, null, null, null, false, true, false, false
        )
        assertEquals(WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED, timeout.status)
        assertTrue(timeout.message.contains("未重试"))

        val confirmed = FavoriteDeleteOutcomePolicy.classify(
            true, false, 0, 0, 1, false, true, true, false
        )
        assertEquals(WeChatFavoriteApi.FavoriteDeleteStatus.CONFIRMED, confirmed.status)
        assertFalse(confirmed.stopLater)
    }

    @Test
    fun nativeCursorOrderingAndTerminalSignalAreSafe() {
        assertEquals(
            FavoritePaginationDecision.ADVANCE,
            FavoritePaginationPolicy.decide(currentCursor = 0L, nextCursor = 900L)
        )
        assertEquals(
            FavoritePaginationDecision.ADVANCE,
            FavoritePaginationPolicy.decide(currentCursor = 900L, nextCursor = 700L)
        )
        assertEquals(
            FavoritePaginationDecision.COMPLETE,
            FavoritePaginationPolicy.decide(currentCursor = 700L, nextCursor = 700L)
        )
        assertEquals(
            FavoritePaginationDecision.INVALID,
            FavoritePaginationPolicy.decide(currentCursor = 700L, nextCursor = 900L)
        )
        assertEquals(
            FavoritePaginationDecision.INVALID,
            FavoritePaginationPolicy.decide(currentCursor = 0L, nextCursor = 0L)
        )
    }

    private fun item(
        localId: Long,
        serverId: Int,
        type: Int,
        update: Long = 1000,
        supports: Boolean = true
    ) = WeChatFavoriteItem(
        localId, type, "title", "summary", 0L, update, emptyList(), serverId, supports
    )
}
