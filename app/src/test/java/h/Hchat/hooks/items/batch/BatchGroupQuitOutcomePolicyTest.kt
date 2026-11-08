package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchGroupQuitOutcomePolicyTest {
    @Test
    fun `submission without response is unknown and must not become a retry`() {
        val decision = classify(submitted = true, transportUnknown = true)
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.REQUEST_RESULT_UNKNOWN, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
        assert(decision.message.contains("未重试"))
    }

    @Test
    fun `server acceptance and missing self membership confirms exit`() {
        val decision = classify(
            submitted = true,
            accepted = true,
            state = BatchGroupStatePolicy.State.NOT_MEMBER
        )
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.LEFT_CONFIRMED, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.SUCCESS, decision.outcome)
    }

    @Test
    fun `server acceptance does not confirm exit while membership remains`() {
        val decision = classify(
            submitted = true,
            accepted = true,
            state = BatchGroupStatePolicy.State.JOINED
        )
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.SERVER_ACCEPTED_STATE_UNKNOWN, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
    }

    @Test
    fun `concurrent group disband is not attributed to the leave request`() {
        val decision = classify(
            submitted = true,
            accepted = true,
            state = BatchGroupStatePolicy.State.DISBANDED
        )
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.SERVER_ACCEPTED_GROUP_DISBANDED, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
    }

    @Test
    fun `confirmed leave with failed native cleanup remains separately visible`() {
        val decision = classify(
            submitted = true,
            accepted = true,
            state = BatchGroupStatePolicy.State.CONTACT_MISSING,
            cleanupFailed = true
        )
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.LEFT_CONFIRMED_CLEANUP_UNKNOWN, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
    }

    @Test
    fun `explicit server rejection is failure`() {
        val decision = classify(submitted = true)
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.SERVER_REJECTED, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.FAILED, decision.outcome)
    }

    @Test
    fun `account change prevents success attribution even after acceptance`() {
        val decision = classify(
            submitted = true,
            accepted = true,
            state = BatchGroupStatePolicy.State.NOT_MEMBER,
            accountChanged = true
        )
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.ACCOUNT_CHANGED_UNKNOWN, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, decision.outcome)
    }

    @Test
    fun `not submitted is skipped`() {
        val decision = classify(submitted = false)
        assertEquals(BatchGroupQuitOutcomePolicy.Stage.NOT_SUBMITTED, decision.stage)
        assertEquals(BatchTaskRunner.Outcome.SKIPPED, decision.outcome)
    }

    private fun classify(
        submitted: Boolean = false,
        accepted: Boolean = false,
        state: BatchGroupStatePolicy.State = BatchGroupStatePolicy.State.UNKNOWN,
        cleanupFailed: Boolean = false,
        transportUnknown: Boolean = false,
        accountChanged: Boolean = false
    ) = BatchGroupQuitOutcomePolicy.classify(
        submitted, accepted, state, cleanupFailed, transportUnknown, accountChanged
    )
}
