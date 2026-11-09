package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchOfficialUnfollowFinalStateVerifierTest {
    @Test
    fun `waits for the followed relation to disappear before confirming`() {
        var reads = 0
        var pauses = 0
        val result = BatchOfficialUnfollowFinalStateVerifier.awaitRemoval(
            targets = setOf("gh_target"),
            shouldContinue = { true },
            isAccountCurrent = { true },
            read = {
                reads++
                BatchOfficialUnfollowFinalStateVerifier.Read(
                    success = true,
                    followedUsernames = if (reads < 3) setOf("gh_target") else emptySet()
                )
            },
            maxAttempts = 4,
            pause = { pauses++ }
        )

        assertEquals(3, result.attempts)
        assertEquals(3, reads)
        assertEquals(2, pauses)
        assertTrue(result.lastRead?.success == true)
        assertTrue(result.stillFollowed.isEmpty())
        assertTrue(result.accountCurrent)
        assertFalse(result.stopped)
    }

    @Test
    fun `stops after bounded reads when relation remains active`() {
        var reads = 0
        val result = BatchOfficialUnfollowFinalStateVerifier.awaitRemoval(
            targets = setOf("gh_target"),
            shouldContinue = { true },
            isAccountCurrent = { true },
            read = {
                reads++
                BatchOfficialUnfollowFinalStateVerifier.Read(true, setOf("gh_target"))
            },
            maxAttempts = 3,
            pause = {}
        )

        assertEquals(3, reads)
        assertEquals(3, result.attempts)
        assertEquals(setOf("gh_target"), result.stillFollowed)
        assertTrue(result.lastRead?.success == true)
        assertFalse(result.stopped)
    }

    @Test
    fun `a transient read failure can recover without submitting another request`() {
        var reads = 0
        val result = BatchOfficialUnfollowFinalStateVerifier.awaitRemoval(
            targets = setOf("gh_target"),
            shouldContinue = { true },
            isAccountCurrent = { true },
            read = {
                reads++
                if (reads == 1) {
                    BatchOfficialUnfollowFinalStateVerifier.Read(
                        success = false,
                        followedUsernames = emptySet(),
                        error = "transient database read failure"
                    )
                } else {
                    BatchOfficialUnfollowFinalStateVerifier.Read(true, emptySet())
                }
            },
            maxAttempts = 3,
            pause = {}
        )

        assertEquals(2, reads)
        assertEquals(2, result.attempts)
        assertTrue(result.lastRead?.success == true)
        assertTrue(result.stillFollowed.isEmpty())
    }

    @Test
    fun `stop request ends state polling without another read`() {
        var mayContinue = true
        var reads = 0
        val result = BatchOfficialUnfollowFinalStateVerifier.awaitRemoval(
            targets = setOf("gh_target"),
            shouldContinue = { mayContinue },
            isAccountCurrent = { true },
            read = {
                reads++
                BatchOfficialUnfollowFinalStateVerifier.Read(true, setOf("gh_target"))
            },
            maxAttempts = 5,
            pause = { mayContinue = false }
        )

        assertEquals(1, reads)
        assertEquals(1, result.attempts)
        assertTrue(result.stopped)
        assertEquals(setOf("gh_target"), result.stillFollowed)
    }

    @Test
    fun `account change ends state polling without another read`() {
        var accountCurrent = true
        var reads = 0
        val result = BatchOfficialUnfollowFinalStateVerifier.awaitRemoval(
            targets = setOf("gh_target"),
            shouldContinue = { true },
            isAccountCurrent = { accountCurrent },
            read = {
                reads++
                BatchOfficialUnfollowFinalStateVerifier.Read(true, setOf("gh_target"))
            },
            maxAttempts = 5,
            pause = { accountCurrent = false }
        )

        assertEquals(1, reads)
        assertEquals(1, result.attempts)
        assertFalse(result.accountCurrent)
        assertEquals(setOf("gh_target"), result.stillFollowed)
    }
}
