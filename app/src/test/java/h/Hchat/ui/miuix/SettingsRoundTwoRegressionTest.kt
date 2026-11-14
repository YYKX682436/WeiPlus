package h.Hchat.ui.miuix

import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRoundTwoRegressionTest {
    @Test
    fun `picker callbacks and live selection changes do not replace the session identity`() {
        val initial = ContactPickerRequest(
            title = "选择发送对象",
            mode = ContactPickerMode.ALL_CHATS,
            multiSelect = true,
            existingValue = "friend-a",
            onValue = {}
        )
        val changedWhileSelecting = initial.copy(
            existingValue = "friend-a|group-b",
            onValue = { }
        )

        assertEquals(initial.sessionKey(), changedWhileSelecting.sessionKey())
        assertNotEquals(initial.sessionKey(), changedWhileSelecting.copy(mode = ContactPickerMode.FRIENDS).sessionKey())
        assertNotEquals(initial.sessionKey(), changedWhileSelecting.copy(title = "选择群聊").sessionKey())
    }

    @Test
    fun `picker MIME and item limits follow the selected business type`() {
        assertEquals("image/*", scheduledTaskFilePickerMimeType(ScheduledTaskSettings.TYPE_IMAGE))
        assertEquals("video/*", scheduledTaskFilePickerMimeType(ScheduledTaskSettings.TYPE_VIDEO))
        assertEquals("*/*", scheduledTaskFilePickerMimeType(ScheduledTaskSettings.TYPE_FILE))
        assertTrue(isPhotoPickerMimeType("image/*"))
        assertTrue(isPhotoPickerMimeType("video/*"))
        assertFalse(isPhotoPickerMimeType("audio/*"))
        assertEquals(0, scheduledTaskFilePickerMaxCount(ScheduledTaskSettings.TYPE_IMAGE))
        assertEquals(1, scheduledTaskFilePickerMaxCount(ScheduledTaskSettings.TYPE_VIDEO))
        assertEquals(9, scheduledTaskFilePickerMaxCount(ScheduledTaskSettings.TYPE_IMAGE, requested = 9))

        assertEquals("image/*", replyPickerMimeType(AutoReplySettings.REPLY_IMAGE, autoReply = true))
        assertEquals("video/*", replyPickerMimeType(AutoReplySettings.REPLY_VIDEO, autoReply = true))
        assertEquals("audio/*", replyPickerMimeType(AutoReplySettings.REPLY_VOICE, autoReply = true))
        assertEquals("image/*", replyPickerMimeType(RedPacketRuleConfig.REPLY_EMOJI, autoReply = false))
        assertEquals("*/*", replyPickerMimeType(RedPacketRuleConfig.REPLY_FILE, autoReply = false))
    }

    @Test
    fun `disposing an edited field never commits but explicit submit does`() {
        val disposedValues = mutableListOf<String>()
        val disposed = InputEditCompletionController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        disposed.update("draft value") { disposedValues += it }
        disposed.markEdited()
        disposed.dispose()
        assertTrue(disposedValues.isEmpty())

        val submittedValues = mutableListOf<String>()
        val submitted = InputEditCompletionController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        submitted.update("valid value") { submittedValues += it }
        submitted.markEdited()
        submitted.finish()
        assertEquals(listOf("valid value"), submittedValues)
        submitted.finish()
        assertEquals(1, submittedValues.size)
    }
}
