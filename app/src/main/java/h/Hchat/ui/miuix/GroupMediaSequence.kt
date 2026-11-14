package h.Hchat.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.UUID

internal data class GroupMediaEntry(val key: String, val type: String, val pending: Boolean)

internal class GroupMediaSequenceSession(initial: String, private val existing: Boolean) {
    var entries by mutableStateOf(initial.split(',').map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotEmpty() }.map { GroupMediaEntry(UUID.randomUUID().toString(), it, false) })
        private set
    var expandedKey by mutableStateOf<String?>(null)
    fun add(type: String) {
        val entry = GroupMediaEntry(UUID.randomUUID().toString(), type, existing)
        entries = entries + entry
        expandedKey = entry.key
    }
    fun find(key: String) = entries.firstOrNull { it.key == key }
    fun commit(key: String) { entries = entries.map { if (it.key == key) it.copy(pending = false) else it } }
    fun discardPending() {
        val pendingKeys = entries.filter { it.pending }.map { it.key }.toSet()
        entries = entries.filterNot { it.pending }
        if (expandedKey in pendingKeys) expandedKey = null
    }
    fun remove(key: String): Boolean {
        val entry = find(key) ?: return false
        entries = entries.filterNot { it.key == key }
        if (expandedKey == key) expandedKey = null
        return !entry.pending
    }
    fun move(key: String, offset: Int): Boolean {
        val index = entries.indexOfFirst { it.key == key }
        val target = index + offset
        if (index < 0 || target !in entries.indices) return false
        entries = entries.toMutableList().also { it.add(target, it.removeAt(index)) }
        return true
    }
    fun value() = entries.filterNot { it.pending }.joinToString(",") { it.type }
}

@Composable
internal fun GroupMediaDiscardDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BatchDeleteConfirmDialog(
        show = show,
        title = "放弃尚未加入的媒体？",
        message = "返回会放弃尚未加入的媒体顺序草稿。",
        labels = emptyList(),
        confirmText = "放弃并返回",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

internal data class GroupMediaDefinition(
    val type: String,
    val label: String,
    val mode: Int,
    val join: String,
    val left: String,
    val delay: String,
    val onJoin: (String) -> Unit,
    val onLeft: (String) -> Unit
)

@Composable
internal fun GroupMediaSequenceEditor(
    context: Context,
    session: GroupMediaSequenceSession,
    definitions: List<GroupMediaDefinition>,
    emptySummary: String,
    onChange: (String) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit
) {
    val entries = session.entries
    var showTypes by remember { mutableStateOf(false) }
    Column {
        SequenceSectionHeader("媒体类型顺序", entries.size, if (entries.isNotEmpty()) ({ showTypes = true }) else null)
        if (entries.isEmpty()) SequenceEmptyCard(emptySummary, "添加第一种媒体", { showTypes = true })
        SequenceTypePickerDialog(showTypes, "选择媒体类型", definitions.mapIndexed { i, item -> i to item.label }, { showTypes = false }) { index ->
            definitions.getOrNull(index)?.let { session.add(it.type); if (session.entries.last().pending.not()) onChange(session.value()) }
        }
        entries.forEachIndexed { index, entry -> key(entry.key) {
            val definition = definitions.firstOrNull { it.type == entry.type }
            val requester = remember(entry.key) { BringIntoViewRequester() }
            LaunchedEffect(session.expandedKey) {
                if (session.expandedKey == entry.key) { withFrameNanos { }; requester.bringIntoView() }
            }
            val count: (String) -> Int = { value -> value.split('|').count { it.isNotBlank() } }
            val summary = if (definition == null) "未知类型，运行时会跳过" else
                "进群 ${count(definition.join)} 项 · 退群 ${count(definition.left)} 项 · ${definition.delay}" + if (entry.pending) " · 尚未加入" else ""
            Box(Modifier.bringIntoViewRequester(requester)) { SequenceItemCard(
                title = "第 ${index + 1} 项 · ${definition?.label ?: entry.type}",
                summary = summary,
                expanded = session.expandedKey == entry.key,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                onToggle = { session.expandedKey = if (session.expandedKey == entry.key) null else entry.key }
            ) {
                if (entry.pending) {
                    InfoRow("素材", "沿用此类型现有的进群和退群素材")
                    ContentActionRow("添加此条", emphasis = ContentActionEmphasis.PRIMARY) { session.commit(entry.key); onChange(session.value()) }
                    ContentActionRow("放弃新增") { session.remove(entry.key) }
                } else {
                    if (definition != null) {
                        if (entries.count { it.type == entry.type } > 1) InfoRow("素材", "同类型条目共享素材")
                        GroupMediaFiles(context, definition, onFavoritePicker) { session.find(entry.key)?.type == entry.type }
                        InsetDivider()
                    }
                    SequenceItemActions(index > 0, index < entries.lastIndex,
                        onMoveUp = { if (session.move(entry.key, -1)) onChange(session.value()) },
                        onMoveDown = { if (session.move(entry.key, 1)) onChange(session.value()) },
                        onDelete = { if (session.remove(entry.key)) onChange(session.value()) })
                }
            } }
        } }
        val omitted = definitions.filter { definition -> entries.none { it.type == definition.type } }
        if (omitted.isNotEmpty()) {
            SettingsSectionTitle(text = "未加入顺序的素材")
            omitted.forEachIndexed { index, definition -> key("unused-${definition.type}") {
                val itemKey = "unused-${definition.type}"
                SequenceItemCard(
                    title = definition.label,
                    summary = "不在发送顺序中 · 可预先配置素材",
                    expanded = session.expandedKey == itemKey,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                    onToggle = { session.expandedKey = if (session.expandedKey == itemKey) null else itemKey }
                ) { GroupMediaFiles(context, definition, onFavoritePicker) { session.entries.none { it.type == definition.type } } }
            } }
        }
    }
}

@Composable
private fun GroupMediaFiles(context: Context, definition: GroupMediaDefinition, onFavoritePicker: (FavoritePickerRequest) -> Unit, stillPresent: () -> Boolean) {
    GroupMemberMediaRow(context, "进群${definition.label}", definition.join, definition.mode, onFavoritePicker) { value ->
        if (stillPresent()) definition.onJoin(value)
    }
    InsetDivider()
    GroupMemberMediaRow(context, "退群${definition.label}", definition.left, definition.mode, onFavoritePicker) { value ->
        if (stillPresent()) definition.onLeft(value)
    }
}
