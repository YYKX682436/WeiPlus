package h.Hchat.ui.miuix

import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.isFutureEditedTask
import h.Hchat.hooks.items.payment.core.RedPacketRuleBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAutoSaveTest {
    @Test
    fun incompleteColorsAreNotValidConfiguration() {
        assertFalse(isCompleteColorSpec(""))
        assertFalse(isCompleteColorSpec("#FF12"))
        assertFalse(isCompleteColorSpec("#123456,#12"))
        assertFalse(isCompleteColorSpec("#123456,"))
        assertTrue(isCompleteColorSpec("#123456"))
        assertTrue(isCompleteColorSpec("#FF123456,#FFFFFFFF"))
        assertTrue(isCompleteColorSpec("#123456-#ABCDEF", allowAlternativeSeparators = true))
        assertFalse(isCompleteColorSpec("#123456-#ABCDEF"))
    }

    @Test
    fun incompleteAmountsDoNotBecomeZero() {
        listOf("", ".", "1.", "1,", "1.2.3", "-").forEach { assertFalse(it, isCompleteWalletAmount(it)) }
        listOf("0", "1234", "1,234.50").forEach { assertTrue(it, isCompleteWalletAmount(it)) }
        listOf("", ".", "1.", "1.2.3", "-", "1,000").forEach { assertFalse(it, isCompleteTransferAmount(it)) }
        listOf("0", "1234", "10.50").forEach { assertTrue(it, isCompleteTransferAmount(it)) }
    }

    @Test
    fun savingAnotherRedPacketBindingKeepsExistingCustomRule() {
        val existing = RedPacketRuleBinding("chat-a", "chat-a", "A", false, "template", true, null)
        val addition = RedPacketRuleBinding("chat-b", "chat-b", "B", false, "", false, null)
        val result = upsertRedPacketBindings(listOf(existing), listOf(addition))
        assertEquals(2, result.size)
        assertTrue(result.first { it.targetId == "chat-a" }.customRules)
    }

    @Test
    fun unrelatedTaskEditsDoNotChangeScheduleAndOverdueTasksAreNotRescheduled() {
        val now = 1_900_000_000_000L
        val task = ScheduledTaskSettings.newDraft(now).copy(targetIds = listOf("chat"), planTime = now + 60_000, planTimes = listOf(now + 60_000))
        assertFalse(scheduledTaskScheduleChanged(task, task.copy(remark = "edited")))
        assertTrue(scheduledTaskScheduleChanged(task, task.copy(planTime = now + 120_000, planTimes = listOf(now + 120_000))))
        assertTrue(isFutureEditedTask(task, now))
        assertFalse(isFutureEditedTask(task.copy(planTime = now), now))
        assertFalse(isFutureEditedTask(task.copy(status = ScheduledTaskSettings.STATUS_RUNNING), now))

        val incomplete = task.copy(remark = "edited", targetIds = emptyList(), planTime = 0, planTimes = emptyList())
        val independent = scheduledTaskIndependentEdits(task, incomplete)
        assertEquals("edited", independent.remark)
        assertEquals(task.targetIds, independent.targetIds)
        assertFalse(scheduledTaskScheduleChanged(task, independent))
    }

    @Test
    fun partiallyEditedTaskContentCannotDiscardAnExistingItem() {
        val task = ScheduledTaskSettings.newDraft().copy(items = listOf(
            ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_TEXT, "first"),
            ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_TEXT, "second")
        ))
        val editing = updateScheduledTaskContentItemValue(task, 0, "")
        assertTrue(scheduledTaskHasIncompleteContent(editing))
        assertFalse(scheduledTaskHasIncompleteContent(task))
        assertFalse(scheduledTaskHasIncompleteContent(task.copy(items = task.items.drop(1))))
    }
}
