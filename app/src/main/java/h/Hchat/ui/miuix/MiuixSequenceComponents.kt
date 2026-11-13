package h.Hchat.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The header owns the space before and after a sequence, rather than each card doing so. */
@Composable
internal fun SequenceSectionHeader(title: String, count: Int, onAdd: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title · $count 条",
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Medium
        )
        if (onAdd != null) {
            Box(
                Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onAdd).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text("＋ 添加", color = MiuixTheme.colorScheme.primary) }
        }
    }
}

@Composable
internal fun SequenceItemActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Triple("上移", canMoveUp, onMoveUp), Triple("下移", canMoveDown, onMoveDown), Triple("删除", true, onDelete)).forEach { (label, enabled, action) ->
            Box(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).clickable(enabled = enabled, onClick = action),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = when {
                    !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
                    label == "删除" -> Color(0xffc43e3e)
                    else -> MiuixTheme.colorScheme.primary
                })
            }
        }
    }
}

@Composable
internal fun PrimaryFormAction(title: String, error: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        TextButton(
            text = title,
            onClick = onClick,
            enabled = error == null,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        if (error != null) {
            Text(error, modifier = Modifier.padding(top = 8.dp, start = 4.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun SequenceTypePickerDialog(
    show: Boolean,
    title: String,
    options: List<Pair<Int, String>>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    if (!show) return
    SettingsWindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Box(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            .clickable { onSelect(value); onDismiss() }.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) { Text(label, color = MiuixTheme.colorScheme.onSurface) }
                }
            }
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.CenterEnd) {
                Text("取消", color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
internal fun SequenceEmptyCard(message: String, action: String, onAdd: () -> Unit) {
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextButton(
                text = "＋ $action",
                onClick = onAdd,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
internal fun SequenceItemCard(
    title: String,
    summary: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    SettingsCard(modifier.animateContentSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                    .semantics { role = Role.Button; stateDescription = if (expanded) "已展开" else "已收起" }
                    .clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(if (expanded) "⌃" else "⌄", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    InsetDivider()
                    content()
                }
            }
        }
    }
}

@Composable
internal fun SettingsDisclosure(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit) {
    SettingsCard(modifier = Modifier.padding(top = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                .semantics { role = Role.Button; stateDescription = if (expanded) "已展开" else "已收起" }
                .clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                if (summary.isNotBlank()) Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
            Text(if (expanded) "⌃" else "⌄", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
        }
    }
}
