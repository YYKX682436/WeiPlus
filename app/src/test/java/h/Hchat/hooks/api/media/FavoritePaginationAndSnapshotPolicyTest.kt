package h.Hchat.hooks.api.media

import h.Hchat.ui.miuix.FavoritePickerFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritePaginationAndSnapshotPolicyTest {
    @Test
    fun `repeated identical page items deduplicate but conflicting snapshots fail closed`() {
        val original = item(localId = 10, type = 1, update = 1000)
        val repeated = item(localId = 10, type = 1, update = 1000)
        val conflictingSnapshot = item(localId = 10, type = 2, update = 1000)
        assertFalse(FavoriteSnapshotPolicy.hasConflictingDuplicate(original, repeated))
        assertTrue(FavoriteSnapshotPolicy.hasConflictingDuplicate(original, conflictingSnapshot))
    }

    @Test
    fun `all native favorite content types remain visible in default filter`() {
        val nativeTypes = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 14, 18, 19)
        nativeTypes.forEach { assertTrue(FavoritePickerFilter.ALL.matches(it)) }
        assertTrue(FavoritePickerFilter.OTHER.matches(8))
        assertFalse(FavoritePickerFilter.OTHER.matches(2))
    }

    @Test
    fun `favorite cache invalidation clears picker and native snapshots`() {
        val pickerCache = linkedMapOf(1L to item(1, 1))
        val nativeCache = linkedMapOf<Long, Any>(1L to Any())
        FavoriteCacheInvalidationPolicy.clear(pickerCache, nativeCache)
        assertTrue(pickerCache.isEmpty())
        assertTrue(nativeCache.isEmpty())
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

    private fun item(localId: Long, type: Int, update: Long = 1000) =
        WeChatFavoriteItem(localId, type, "title", "summary", 0L, update)
}
