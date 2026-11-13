package h.Hchat.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.items.payment.core.RedPacketReplyStep
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

/** Shared by red-packet and transfer replies without changing either business model. */
@Composable
internal fun RedPacketReplyStepsPage(
    context: Context,
    title: String,
    initialSteps: List<RedPacketReplyStep>,
    onBack: () -> Unit,
    onSave: (List<RedPacketReplyStep>) -> Unit,
    templateVariables: List<TemplateVariable> = redPacketReplyTemplateVariables,
    draftOnly: Boolean = false
) {
    val session = remember { PaymentReplySequenceSession(initialSteps, existing = !draftOnly) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollOffset = with(LocalDensity.current) { 80.dp.roundToPx() }
    fun publish() = onSave(session.values())
    fun requestBack() {
        when {
            session.entries.any { !session.delayValid(it.step.id) } -> Toast.makeText(context, "请先修正回复延迟", Toast.LENGTH_SHORT).show()
            session.entries.any { it.pending } -> showDiscard = true
            else -> onBack()
        }
    }
    LaunchedEffect(session.scrollRequest, favoritePicker) {
        val id = session.scrollRequest ?: return@LaunchedEffect
        if (favoritePicker == null) {
            val index = session.entries.indexOfFirst { it.step.id == id }
            if (index >= 0) {
                withFrameNanos { }
                listState.animateScrollToItem(index + 1, -scrollOffset)
            }
            session.consumeScrollRequest(id)
        }
    }
    SettingsRouteTransition(
        targetState = favoritePicker,
        label = "PaymentReplyPicker",
        depthOf = { if (it == null) 0 else 1 }
    ) { picker ->
        if (picker != null) {
            FavoritePickerPage(picker, onBack = { favoritePicker = null })
        } else {
            val scrollBehavior = MiuixScrollBehavior()
            PageScaffold(title, title, scrollBehavior, onBack = ::requestBack, showBackIcon = false) { padding ->
                val entries = session.entries
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                ) {
                    item(key = "payment-reply-heading") {
                        var showTypes by remember { mutableStateOf(false) }
                        val add = { showTypes = true }
                        SequenceSectionHeader("回复步骤", entries.size, if (entries.isNotEmpty()) add else null)
                        if (entries.isEmpty()) SequenceEmptyCard("尚未添加回复", "添加第一条回复", add)
                        SequenceTypePickerDialog(
                            show = showTypes,
                            title = "选择回复类型",
                            options = redPacketReplyStepModeOptions().map { it.value to it.label },
                            onDismiss = { showTypes = false },
                            onSelect = { mode -> session.add(mode); if (draftOnly) publish() }
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        val step = entry.step
                        item(key = "payment-reply-${step.id}") {
                            SequenceItemCard(
                                title = "第 ${index + 1} 条 · ${redPacketReplyModeLabel(step.mode).removePrefix("发送")}",
                                summary = redPacketReplyStepSummary(step) + if (entry.pending) " · 尚未加入" else "",
                                expanded = session.expandedId == step.id,
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                                onToggle = { session.expandedId = if (session.expandedId == step.id) null else step.id }
                            ) {
                                PopupOptionRow("回复类型", redPacketReplyModeLabel(step.mode), redPacketReplyStepModeOptions(), step.mode, onValueChanged = { mode ->
                                    if (session.update(step.id) { it.copy(mode = mode, content = if (it.mode == mode) it.content else "") } && !entry.pending) publish()
                                })
                                InsetDivider()
                                RedPacketReplyContentEditor(
                                    context = context,
                                    replyMode = step.mode,
                                    value = step.content,
                                    onValueChange = { value ->
                                        val current = session.find(step.id)
                                        if (current?.step?.mode == step.mode && session.update(step.id) { it.copy(content = value) } && !current.pending) publish()
                                    },
                                    onClear = {
                                        val current = session.find(step.id)
                                        if (current?.step?.mode == step.mode && session.update(step.id) { it.copy(content = "") } && !current.pending) publish()
                                    },
                                    onFavoritePicker = { favoritePicker = it },
                                    templateVariables = templateVariables,
                                    fieldState = session.textState(step.id)
                                )
                                InsetDivider()
                                val delay = session.delayState(step.id)
                                NumberInputRow("发送前延迟", if (session.delayValid(step.id)) "单位 ms，按步骤顺序等待" else "请输入 0–600000，保留上次有效配置", delay.value) { value ->
                                    delay.value = value
                                    value.toLongOrNull()?.takeIf { it in 0L..600000L }?.let { millis ->
                                        if (session.update(step.id) { it.copy(delayMs = millis) } && !entry.pending) publish()
                                    }
                                }
                                InsetDivider()
                                SwitchRow(step.random, "随机追加延迟", "发送前额外随机等待 0–2 秒") { value ->
                                    if (session.update(step.id) { it.copy(random = value) } && !entry.pending) publish()
                                }
                                InsetDivider()
                                if (entry.pending) {
                                    ContentActionRow("添加此条") {
                                        if (session.commit(step.id)) publish() else Toast.makeText(context, "请填写有效的回复内容和延迟", Toast.LENGTH_SHORT).show()
                                    }
                                    ContentActionRow("放弃新增") { session.remove(step.id) }
                                } else {
                                    SequenceItemActions(index > 0, index < entries.lastIndex,
                                        onMoveUp = { if (session.move(step.id, -1)) publish() },
                                        onMoveDown = { if (session.move(step.id, 1)) publish() },
                                        onDelete = { if (session.remove(step.id)) publish() })
                                }
                            }
                        }
                    }
                }
            }
            BatchDeleteConfirmDialog(
                show = showDiscard,
                title = "放弃尚未加入的回复？",
                message = "返回会放弃尚未加入的回复草稿。",
                labels = emptyList(),
                confirmText = "放弃并返回",
                onDismiss = { showDiscard = false },
                onConfirm = { showDiscard = false; onBack() }
            )
        }
    }
}
