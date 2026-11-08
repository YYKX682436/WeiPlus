package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDeleteOutcomePolicyTest {
    @Test
    fun nativeFriendResponseDistinguishesRejectionFromMissingResponse() {
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED,
            BatchDeleteOutcomePolicy.friendNativeResult(null)?.outcome)
        assertEquals(BatchTaskRunner.Outcome.FAILED,
            BatchDeleteOutcomePolicy.friendNativeResult(1001)?.outcome)
        assertNull(BatchDeleteOutcomePolicy.friendNativeResult(0))
    }

    @Test
    fun chatIndexIsNeverRemovedBeforeNativeMessageCleanupCompletes() {
        var removedConversation = false
        val result = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.UNCONFIRMED },
            mayDeleteConversation = { true },
            deleteConversation = {
                removedConversation = true
                BatchDeleteOutcomePolicy.NativeHomeDeleteState.CONFIRMED_REMOVED
            }
        )

        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, result.outcome)
        assertFalse(removedConversation)
    }

    @Test
    fun chatFailureAndUnknownDeletionStayDistinctFromConfirmedSuccess() {
        val clearFailure = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.NOT_SUBMITTED },
            mayDeleteConversation = { true },
            deleteConversation = { BatchDeleteOutcomePolicy.NativeHomeDeleteState.CONFIRMED_REMOVED }
        )
        assertEquals(BatchTaskRunner.Outcome.FAILED, clearFailure.outcome)

        val removalUnknown = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.COMPLETED },
            mayDeleteConversation = { null },
            deleteConversation = { error("must not remove when scope is unknown") }
        )
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, removalUnknown.outcome)

        val removalFailure = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.COMPLETED },
            mayDeleteConversation = { true },
            deleteConversation = { BatchDeleteOutcomePolicy.NativeHomeDeleteState.ROW_REMAINS }
        )
        assertEquals(BatchTaskRunner.Outcome.FAILED, removalFailure.outcome)

        val recreated = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.COMPLETED },
            mayDeleteConversation = { true },
            deleteConversation = { BatchDeleteOutcomePolicy.NativeHomeDeleteState.ROW_RECREATED }
        )
        assertEquals(BatchTaskRunner.Outcome.FAILED, recreated.outcome)

        val nativeHomeStale = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.COMPLETED },
            mayDeleteConversation = { true },
            deleteConversation = { BatchDeleteOutcomePolicy.NativeHomeDeleteState.HOME_STILL_CONTAINS }
        )
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, nativeHomeStale.outcome)

        val stoppedBeforeDelete = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.COMPLETED },
            mayDeleteConversation = { true },
            deleteConversation = { BatchDeleteOutcomePolicy.NativeHomeDeleteState.NOT_SUBMITTED }
        )
        assertEquals(BatchTaskRunner.Outcome.SKIPPED, stoppedBeforeDelete.outcome)

        val stoppedAfterClearSubmission = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.STOPPED },
            mayDeleteConversation = { error("must not remove after a stopped clear") },
            deleteConversation = { error("must not remove after a stopped clear") }
        )
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, stoppedAfterClearSubmission.outcome)
    }

    @Test
    fun friendRequiresBothConfirmedRelationshipRemovalAndEmptyHistory() {
        assertNull(BatchDeleteOutcomePolicy.friendState(validFriend = true, messagesRemain = null))
        assertNull(BatchDeleteOutcomePolicy.friendState(validFriend = false, messagesRemain = true))
        assertEquals(
            BatchTaskRunner.Outcome.SUCCESS,
            BatchDeleteOutcomePolicy.friendState(validFriend = false, messagesRemain = false)?.outcome
        )
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            BatchDeleteOutcomePolicy.friendState(validFriend = false, messagesRemain = null)?.outcome
        )
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            BatchDeleteOutcomePolicy.friendState(validFriend = null, messagesRemain = null)?.outcome
        )
    }
}
