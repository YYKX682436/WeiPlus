package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.media.WeChatFavoriteApi
import h.Hchat.ui.miuix.batchOperationsFeatureCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchOperationCatalogTest {
    @Test
    fun `the original twelve operations remain and round 2c adds two`() {
        assertEquals(14, batchOperationsFeatureCount())
        assertTrue(BatchOperation.entries.contains(BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS))
        assertTrue(BatchOperation.entries.contains(BatchOperation.DELETE_FAVORITES))
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
                BatchOperation.DELETE_FRIENDS
            ),
            BatchOperation.entries.take(12)
        )
    }

    @Test
    fun `favorite batch stops after unknown outcome account change or explicit stop`() {
        assertFalse(BatchFavoriteDeleteExecutionPolicy.afterNativeBatch(
            listOf(WeChatFavoriteApi.FavoriteDeleteStatus.CONFIRMED), true, false
        ).stopLater)
        assertTrue(BatchFavoriteDeleteExecutionPolicy.afterNativeBatch(
            listOf(WeChatFavoriteApi.FavoriteDeleteStatus.UNCONFIRMED), true, false
        ).stopLater)
        assertTrue(BatchFavoriteDeleteExecutionPolicy.afterNativeBatch(
            listOf(WeChatFavoriteApi.FavoriteDeleteStatus.CONFIRMED), false, false
        ).stopLater)
        assertTrue(BatchFavoriteDeleteExecutionPolicy.afterNativeBatch(
            listOf(WeChatFavoriteApi.FavoriteDeleteStatus.CONFIRMED), true, true
        ).stopLater)
    }
}
