package h.Hchat.ui.miuix

import androidx.compose.runtime.MonotonicFrameClock
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRoundTwoRegressionTest {
    private class DeferredInputFrameClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(30)
            return onFrame(1L)
        }
    }

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
    fun `shared picker sessions ignore changing callbacks and initial values`() {
        val messageBlock = MessageBlockContactPickerRequest(
            title = "选择适用聊天",
            existingValue = "chat-a",
            onValue = {}
        )
        assertEquals(
            messageBlock.sessionKey(),
            messageBlock.copy(existingValue = "chat-a|chat-b", onValue = {}).sessionKey()
        )
        assertNotEquals(
            messageBlock.sessionKey(),
            messageBlock.copy(allowOfficialAccounts = false).sessionKey()
        )

        val favorite = FavoritePickerRequest(
            title = "选择收藏",
            existingValue = "item-a",
            onValue = {}
        )
        assertEquals(
            favorite.sessionKey(),
            favorite.copy(existingValue = "item-a|item-b", onValue = {}).sessionKey()
        )
        assertNotEquals(favorite.sessionKey(), favorite.copy(delimiter = ",").sessionKey())

        val groupMembers = GroupMemberPickerRequest(
            title = "选择群成员",
            existingValue = "group-a/member-a",
            onValue = {},
            allowedGroupIds = setOf("group-a")
        )
        assertEquals(
            groupMembers.sessionKey(),
            groupMembers.copy(existingValue = "group-a/member-a|group-a/member-b", onValue = {}).sessionKey()
        )
        assertNotEquals(
            groupMembers.sessionKey(),
            groupMembers.copy(allowedGroupIds = setOf("group-b")).sessionKey()
        )

        val options = listOf(OptionItem("单次", 0), OptionItem("每天", 1))
        val optionPicker = OptionPickerRequest("重复方式", options, 0) {}
        assertEquals(
            optionPicker.sessionKey(),
            optionPicker.copy(currentValue = 1, onSelected = {}).sessionKey()
        )
        assertNotEquals(optionPicker.sessionKey(), optionPicker.copy(options = options.dropLast(1)).sessionKey())
    }

    @Test
    fun `pending focus loss commits only while the input remains composed`() = runBlocking {
        val disposedValues = mutableListOf<String>()
        val disposed = InputEditCompletionController(
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + DeferredInputFrameClock())
        )
        disposed.update("folded draft") { disposedValues += it }
        disposed.markEdited()
        disposed.onFocusChanged(false)
        disposed.dispose()
        delay(80)
        assertTrue(disposedValues.isEmpty())

        val focusedValues = mutableListOf<String>()
        val focused = InputEditCompletionController(
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + DeferredInputFrameClock())
        )
        focused.update("valid edit") { focusedValues += it }
        focused.markEdited()
        focused.onFocusChanged(false)
        delay(80)
        assertEquals(listOf("valid edit"), focusedValues)
        focused.dispose()
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
