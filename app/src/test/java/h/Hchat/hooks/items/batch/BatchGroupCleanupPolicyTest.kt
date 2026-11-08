package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchGroupCleanupPolicyTest {
    @Test
    fun `existing conversation uses native deletion before group deletion`() {
        assertEquals(
            BatchGroupCleanupPolicy.Action.DELETE_CONVERSATION_THEN_GROUP,
            BatchGroupCleanupPolicy.decide(
                true, BatchGroupCleanupPolicy.ConversationState.EXISTS
            )
        )
    }

    @Test
    fun `absent conversation skips duplicate deletion but allows invalid group cleanup`() {
        assertEquals(
            BatchGroupCleanupPolicy.Action.DELETE_GROUP_ONLY,
            BatchGroupCleanupPolicy.decide(
                true, BatchGroupCleanupPolicy.ConversationState.ABSENT
            )
        )
    }

    @Test
    fun `unread conversation and recovered valid group both abort`() {
        assertEquals(
            BatchGroupCleanupPolicy.Action.ABORT,
            BatchGroupCleanupPolicy.decide(
                true, BatchGroupCleanupPolicy.ConversationState.READ_FAILED
            )
        )
        assertEquals(
            BatchGroupCleanupPolicy.Action.ABORT,
            BatchGroupCleanupPolicy.decide(
                false, BatchGroupCleanupPolicy.ConversationState.ABSENT
            )
        )
    }
}
