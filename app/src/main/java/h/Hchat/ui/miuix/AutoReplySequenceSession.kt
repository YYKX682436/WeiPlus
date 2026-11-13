package h.Hchat.ui.miuix

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyStep
import java.util.UUID

internal data class AutoReplySequenceEntry(val step: AutoReplyStep, val pending: Boolean)

internal fun validAutoReplyStep(step: AutoReplyStep): Boolean =
    AutoReplySettings.isAiReplyMode(step.mode) || AutoReplySettings.isRemovedReplyMode(step.mode) || step.content.isNotBlank()

/** Keeps incomplete edits apart from the last valid, executable sequence. */
internal class AutoReplySequenceSession(initial: List<AutoReplyStep>, private val existing: Boolean) {
    var entries by mutableStateOf(initial.map { AutoReplySequenceEntry(it, false) })
        private set
    private var savedSteps = initial
    var expandedId by mutableStateOf<String?>(null)
    var scrollRequest by mutableStateOf<String?>(null)
    private val textStates = mutableStateMapOf<String, MutableState<TextFieldValue>>()
    private val delayStates = mutableStateMapOf<String, MutableState<String>>()

    fun find(id: String): AutoReplySequenceEntry? = entries.firstOrNull { it.step.id == id }

    fun textState(id: String): MutableState<TextFieldValue> = textStates.getOrPut(id) {
        val text = find(id)?.step?.content.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }

    fun delayState(id: String): MutableState<String> = delayStates.getOrPut(id) {
        mutableStateOf(find(id)?.step?.delayMs?.toString() ?: "0")
    }

    fun add(mode: Int) {
        val step = AutoReplyStep(id = UUID.randomUUID().toString(), mode = mode, content = "")
        entries = entries + AutoReplySequenceEntry(step, existing)
        expandedId = step.id
        scrollRequest = step.id
    }

    fun update(id: String, transform: (AutoReplyStep) -> AutoReplyStep): Boolean {
        val index = entries.indexOfFirst { it.step.id == id }
        if (index < 0) return false
        val previous = entries[index]
        val step = transform(previous.step).copy(id = id)
        entries = entries.toMutableList().also { it[index] = previous.copy(step = step) }
        textStates[id]?.let { state ->
            if (state.value.text != step.content) state.value = TextFieldValue(step.content, TextRange(step.content.length))
        }
        if (!previous.pending && validAutoReplyStep(step)) {
            savedSteps = savedSteps.map { if (it.id == id) step else it }
        }
        return true
    }

    fun commit(id: String): Boolean {
        val entry = find(id) ?: return false
        if (!entry.pending || !validAutoReplyStep(entry.step) || !delayValid(id)) return false
        entries = entries.map { if (it.step.id == id) it.copy(pending = false) else it }
        savedSteps = ordered(savedSteps + entry.step)
        return true
    }

    fun remove(id: String): Boolean {
        val entry = find(id) ?: return false
        entries = entries.filterNot { it.step.id == id }
        savedSteps = savedSteps.filterNot { it.id == id }
        textStates.remove(id)
        delayStates.remove(id)
        if (expandedId == id) expandedId = null
        return !entry.pending
    }

    fun move(id: String, offset: Int): Boolean {
        val index = entries.indexOfFirst { it.step.id == id }
        val target = index + offset
        if (index < 0 || target !in entries.indices) return false
        entries = entries.toMutableList().also { it.add(target, it.removeAt(index)) }
        savedSteps = ordered(savedSteps)
        return true
    }

    fun stepsForDraft(): List<AutoReplyStep> = if (existing) savedSteps else entries.map { it.step }
    fun isComplete(): Boolean = entries.isNotEmpty() && entries.all { validAutoReplyStep(it.step) && delayValid(it.step.id) }
    fun delayValid(id: String): Boolean = delayState(id).value.toLongOrNull()?.let { it in 0L..600000L } == true
    fun consumeScrollRequest(id: String) { if (scrollRequest == id) scrollRequest = null }

    private fun ordered(values: List<AutoReplyStep>): List<AutoReplyStep> {
        val positions = entries.mapIndexed { index, entry -> entry.step.id to index }.toMap()
        return values.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
    }
}
