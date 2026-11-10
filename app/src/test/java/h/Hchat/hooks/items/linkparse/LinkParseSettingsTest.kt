package h.Hchat.hooks.items.linkparse

import h.Hchat.preferences.HchatStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkParseSettingsTest {
    @Test fun ownMessagesBypassWhitelistAndOtherMessagesUseTheSameRuleInGroupsAndPrivateChats() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = HchatStorage.preferences(context, LinkParseSettings.PREFS_NAME)
        prefs.edit().clear().commit()
        val settings = LinkParseSettings(context)
        val dy = WorkLinkMatcher.Platform.DOUYIN
        val allowedChats = listOf("friend-id", "group-id@chatroom")
        val otherChats = listOf("other-friend", "other-group@chatroom")
        assertFalse(settings.allows(dy, otherChats.first(), outgoing = true))
        prefs.edit().putBoolean(LinkParseSettings.KEY_ENABLE, true).putBoolean(LinkParseSettings.KEY_DOUYIN, true).commit()
        assertFalse(settings.allows(dy, allowedChats.first(), outgoing = false))
        settings.saveAllowedTalkers(allowedChats.toSet())
        for (chat in allowedChats) {
            assertTrue(settings.allows(dy, chat, outgoing = false))
            assertFalse(settings.allows(WorkLinkMatcher.Platform.XIAOHONGSHU, chat, outgoing = false))
        }
        for (chat in otherChats) {
            assertFalse(settings.allows(dy, chat, outgoing = false))
            assertTrue(settings.allows(dy, chat, outgoing = true))
            assertFalse(settings.allows(WorkLinkMatcher.Platform.XIAOHONGSHU, chat, outgoing = true))
        }
        assertFalse(settings.allows(dy, "", outgoing = false))
        prefs.edit().clear().commit()
    }
}
