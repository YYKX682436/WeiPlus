package h.Hchat.hooks.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatRowRefreshTargetTest {
    @Test
    fun `only native chat item holders qualify a recycler view for bubble refresh`() {
        assertTrue(
            ChatRowRefresh.hasChatMessageRows(
                listOf("com.tencent.mm.ui.chatting.viewitems.MvvmChattingItem\$ViewHolder")
            )
        )
        assertFalse(
            ChatRowRefresh.hasChatMessageRows(
                listOf("com.tencent.mm.ui.mvvm.MvvmContactListUI\$ViewHolder")
            )
        )
    }

    @Test
    fun `holder package prefix must end at the viewitems boundary`() {
        assertFalse(
            ChatRowRefresh.hasChatMessageRows(
                listOf("com.tencent.mm.ui.chatting.viewitemsx.ContactViewHolder")
            )
        )
        assertFalse(ChatRowRefresh.hasChatMessageRows(listOf("com.tencent.mm.ui.chatting.viewitems")))
        assertFalse(ChatRowRefresh.hasChatMessageRows(listOf(null)))
    }
}
