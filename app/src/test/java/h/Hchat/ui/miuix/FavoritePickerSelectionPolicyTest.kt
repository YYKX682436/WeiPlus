package h.Hchat.ui.miuix

import h.Hchat.hooks.api.media.WeChatFavoriteItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritePickerSelectionPolicyTest {
    @Test
    fun `search checks title summary and tags then pins selected matches`() {
        val titleMatch = item(1, 2, "Invoice image", "", listOf("work"))
        val summaryMatch = item(2, 5, "Link", "invoice from vendor", emptyList())
        val tagMatch = item(3, 8, "Spreadsheet", "document", listOf("invoice"))
        val unrelated = item(4, 2, "Photo", "holiday", emptyList())
        val visible = FavoritePickerSelectionPolicy.visibleItems(
            listOf(titleMatch, summaryMatch, unrelated, tagMatch),
            FavoritePickerFilter.ALL,
            "invoice",
            setOf("3")
        )
        assertEquals(listOf(3L, 1L, 2L), visible.map { it.localId })
    }

    @Test
    fun `all selects only the current filter result and preserves outside selection`() {
        val selected = setOf("1", "9")
        val visible = setOf("1", "2")
        assertEquals(setOf("1", "2", "9"), FavoritePickerSelectionPolicy.toggleVisible(selected, visible))
        assertEquals(setOf("9"), FavoritePickerSelectionPolicy.toggleVisible(setOf("1", "2", "9"), visible))
    }

    private fun item(
        id: Long,
        type: Int,
        title: String,
        summary: String,
        tags: List<String>
    ) = WeChatFavoriteItem(id, type, title, summary, 0, 0, tags, id.toInt())
}
