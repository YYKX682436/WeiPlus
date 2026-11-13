package h.Hchat.ui.miuix

import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyStep
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.payment.core.RedPacketReplyStep
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import org.junit.Assert.*
import org.junit.Test

class SequenceEditingSessionTest {
    @Test fun newScheduledDraftIsEmptyButLegacyFieldsRemainReadable() {
        val draft = ScheduledTaskSettings.newDraft()
        assertTrue(scheduledTaskEditableItems(draft).isEmpty())
        val legacy = draft.copy(content = "old", contentItems = listOf("old", "old"))
        assertEquals(listOf("old", "old"), scheduledTaskEditableItems(legacy).map { it.value })
        val session = ScheduledContentSession(scheduledTaskEditableItems(legacy), false)
        session.entries.toList().forEach { session.remove(it.key) }
        assertTrue(scheduledTaskEditableItems(session.applyTo(legacy)).isEmpty())
    }

    @Test fun scheduledPickerResultsFollowIdentityAndPendingItemsStayOutOfExistingTask() {
        val text = ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_TEXT, "same")
        val image = ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_IMAGE, "/old.png")
        val session = ScheduledContentSession(listOf(text, image, text), true)
        val imageKey = session.entries[1].key
        assertTrue(session.move(imageKey, -1))
        assertTrue(session.setFiles(imageKey, ScheduledTaskSettings.TYPE_IMAGE, listOf("/new.png")))
        assertEquals("/new.png", session.entries.first().item.value)
        assertEquals(2, session.entries.count { it.item == text })
        val pending = session.add(ScheduledTaskSettings.TYPE_TEXT)
        session.update(pending, text.copy(value = "draft"))
        val task = ScheduledTaskSettings.newDraft()
        assertFalse(session.applyTo(task).items.any { it.value == "draft" })
        assertTrue(session.applyTo(task, includePendingKey = pending).items.any { it.value == "draft" })
        assertTrue(session.remove(imageKey))
        assertFalse(session.setFiles(imageKey, ScheduledTaskSettings.TYPE_IMAGE, listOf("/late.png")))
        assertFalse(session.entries.any { it.item.value == "/late.png" })
    }

    @Test fun scheduledCreationUsesTheSameScheduleValidationAsSaving() {
        val now = 1_700_000_000_000L
        val task = ScheduledTaskSettings.newDraft(now).copy(
            targetIds = listOf("recipient"),
            items = listOf(ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_TEXT, "content"))
        )
        assertNull(scheduledTaskValidationError(task, null, now))
        assertEquals("请选择每周重复日期", scheduledTaskValidationError(task.copy(repeatType = ScheduledTaskSettings.REPEAT_WEEKLY), null, now))
        assertEquals("计划时间需要晚于当前时间", scheduledTaskValidationError(task.copy(planTime = now - 1, planTimes = emptyList()), null, now))
    }

    @Test fun replyDraftsPreserveValidRuntimeValuesAndExplicitDeletionCanBeEmpty() {
        val original = AutoReplyStep(id = "original", content = "old")
        val session = AutoReplySequenceSession(listOf(original), true)
        session.add(AutoReplySettings.REPLY_TEXT)
        val pending = session.entries.last().step.id
        assertTrue(session.update(pending) { it.copy(content = "new") })
        assertEquals(listOf(original), session.stepsForDraft())
        assertTrue(session.commit(pending))
        assertEquals(listOf("old", "new"), session.stepsForDraft().map { it.content })
        session.update(original.id) { it.copy(content = "") }
        assertEquals("old", session.stepsForDraft().first().content)
        assertTrue(session.move(pending, -1))
        assertEquals(listOf(pending, original.id), session.stepsForDraft().map { it.id })
        assertTrue(session.remove(pending))
        assertTrue(session.remove(original.id))
        assertTrue(session.stepsForDraft().isEmpty())
        assertFalse(session.update(original.id) { it.copy(content = "late") })
    }

    @Test fun newReplyNeedsRealContentAndCanReturnToZero() {
        val session = AutoReplySequenceSession(emptyList(), false)
        session.add(AutoReplySettings.REPLY_TEXT)
        val id = session.entries.single().step.id
        assertEquals("", session.entries.single().step.content)
        assertFalse(session.isComplete())
        session.update(id) { it.copy(content = "hello") }
        assertTrue(session.isComplete())
        session.remove(id)
        assertTrue(session.stepsForDraft().isEmpty())
        assertFalse(session.isComplete())
    }

    @Test fun paymentReplyDraftDoesNotEnterExistingSequenceUntilCommitted() {
        val original = RedPacketReplyStep("one", RedPacketRuleConfig.REPLY_TEXT, "old", 100, true)
        val session = PaymentReplySequenceSession(listOf(original), true)
        session.add(RedPacketRuleConfig.REPLY_TEXT)
        val pending = session.entries.last().step.id
        assertEquals("", session.find(pending)?.step?.content)
        session.update(pending) { it.copy(content = "new") }
        assertEquals(listOf(original), session.values())
        assertTrue(session.commit(pending))
        assertEquals(listOf("old", "new"), session.values().map { it.content })
        session.update(original.id) { it.copy(content = "") }
        assertEquals("old", session.values().first().content)
        assertTrue(session.remove(original.id))
        assertFalse(session.update(original.id) { it.copy(content = "late result") })
        assertEquals(listOf("new"), session.values().map { it.content })
    }

    @Test fun groupMediaOrderPreservesDuplicatesAndUnknownLegacyEntries() {
        val session = GroupMediaSequenceSession("image,voice,image,legacy", true)
        val secondImage = session.entries[2].key
        assertTrue(session.move(secondImage, -1))
        assertEquals("image,image,voice,legacy", session.value())
        session.add("video")
        val pending = session.entries.last().key
        assertEquals("image,image,voice,legacy", session.value())
        session.commit(pending)
        assertEquals("image,image,voice,legacy,video", session.value())
        assertTrue(session.remove(secondImage))
        assertEquals("image,voice,legacy,video", session.value())
        assertNull(session.find(secondImage))
    }
}
