package h.Hchat.ui.miuix

import h.Hchat.hooks.items.batch.BatchOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchCandidateScopeTest {
    private val home = listOf("home@chatroom", "friend-home", "gh_home", "filehelper", "message_fold", "stale-home")
    private val friends = setOf("friend-home", "friend-folded", "friend-no-session")
    private val groups = setOf("home@chatroom", "folded@im.chatroom", "no-session@chatroom")
    private val existing = setOf("friend-home", "friend-folded", "home@chatroom", "folded@im.chatroom", "gh_home", "filehelper")

    @Test
    fun doNotDisturbUsesNativeHomeOrTheRequestedContactDirectory() {
        val expected = mapOf(
            BatchScope.CURRENT to listOf("home@chatroom", "friend-home"),
            BatchScope.FRIENDS to friends.toList(),
            BatchScope.GROUPS to groups.toList(),
            BatchScope.ALL to (friends + groups).toList()
        )
        assertCandidates(BatchOperation.DO_NOT_DISTURB, expected)
    }

    @Test
    fun pinNeverOffersContactsWithoutAnExistingConversation() {
        val expected = mapOf(
            BatchScope.CURRENT to listOf("home@chatroom", "friend-home", "gh_home", "filehelper"),
            BatchScope.FRIENDS to listOf("friend-home", "friend-folded"),
            BatchScope.GROUPS to listOf("home@chatroom", "folded@im.chatroom"),
            BatchScope.ALL to listOf("friend-home", "friend-folded", "home@chatroom", "folded@im.chatroom")
        )
        assertCandidates(BatchOperation.PIN, expected)
    }

    private fun assertCandidates(operation: BatchOperation, expected: Map<BatchScope, List<String>>) {
        for (scope in BatchScope.entries) {
            for (enabled in listOf(true, false)) {
                val mode = requireNotNull(candidateMode(operation, scope, enabled))
                assertEquals("$operation $scope enabled=$enabled", expected.getValue(scope),
                    mode.scopedCandidateIds(home, friends, groups, existing))
            }
        }
    }
}
