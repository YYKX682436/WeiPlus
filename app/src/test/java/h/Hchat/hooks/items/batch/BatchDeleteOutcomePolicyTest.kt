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
            deleteConversation = { removedConversation = true; true }
        )

        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, result.outcome)
        assertFalse(removedConversation)
    }

    @Test
    fun chatFailureAndUnknownDeletionStayDistinctFromConfirmedSuccess() {
        val clearFailure = BatchDeleteOutcomePolicy.deleteChat(
            clearMessages = { BatchDeleteOutcomePolicy.ChatClearState.NOT_SUBMITTED },
            mayDeleteConversation = { true },
            deleteConversation = { true }
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
            deleteConversation = { false }
        )
        assertEquals(BatchTaskRunner.Outcome.FAILED, removalFailure.outcome)
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
