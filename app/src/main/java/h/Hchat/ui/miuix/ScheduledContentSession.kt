package h.Hchat.ui.miuix

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskItem
import java.util.UUID

internal data class ScheduledContentEntry(
    val key: String,
    val item: ScheduledTaskContentItem,
    val pending: Boolean
)

/** UI identities belong to this editing session and are never written to the task format. */
internal class ScheduledContentSession(initial: List<ScheduledTaskContentItem>, private val existingTask: Boolean) {
    var entries by mutableStateOf(initial.map { ScheduledContentEntry(newKey(), it, false) })
        private set
    var expandedKey by mutableStateOf<String?>(null)
    var scrollRequest by mutableStateOf<String?>(null)
    private val textStates = mutableStateMapOf<String, MutableState<TextFieldValue>>()

    fun find(key: String): ScheduledContentEntry? = entries.firstOrNull { it.key == key }

    fun textState(key: String): MutableState<TextFieldValue> = textStates.getOrPut(key) {
        val text = find(key)?.item?.value.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }

    fun add(type: Int): String {
        val key = newKey()
        entries = entries + ScheduledContentEntry(key, ScheduledTaskContentItem(type, ""), existingTask)
        expandedKey = key
        scrollRequest = key
        return key
    }

    fun update(key: String, item: ScheduledTaskContentItem): Boolean {
        val index = entries.indexOfFirst { it.key == key }
        if (index < 0) return false
        entries = entries.toMutableList().also { it[index] = it[index].copy(item = item) }
        textStates[key]?.let { state ->
            if (state.value.text != item.value) state.value = TextFieldValue(item.value, TextRange(item.value.length))
        }
        return true
    }

    fun setFiles(key: String, type: Int, paths: List<String>): Boolean {
        val index = entries.indexOfFirst { it.key == key && it.item.type == type }
        val clean = paths.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (index < 0 || clean.isEmpty()) return false
        val original = entries[index]
        val next = entries.toMutableList()
        next[index] = original.copy(item = ScheduledTaskContentItem(type, clean.first()))
        clean.drop(1).forEachIndexed { offset, path ->
            next.add(index + offset + 1, ScheduledContentEntry(newKey(), ScheduledTaskContentItem(type, path), existingTask))
        }
        entries = next
        return true
    }

    fun move(key: String, offset: Int): Boolean {
        val index = entries.indexOfFirst { it.key == key }
        val target = index + offset
        if (index < 0 || target !in entries.indices) return false
        entries = entries.toMutableList().also { it.add(target, it.removeAt(index)) }
        return true
    }

    fun remove(key: String): Boolean {
        if (find(key) == null) return false
        entries = entries.filterNot { it.key == key }
        textStates.remove(key)
        if (expandedKey == key) expandedKey = null
        return true
    }

    fun markCommitted(key: String) {
        entries = entries.map { if (it.key == key) it.copy(pending = false) else it }
    }

    fun consumeScrollRequest(key: String) {
        if (scrollRequest == key) scrollRequest = null
    }

    fun applyTo(task: ScheduledTaskItem, includePendingKey: String? = null, excludeKey: String? = null): ScheduledTaskItem = task.copy(
        items = entries.filter { it.key != excludeKey && (!it.pending || it.key == includePendingKey) }.map { it.item },
        content = "",
        contentItems = emptyList(),
        mediaPaths = emptyList()
    )

    private companion object {
        fun newKey(): String = UUID.randomUUID().toString()
    }
}
