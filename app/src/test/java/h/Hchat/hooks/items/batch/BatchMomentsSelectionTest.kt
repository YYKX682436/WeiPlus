package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.sns.SnsLikeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchMomentsSelectionTest {
    private val owner = "wxid.self"

    @Test
    fun `filters strictly to owner and keeps SNS IDs distinct from other IDs`() {
        val input = listOf(
            item("18446744073709551615", owner, 1_700_000_000),
            item("2", "wxid.other", 1_700_000_001),
            item("18446744073709551615", owner, 1_700_000_002),
            item("0", owner, 1_700_000_003)
        )
        val result = BatchMomentsSelection.filter(input, owner, BatchMomentFilter())
        assertEquals(listOf("18446744073709551615"), result.map { it.snsId })
    }

    @Test
    fun `date filter uses local calendar day boundaries including DST transitions`() {
        val zone = "America/New_York"
        val start = requireNotNull(BatchMomentsSelection.startOfLocalDaySeconds("2024-03-10", zone))
        val next = requireNotNull(BatchMomentsSelection.startOfNextLocalDaySeconds("2024-03-10", zone))
        assertEquals(23L * 60L * 60L, next - start)
        val justBeforeEnd = item("11", owner, next - 1)
        val nextDay = item("12", owner, next)
        val result = BatchMomentsSelection.filter(
            listOf(justBeforeEnd, nextDay), owner,
            BatchMomentFilter(startDate = "2024-03-10", endDate = "2024-03-10", timeZoneId = zone)
        )
        assertEquals(listOf("11"), result.map { it.snsId })
    }

    @Test
    fun `rejects reversed or malformed date ranges`() {
        assertFalse(BatchMomentsSelection.isValidDateRange(BatchMomentFilter("2024-02-02", "2024-02-01")))
        assertFalse(BatchMomentsSelection.isValidDateRange(BatchMomentFilter("2024-02-30", "2024-03-01")))
        assertFalse(BatchMomentsSelection.isValidDateRange(BatchMomentFilter("2024-01-01junk", "2024-01-02")))
        assertTrue(BatchMomentsSelection.isValidDateRange(BatchMomentFilter()))
    }

    @Test
    fun `maps live photos to images and cards to other`() {
        assertEquals(BatchMomentContentType.IMAGE, BatchMomentsSelection.contentTypeFromNative("live_photo"))
        assertEquals(BatchMomentContentType.IMAGE, BatchMomentsSelection.contentTypeFromNative("image"))
        assertEquals(BatchMomentContentType.VIDEO, BatchMomentsSelection.contentTypeFromNative("video"))
        assertEquals(BatchMomentContentType.TEXT, BatchMomentsSelection.contentTypeFromNative("text"))
        assertEquals(BatchMomentContentType.OTHER, BatchMomentsSelection.contentTypeFromNative("card"))
        assertEquals(BatchMomentContentType.OTHER, BatchMomentsSelection.contentTypeFromNative("unknown"))
    }

    @Test
    fun `search multi selection select all and selected first are scoped to current results`() {
        val first = item("1", owner, 1_700_000_000, text = "Hello first")
        val second = item("2", owner, 1_700_000_100, text = "other phrase")
        val otherOwner = item("3", "wxid.other", 1_700_000_200, text = "hello there")
        val searched = BatchMomentsSelection.filter(
            listOf(first, second, otherOwner), owner,
            BatchMomentFilter(query = "HELLO")
        )
        assertEquals(listOf("1"), searched.map { it.snsId })
        val selected = BatchMomentsSelection.selectAllCurrentFilter(setOf("2"), searched)
        assertEquals(setOf("1", "2"), selected)
        assertEquals("2", BatchMomentsSelection.selectedFirst(listOf(first, second), setOf("2")).first().snsId)
        assertEquals(setOf("2"), BatchMomentsSelection.selectAllCurrentFilter(selected, searched))
        assertEquals(setOf("2"), BatchMomentsSelection.keepKnownSelections(selected, listOf(second)))
        assertTrue(BatchMomentsSelection.selectAllCurrentFilter(emptySet(), emptyList()).isEmpty())
    }

    @Test
    fun `defaults are unselected and summary truncation is readable`() {
        val selected = emptySet<String>()
        assertTrue(selected.isEmpty())
        assertEquals("one two", BatchMomentsSelection.summary(" one\n two "))
        assertEquals("abcd…", BatchMomentsSelection.summary("abcdef", 5))
    }

    @Test
    fun selectAllForLikesExcludesConfirmedAndUnknownStates() {
        val unliked = item("7", owner, 500).copy(likeState = SnsLikeState.NOT_LIKED)
        val liked = item("8", owner, 501).copy(likeState = SnsLikeState.LIKED)
        val unknown = item("9", owner, 502)
        val visible = listOf(unliked, liked, unknown)
        val selected = BatchMomentsSelection.selectAllCurrentFilter(emptySet(), BatchMomentsSelection.selectable(visible, true))
        assertEquals(setOf("7"), selected)
        assertEquals(setOf("7", "8", "9"),
            BatchMomentsSelection.selectAllCurrentFilter(emptySet(), BatchMomentsSelection.selectable(visible, false)))
    }

    private fun item(id: String, user: String, time: Long, text: String = "") = BatchMomentItem(
        snsId = id,
        userName = user,
        createTimeSeconds = time,
        contentType = BatchMomentContentType.TEXT,
        text = text
    )
}
