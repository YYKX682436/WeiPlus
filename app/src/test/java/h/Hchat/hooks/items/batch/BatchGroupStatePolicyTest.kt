package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchGroupStatePolicyTest {
    @Test
    fun `confirmed member is eligible only when not the owner`() {
        assertTrue(BatchGroupStatePolicy.isLeaveCandidate(
            BatchGroupStatePolicy.State.JOINED, "self", "owner"
        ))
        assertFalse(BatchGroupStatePolicy.isLeaveCandidate(
            BatchGroupStatePolicy.State.JOINED, "self", "self"
        ))
        assertFalse(BatchGroupStatePolicy.isLeaveCandidate(
            BatchGroupStatePolicy.State.JOINED, "self", ""
        ))
    }

    @Test
    fun `missing group data and empty member lists stay unknown`() {
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, classify(
            readSucceeded = false, contact = true, room = true, members = "other", status = 0
        ))
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, classify(
            readSucceeded = true, contact = true, room = false, members = "other", status = 0
        ))
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, classify(
            readSucceeded = true, contact = true, room = true, members = "", status = 0
        ))
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, classify(
            readSucceeded = true, contact = true, room = true, members = null, status = 0
        ))
    }

    @Test
    fun `missing contact is not a residual cleanup candidate`() {
        assertEquals(BatchGroupStatePolicy.State.CONTACT_MISSING, classify(
            readSucceeded = true, contact = false, room = true, members = "other", status = 0
        ))
        assertNull(BatchGroupStatePolicy.cleanupReason(BatchGroupStatePolicy.State.CONTACT_MISSING))
    }

    @Test
    fun `native disband flag is independent evidence`() {
        assertEquals(BatchGroupStatePolicy.State.DISBANDED, classify(
            readSucceeded = true,
            contact = true,
            room = true,
            members = "",
            status = BatchGroupStatePolicy.DISBANDED_STATUS_FLAG
        ))
        assertEquals(
            BatchGroupStatePolicy.CleanupReason.DISBANDED,
            BatchGroupStatePolicy.cleanupReason(BatchGroupStatePolicy.State.DISBANDED)
        )
    }

    @Test
    fun `nonempty roster without self confirms already left or removed`() {
        assertEquals(BatchGroupStatePolicy.State.NOT_MEMBER, classify(
            readSucceeded = true, contact = true, room = true, members = "member-a;member-b", status = 0
        ))
        assertEquals(
            BatchGroupStatePolicy.CleanupReason.LEFT_OR_REMOVED,
            BatchGroupStatePolicy.cleanupReason(BatchGroupStatePolicy.State.NOT_MEMBER)
        )
    }

    @Test
    fun `native already quit predicate must agree with the roster`() {
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, BatchGroupStatePolicy.classify(
            true, true, true, "self", "member-a;member-b", 0, null
        ))
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, BatchGroupStatePolicy.classify(
            true, true, true, "self", "member-a;member-b", 0, false
        ))
        assertEquals(BatchGroupStatePolicy.State.NOT_MEMBER, BatchGroupStatePolicy.classify(
            true, true, true, "self", "member-a;member-b", 0, true
        ))
        assertEquals(BatchGroupStatePolicy.State.JOINED, BatchGroupStatePolicy.classify(
            true, true, true, "self", "member-a;self", 0, false
        ))
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN, BatchGroupStatePolicy.classify(
            true, true, true, "self", "member-a;self", 0, true
        ))
        assertEquals(BatchGroupStatePolicy.State.DISBANDED, BatchGroupStatePolicy.classify(
            true, true, true, "self", "", BatchGroupStatePolicy.DISBANDED_STATUS_FLAG, null
        ))
    }

    @Test
    fun `rejoined state or changed invalid reason stops cleanup`() {
        assertFalse(BatchGroupStatePolicy.stillMatchesCleanupReason(
            BatchGroupStatePolicy.State.JOINED,
            BatchGroupStatePolicy.CleanupReason.LEFT_OR_REMOVED
        ))
        assertFalse(BatchGroupStatePolicy.stillMatchesCleanupReason(
            BatchGroupStatePolicy.State.DISBANDED,
            BatchGroupStatePolicy.CleanupReason.LEFT_OR_REMOVED
        ))
        assertTrue(BatchGroupStatePolicy.stillMatchesCleanupReason(
            BatchGroupStatePolicy.State.NOT_MEMBER,
            BatchGroupStatePolicy.CleanupReason.LEFT_OR_REMOVED
        ))
    }

    @Test
    fun `unresolved leave blocks another submit until native membership state resolves`() {
        assertTrue(BatchGroupStatePolicy.shouldBlockRepeat(
            BatchGroupStatePolicy.State.JOINED, true
        ))
        assertTrue(BatchGroupStatePolicy.shouldBlockRepeat(
            BatchGroupStatePolicy.State.UNKNOWN, true
        ))
        assertFalse(BatchGroupStatePolicy.shouldBlockRepeat(
            BatchGroupStatePolicy.State.JOINED, false
        ))
        assertTrue(BatchGroupStatePolicy.resolvesPendingRequest(BatchGroupStatePolicy.State.NOT_MEMBER))
        assertTrue(BatchGroupStatePolicy.resolvesPendingRequest(BatchGroupStatePolicy.State.DISBANDED))
    }

    private fun classify(
        readSucceeded: Boolean,
        contact: Boolean,
        room: Boolean,
        members: String?,
        status: Int
    ) = BatchGroupStatePolicy.classify(
        readSucceeded, contact, room, "self", members, status
    )
}
