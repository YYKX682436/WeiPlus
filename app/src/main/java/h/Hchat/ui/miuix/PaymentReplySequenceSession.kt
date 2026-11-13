package h.Hchat.ui.miuix

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import h.Hchat.hooks.items.payment.core.RedPacketReplyStep
import java.util.UUID

internal data class PaymentReplyEntry(val step: RedPacketReplyStep, val pending: Boolean)

internal class PaymentReplySequenceSession(initial: List<RedPacketReplyStep>, private val existing: Boolean) {
    var entries by mutableStateOf(initial.map { PaymentReplyEntry(normalizeRedPacketReplyStepForUi(it), false) })
        private set
    private var saved = entries.map { it.step }
    var expandedId by mutableStateOf<String?>(null)
    var scrollRequest by mutableStateOf<String?>(null)
    private val texts = mutableStateMapOf<String, MutableState<TextFieldValue>>()
    private val delays = mutableStateMapOf<String, MutableState<String>>()

    fun find(id: String) = entries.firstOrNull { it.step.id == id }
    fun textState(id: String): MutableState<TextFieldValue> = texts.getOrPut(id) {
        val text = find(id)?.step?.content.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    fun delayState(id: String): MutableState<String> = delays.getOrPut(id) {
        mutableStateOf(find(id)?.step?.delayMs?.toString() ?: "0")
    }
    fun delayValid(id: String) = delayState(id).value.toLongOrNull()?.let { it in 0L..600000L } == true
    fun add(mode: Int) {
        val step = RedPacketReplyStep(UUID.randomUUID().toString(), mode, "", 0L, false)
        entries = entries + PaymentReplyEntry(step, existing)
        expandedId = step.id
        scrollRequest = step.id
    }
    fun update(id: String, transform: (RedPacketReplyStep) -> RedPacketReplyStep): Boolean {
        val index = entries.indexOfFirst { it.step.id == id }
        if (index < 0) return false
        val previous = entries[index]
        val step = transform(previous.step).copy(id = id)
        entries = entries.toMutableList().also { it[index] = previous.copy(step = step) }
        texts[id]?.let { if (it.value.text != step.content) it.value = TextFieldValue(step.content, TextRange(step.content.length)) }
        if (!previous.pending && step.content.isNotBlank()) saved = saved.map { if (it.id == id) normalizeRedPacketReplyStepForUi(step) else it }
        return true
    }
    fun commit(id: String): Boolean {
        val entry = find(id) ?: return false
        if (!entry.pending || entry.step.content.isBlank() || !delayValid(id)) return false
        entries = entries.map { if (it.step.id == id) it.copy(pending = false) else it }
        saved = ordered(saved + normalizeRedPacketReplyStepForUi(entry.step))
        return true
    }
    fun remove(id: String): Boolean {
        val entry = find(id) ?: return false
        entries = entries.filterNot { it.step.id == id }
        saved = saved.filterNot { it.id == id }
        texts.remove(id); delays.remove(id)
        if (expandedId == id) expandedId = null
        return !entry.pending
    }
    fun move(id: String, offset: Int): Boolean {
        val index = entries.indexOfFirst { it.step.id == id }
        val target = index + offset
        if (index < 0 || target !in entries.indices) return false
        entries = entries.toMutableList().also { it.add(target, it.removeAt(index)) }
        saved = ordered(saved)
        return true
    }
    fun values(): List<RedPacketReplyStep> = if (existing) saved else entries.map { it.step }
    fun consumeScrollRequest(id: String) { if (scrollRequest == id) scrollRequest = null }
    private fun ordered(values: List<RedPacketReplyStep>): List<RedPacketReplyStep> {
        val order = entries.mapIndexed { i, e -> e.step.id to i }.toMap()
        return values.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }
}
