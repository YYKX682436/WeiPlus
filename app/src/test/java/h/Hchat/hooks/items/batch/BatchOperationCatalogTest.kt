package h.Hchat.hooks.items.batch

import h.Hchat.ui.miuix.batchOperationsFeatureCount
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchOperationCatalogTest {
    @Test
    fun `the original thirteen operations remain and own moments deletion is appended`() {
        assertEquals(14, batchOperationsFeatureCount())
        assertEquals(
            listOf(
                BatchOperation.HIDE,
                BatchOperation.DO_NOT_DISTURB,
                BatchOperation.PIN,
                BatchOperation.FRIEND_LABEL,
                BatchOperation.STAR_FRIEND,
                BatchOperation.FRIEND_PERMISSION,
                BatchOperation.GROUP_SAVE_TO_CONTACTS,
                BatchOperation.GROUP_NICKNAME,
                BatchOperation.GROUP_LEAVE,
                BatchOperation.DELETE_INVALID_GROUPS,
                BatchOperation.DELETE_CHATS,
                BatchOperation.DELETE_FRIENDS,
                BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS,
                BatchOperation.DELETE_OWN_MOMENTS
            ),
            BatchOperation.entries
        )
    }
}
