package h.Hchat.hooks.items.musicorder

import h.Hchat.hooks.items.linkparse.LinkParseSettings
import h.Hchat.hooks.items.linkparse.WorkLinkMatcher
import h.Hchat.hooks.items.talkerlist.TalkerListMode
import h.Hchat.preferences.HchatStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QQMusicOrderSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = HchatStorage.preferences(context, QQMusicOrderSettings.PREFS_NAME)

    private fun freshSettings(): QQMusicOrderSettings {
        prefs.edit().clear().commit()
        val settings = QQMusicOrderSettings(context)
        prefs.edit().putBoolean(QQMusicOrderSettings.KEY_ENABLE, true).commit()
        return settings
    }

    @Test fun freshConfigurationDefaultsToBlacklistAndAllowsNewFriendsAndGroups() {
        val settings = freshSettings()

        assertEquals(TalkerListMode.BLACKLIST, settings.listMode())
        assertTrue(settings.talkersFor(TalkerListMode.BLACKLIST).isEmpty())
        assertTrue(settings.allows("new-friend", outgoing = false))
        assertTrue(settings.allows("new-group@chatroom", outgoing = false))
    }

    @Test fun blacklistBlocksListedFriendsAndGroups() {
        val settings = freshSettings()
        settings.saveTalkers(TalkerListMode.BLACKLIST, setOf("blocked-friend", "blocked-group@chatroom"))

        assertFalse(settings.allows("blocked-friend", outgoing = false))
        assertFalse(settings.allows("blocked-group@chatroom", outgoing = false))
        assertTrue(settings.allows("other-friend", outgoing = false))
    }

    @Test fun whitelistOnlyAllowsSelectedChatsAndKeepsOwnMessageBehavior() {
        val settings = freshSettings()
        settings.saveTalkers(TalkerListMode.WHITELIST, setOf("allowed-friend", "allowed-group@chatroom"))
        settings.saveListMode(TalkerListMode.WHITELIST)

        assertTrue(settings.allows("allowed-friend", outgoing = false))
        assertTrue(settings.allows("allowed-group@chatroom", outgoing = false))
        assertFalse(settings.allows("other-friend", outgoing = false))
        assertTrue(settings.allows("other-friend", outgoing = true))
    }

    @Test fun switchingModesPreservesEachList() {
        val settings = freshSettings()
        settings.saveTalkers(TalkerListMode.BLACKLIST, setOf("black-id"))
        settings.saveTalkers(TalkerListMode.WHITELIST, setOf("white-id"))

        settings.saveListMode(TalkerListMode.WHITELIST)
        assertFalse(settings.allows("black-id", outgoing = false))
        assertTrue(settings.allows("white-id", outgoing = false))
        settings.saveListMode(TalkerListMode.BLACKLIST)
        assertFalse(settings.allows("black-id", outgoing = false))
        assertTrue(settings.allows("white-id", outgoing = false))
    }

    @Test fun oldConfigurationAndAllowedTalkersUpgradeAsWhitelist() {
        prefs.edit().clear()
            .putBoolean(QQMusicOrderSettings.KEY_ENABLE, true)
            .putString(QQMusicOrderSettings.KEY_ALLOWED_TALKERS, "[\"legacy-friend\",\"legacy-group@chatroom\"]")
            .commit()
        val settings = QQMusicOrderSettings(context)

        assertEquals(TalkerListMode.WHITELIST, settings.listMode())
        assertTrue(settings.allows("legacy-friend", outgoing = false))
        assertTrue(settings.allows("legacy-group@chatroom", outgoing = false))
        assertFalse(settings.allows("new-friend", outgoing = false))
    }

    @Test fun enableAndDisableCommandsUpdateTheActiveListInBothModes() {
        val settings = freshSettings()
        val chat = "command-chat@chatroom"

        assertTrue(settings.setTalkerAllowed(chat, false))
        assertFalse(settings.allows(chat, outgoing = false))
        assertTrue(settings.setTalkerAllowed(chat, true))
        assertTrue(settings.allows(chat, outgoing = false))

        settings.saveListMode(TalkerListMode.WHITELIST)
        assertTrue(settings.setTalkerAllowed(chat, true))
        assertTrue(settings.allows(chat, outgoing = false))
        assertTrue(settings.setTalkerAllowed(chat, false))
        assertFalse(settings.allows(chat, outgoing = false))
    }

    @Test fun globalSwitchAndInvalidConfigurationFailClosedForOthers() {
        val settings = freshSettings()
        assertTrue(settings.allows("friend-id", outgoing = false))
        prefs.edit().putBoolean(QQMusicOrderSettings.KEY_ENABLE, false).commit()
        assertFalse(settings.allows("friend-id", outgoing = false))
        assertFalse(settings.allows("friend-id", outgoing = true))
        prefs.edit().putBoolean(QQMusicOrderSettings.KEY_ENABLE, true)
            .putString(QQMusicOrderSettings.KEY_BLOCKED_TALKERS, "broken-json").commit()
        assertFalse(settings.isTalkerListValid(TalkerListMode.BLACKLIST))
        assertFalse(settings.allows("friend-id", outgoing = false))
        assertFalse(settings.allows("", outgoing = false))
        assertFalse(settings.allows(" friend-id", outgoing = false))
    }

    @Test fun linkParsingAndMusicOrderStoreModesIndependently() {
        val music = freshSettings()
        val linkPrefs = HchatStorage.preferences(context, LinkParseSettings.PREFS_NAME)
        linkPrefs.edit().clear()
            .putBoolean(LinkParseSettings.KEY_ENABLE, true)
            .putBoolean(LinkParseSettings.KEY_DOUYIN, true)
            .commit()
        val link = LinkParseSettings(context)
        link.saveListMode(TalkerListMode.BLACKLIST)
        val chat = "shared-chat"

        music.saveTalkers(TalkerListMode.BLACKLIST, setOf(chat))
        assertFalse(music.allows(chat, outgoing = false))
        assertTrue(link.allows(WorkLinkMatcher.Platform.DOUYIN, chat, outgoing = false))
        link.saveListMode(TalkerListMode.WHITELIST)
        link.saveTalkers(TalkerListMode.WHITELIST, setOf("link-only"))
        assertFalse(link.allows(WorkLinkMatcher.Platform.DOUYIN, chat, outgoing = false))
        assertTrue(music.allows("link-only", outgoing = false))
    }

    @Test fun permissionChangesAffectTheNextCheckWithoutRecreatingSettings() {
        val settings = freshSettings()
        val friend = "live-update-friend"
        assertTrue(settings.allows(friend, outgoing = false))

        settings.setTalkerAllowed(friend, false)
        assertFalse(settings.allows(friend, outgoing = false))
        settings.saveListMode(TalkerListMode.WHITELIST)
        assertFalse(settings.allows(friend, outgoing = false))
        settings.setTalkerAllowed(friend, true)
        assertTrue(settings.allows(friend, outgoing = false))
    }
}
