package h.Hchat.ui.miuix

import h.Hchat.hooks.api.media.WeChatFavoriteItem
import java.util.Locale

internal object FavoritePickerSelectionPolicy {
    fun visibleItems(
        items: List<WeChatFavoriteItem>,
        filter: FavoritePickerFilter,
        query: String,
        selectedIds: Set<String>
    ): List<WeChatFavoriteItem> {
        val lower = query.trim().lowercase(Locale.US)
        return items.filter { item ->
            filter.matches(item.type) && (
                lower.isEmpty() ||
                    item.displayTitle().lowercase(Locale.US).contains(lower) ||
                    item.displaySummary().lowercase(Locale.US).contains(lower) ||
                    item.tags.any { it.lowercase(Locale.US).contains(lower) }
                )
        }.selectedFirst { selectedIds.contains(it.localId.toString()) }
    }

    fun toggleVisible(selectedIds: Set<String>, visibleIds: Set<String>): Set<String> =
        if (visibleIds.isNotEmpty() && visibleIds.all(selectedIds::contains)) {
            selectedIds - visibleIds
        } else {
            selectedIds + visibleIds
        }
}
