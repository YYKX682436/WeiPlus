package h.Hchat.hooks.items.batch

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class BatchMomentContentType {
    TEXT,
    IMAGE,
    VIDEO,
    OTHER
}

internal data class BatchMomentItem(
    val snsId: String,
    val userName: String,
    val createTimeSeconds: Long,
    val contentType: BatchMomentContentType,
    val text: String,
    val mediaCount: Int = 0
)

internal data class BatchMomentFilter(
    val startDate: String = "",
    val endDate: String = "",
    val timeZoneId: String = TimeZone.getDefault().id,
    val contentType: BatchMomentContentType? = null,
    val query: String = ""
)

/** Pure selection rules. Dates are calendar dates in the supplied local time zone. */
internal object BatchMomentsSelection {
    fun contentTypeFromNative(type: String): BatchMomentContentType = when (type) {
        "text" -> BatchMomentContentType.TEXT
        "image", "live_photo" -> BatchMomentContentType.IMAGE
        "video" -> BatchMomentContentType.VIDEO
        else -> BatchMomentContentType.OTHER
    }

    fun filter(
        items: List<BatchMomentItem>,
        ownerUserName: String,
        filter: BatchMomentFilter
    ): List<BatchMomentItem> {
        val owner = ownerUserName.trim()
        if (owner.isEmpty() || !isValidDateRange(filter)) return emptyList()

        val startSeconds = filter.startDate.takeIf(String::isNotBlank)
            ?.let { startOfLocalDaySeconds(it, filter.timeZoneId) }
        val endExclusiveSeconds = filter.endDate.takeIf(String::isNotBlank)
            ?.let { startOfNextLocalDaySeconds(it, filter.timeZoneId) }
        val query = filter.query.trim().lowercase(Locale.ROOT)

        return items.asSequence()
            .filter { it.userName == owner && isValidSnsId(it.snsId) }
            .filter { item -> startSeconds == null || item.createTimeSeconds >= startSeconds }
            .filter { item -> endExclusiveSeconds == null || item.createTimeSeconds < endExclusiveSeconds }
            .filter { item -> filter.contentType == null || item.contentType == filter.contentType }
            .filter { item -> query.isEmpty() || item.text.lowercase(Locale.ROOT).contains(query) }
            .distinctBy(BatchMomentItem::snsId)
            .toList()
    }

    fun selectedFirst(
        visibleItems: List<BatchMomentItem>,
        selectedIds: Set<String>
    ): List<BatchMomentItem> = visibleItems.sortedWith(
        compareByDescending<BatchMomentItem> { it.snsId in selectedIds }
            .thenByDescending { it.createTimeSeconds }
            .thenBy { it.snsId }
    )

    fun selectAllCurrentFilter(
        selectedIds: Set<String>,
        visibleItems: List<BatchMomentItem>
    ): Set<String> {
        val visibleIds = visibleItems.mapTo(linkedSetOf(), BatchMomentItem::snsId)
        if (visibleIds.isEmpty()) return selectedIds
        return if (selectedIds.containsAll(visibleIds)) {
            selectedIds - visibleIds
        } else {
            selectedIds + visibleIds
        }
    }

    fun keepKnownSelections(selectedIds: Set<String>, loadedItems: List<BatchMomentItem>): Set<String> {
        val known = loadedItems.mapTo(hashSetOf(), BatchMomentItem::snsId)
        return selectedIds.filterTo(linkedSetOf()) { it in known }
    }

    fun isValidDateRange(filter: BatchMomentFilter): Boolean {
        if (filter.startDate.isBlank() || filter.endDate.isBlank()) return true
        val start = parseLocalDate(filter.startDate, filter.timeZoneId) ?: return false
        val end = parseLocalDate(filter.endDate, filter.timeZoneId) ?: return false
        return start <= end
    }

    fun startOfLocalDaySeconds(date: String, timeZoneId: String): Long? =
        parseLocalDate(date, timeZoneId)?.time?.div(1000L)

    fun startOfNextLocalDaySeconds(date: String, timeZoneId: String): Long? {
        val localDate = parseLocalDate(date, timeZoneId) ?: return null
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId), Locale.ROOT).apply {
            timeInMillis = localDate.time
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis / 1000L
    }

    fun isValidSnsId(rawId: String): Boolean {
        val id = rawId.trim().trim('\'', '"')
        if (id.isEmpty()) return false
        val signed = id.toLongOrNull() ?: runCatching { java.lang.Long.parseUnsignedLong(id) }.getOrNull()
        return signed != null && signed != 0L
    }

    fun summary(text: String, maxCharacters: Int = 120): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= maxCharacters) return collapsed
        return collapsed.take((maxCharacters - 1).coerceAtLeast(0)).trimEnd() + "…"
    }

    private fun parseLocalDate(date: String, timeZoneId: String): Date? {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone(timeZoneId)
        }
        return runCatching {
            val position = ParsePosition(0)
            formatter.parse(date, position)?.takeIf { position.index == date.length }
        }.getOrNull()
    }
}
