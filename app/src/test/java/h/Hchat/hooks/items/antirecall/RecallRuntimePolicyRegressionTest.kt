package h.Hchat.hooks.items.antirecall

import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.model.WeChatMessageTypes
import h.Hchat.hooks.api.message.NativeSystemMessageInsertReceipt
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallRuntimePolicyRegressionTest {
    @Test
    fun duplicateRecallIsClaimedOnlyOnceAndFailedAttemptCanRetry() {
        val gate = RecallEventGate(maxEntries = 4)

        assertTrue(gate.begin("room:msg-1"))
        assertFalse(gate.begin("room:msg-1"))
        gate.complete("room:msg-1")
        assertFalse(gate.begin("room:msg-1"))

        assertTrue(gate.begin("room:msg-2"))
        gate.abort("room:msg-2")
        assertTrue(gate.begin("room:msg-2"))
    }

    @Test
    fun recallResolutionRejectsLocalIdCollisionAndCrossConversationRows() {
        val unrelated = message(msgId = 42L, msgSvrId = 9001L, talker = "other-chat")

        val notFound = RecallMessageResolver.resolve(
            talker = "target-chat",
            serverIds = listOf(42L),
            hinted = unrelated,
            cachedByServerId = { _, _ -> null },
            storedByServerId = { _, _ -> unrelated }
        )
        assertNull("a server id must never be accepted as a local msgId", notFound)

        val expected = message(msgId = 99L, msgSvrId = 42L, talker = "target-chat")
        val found = RecallMessageResolver.resolve(
            talker = "target-chat",
            serverIds = listOf(42L),
            hinted = unrelated,
            cachedByServerId = { _, _ -> null },
            storedByServerId = { session, serverId ->
                if (session == "target-chat" && serverId == 42L) expected else unrelated
            }
        )
        assertEquals(expected, found)
    }

    @Test
    fun timeNormalizationAndInvalidPatternUseConfiguredFallback() {
        assertEquals(1_700_000_000_000L, RecallNoticePolicy.normalizeMillis(1_700_000_000L))
        assertEquals(1_700_000_000_000L, RecallNoticePolicy.normalizeMillis(1_700_000_000_000L))

        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                "1970-01-01 00:00:01",
                RecallNoticePolicy.formatTime(1_000L, "not-a-pattern-Q", "yyyy-MM-dd HH:mm:ss")
            )
            assertEquals("", RecallNoticePolicy.formatTime(0L, "yyyy", "yyyy"))
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun insertionReceiptRejectsFailureWrongSessionAndWrongTimestamp() {
        val receipt = NativeSystemMessageInsertReceipt("target-chat", 2_000L)
        receipt.record("target-chat", "recall notice", WeChatMessageTypes.SYSTEM, 2_000L, 0L)
        assertNull(receipt.insertedMessage())

        receipt.record("other-chat", "recall notice", WeChatMessageTypes.SYSTEM, 2_000L, 501L)
        receipt.record("target-chat", "recall notice", WeChatMessageTypes.SYSTEM, 2_001L, 502L)
        assertNull(receipt.insertedMessage())

        receipt.record("target-chat", "recall notice with native expansion", WeChatMessageTypes.SYSTEM, 2_000L, 503L)
        assertEquals(503L, receipt.insertedMessage()?.messageId)
        assertEquals("recall notice with native expansion", receipt.insertedMessage()?.content)
        receipt.record("target-chat", "recall notice", WeChatMessageTypes.SYSTEM, 2_000L, 504L)
        assertEquals("only one actual insert result is retained", 503L, receipt.insertedMessage()?.messageId)
    }

    @Test
    fun noticeToggleDoesNotChangePreservationAndSelfRecallPolicy() {
        assertTrue(RecallNoticePolicy.shouldPreserve(true, selfRecall = false, keepSelfRecall = false))
        assertTrue(RecallNoticePolicy.shouldPreserve(true, selfRecall = true, keepSelfRecall = true))
        assertFalse(RecallNoticePolicy.shouldPreserve(false, selfRecall = false, keepSelfRecall = true))
        assertFalse(RecallNoticePolicy.shouldPreserve(true, selfRecall = true, keepSelfRecall = false))

        assertTrue(RecallNoticePolicy.shouldInsertNotice(true, true, selfRecall = false, keepSelfRecall = true))
        assertFalse(RecallNoticePolicy.shouldInsertNotice(true, false, selfRecall = false, keepSelfRecall = true))
        assertFalse(RecallNoticePolicy.shouldInsertNotice(true, true, selfRecall = true, keepSelfRecall = false))
    }

    @Test
    fun failedNoticeOrMissingSourceKeepsRecallSuppressedAndAllowsRetry() {
        val failedNotice = RecallNoticePolicy.decideEvent(
            antiRecallEnabled = true,
            selfRecall = false,
            keepSelfRecall = false,
            showNotice = true,
            noticeInserted = false
        )
        assertTrue(failedNotice.suppressOriginalRecall)
        assertFalse(failedNotice.markEventHandled)

        val noticeDisabled = RecallNoticePolicy.decideEvent(
            antiRecallEnabled = true,
            selfRecall = false,
            keepSelfRecall = false,
            showNotice = false,
            noticeInserted = false
        )
        assertTrue(noticeDisabled.suppressOriginalRecall)
        assertTrue(noticeDisabled.markEventHandled)

        val selfRecallNotKept = RecallNoticePolicy.decideEvent(
            antiRecallEnabled = true,
            selfRecall = true,
            keepSelfRecall = false,
            showNotice = true,
            noticeInserted = true
        )
        assertFalse(selfRecallNotKept.suppressOriginalRecall)
        assertFalse(selfRecallNotKept.markEventHandled)
    }

    private fun message(msgId: Long, msgSvrId: Long, talker: String): WeChatMessage {
        return WeChatMessage(
            msgId,
            msgSvrId,
            type = 1,
            status = 2,
            isSend = 0,
            createTime = 1_700_000_000_000L,
            talker = talker,
            content = "text",
            imagePath = "",
            reserved = "",
            translatedContent = "",
            flag = 0
        )
    }
}
