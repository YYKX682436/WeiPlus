package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.contact.NativeBatchOfficialUnfollowProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchOfficialUnfollowOutcomePolicyTest {
    @Test
    fun `native batch scene uses WeChat's unsubscribe type and operation with the verified default cap`() {
        assertEquals(1, NativeBatchOfficialUnfollowProcess.CONTACT_TYPE_FOLLOWED_OFFICIAL_ACCOUNT)
        assertEquals(2, NativeBatchOfficialUnfollowProcess.CONTACT_TYPE_OPERATION_UNSUBSCRIBE)
        assertEquals(50, NativeBatchOfficialUnfollowProcess.NATIVE_MAX_BATCH_SIZE)
    }

    @Test
    fun `success requires server success and relationship removal`() {
        val decision = classify(serverResult = 0, finalRead = true, stillFollowed = false)
        assertEquals(BatchTaskRunner.Outcome.SUCCESS, decision.outcome)
        assertFalse(decision.stopLater)
    }

    @Test
    fun `server rejection is failure and does not masquerade as success`() {
        val decision = classify(serverResult = -3, finalRead = true, stillFollowed = true)
        assertEquals(BatchTaskRunner.Outcome.FAILED, decision.outcome)
        assertTrue(decision.stopLater)
    }

    @Test
    fun `unknown response stops later requests without retry`() {
        val decision = classify(serverResult = null)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
        assertTrue(decision.stopLater)
        assertTrue(decision.message.contains("未重试"))
    }

    @Test
    fun `active relation after zero response is not success`() {
        val decision = classify(serverResult = 0, finalRead = true, stillFollowed = true)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
        assertTrue(decision.stopLater)
    }

    @Test
    fun `account switch or unreadable final state prevents attribution`() {
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            classify(serverResult = 0, accountCurrent = false).outcome
        )
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            classify(serverResult = 0, finalRead = false).outcome
        )
    }

    @Test
    fun `native local reconciliation must also confirm the server accepted item`() {
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            classify(serverResult = 0, nativeLocal = false).outcome
        )
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            classify(serverResult = 0, cleanupFailed = true).outcome
        )
    }

    @Test
    fun `missing callback or request is not reported as successful`() {
        assertEquals(
            BatchTaskRunner.Outcome.UNCONFIRMED,
            classify(serverResult = 0, responseObserved = false).outcome
        )
        assertEquals(
            BatchTaskRunner.Outcome.FAILED,
            classify(submitted = false).outcome
        )
    }

    private fun classify(
        submitted: Boolean = true,
        responseObserved: Boolean = true,
        serverResult: Int? = 0,
        nativeLocal: Boolean = true,
        cleanupFailed: Boolean = false,
        finalRead: Boolean = true,
        stillFollowed: Boolean = false,
        accountCurrent: Boolean = true
    ) = BatchOfficialUnfollowOutcomePolicy.classify(
        submitted = submitted,
        responseObserved = responseObserved,
        accountCurrent = accountCurrent,
        serverResult = serverResult,
        nativeLocalResult = nativeLocal,
        nativeCleanupFailed = cleanupFailed,
        finalStateReadSucceeded = finalRead,
        stillFollowed = stillFollowed
    )
}
