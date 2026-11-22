package h.Hchat.hooks.items.linkparse

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
class LinkParseSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = HchatStorage.preferences(context, LinkParseSettings.PREFS_NAME)
    private val dy get() = WorkLinkMatcher.Platform.DOUYIN
    private val xhs get() = WorkLinkMatcher.Platform.XIAOHONGSHU

    private fun freshSettings(): LinkParseSettings {
        prefs.edit().clear().commit()
        return LinkParseSettings(context)
    }

    private fun enableDouyin() {
        prefs.edit()
            .putBoolean(LinkParseSettings.KEY_ENABLE, true)
            .putBoolean(LinkParseSettings.KEY_DOUYIN, true)
            .commit()
    }

    @Test fun freshConfigurationDefaultsToOpenBlacklistForFriendsAndGroups() {
        val settings = freshSettings()
        enableDouyin()

        assertEquals(TalkerListMode.BLACKLIST, settings.listMode())
        assertTrue(settings.allows(dy, "new-friend", outgoing = false))
        assertTrue(settings.allows(dy, "new-group@chatroom", outgoing = false))
        assertEquals(emptySet<String>(), settings.talkersFor(TalkerListMode.BLACKLIST))
    }

    @Test fun blacklistBlocksListedFriendsAndWholeGroups() {
        val settings = freshSettings()
        enableDouyin()
        settings.saveTalkers(TalkerListMode.BLACKLIST, setOf("blocked-friend", "blocked-group@chatroom"))

        assertFalse(settings.allows(dy, "blocked-friend", outgoing = false))
        assertFalse(settings.allows(dy, "blocked-group@chatroom", outgoing = false))
        assertTrue(settings.allows(dy, "other-friend", outgoing = false))
        assertTrue(settings.allows(dy, "other-group@chatroom", outgoing = false))
    }

    @Test fun whitelistOnlyAllowsListedFriendsAndGroups() {
        val settings = freshSettings()
        enableDouyin()
        settings.saveTalkers(TalkerListMode.WHITELIST, setOf("allowed-friend", "allowed-group@chatroom"))
        settings.saveListMode(TalkerListMode.WHITELIST)

        assertTrue(settings.allows(dy, "allowed-friend", outgoing = false))
        assertTrue(settings.allows(dy, "allowed-group@chatroom", outgoing = false))
        assertFalse(settings.allows(dy, "other-friend", outgoing = false))
        assertFalse(settings.allows(dy, "other-group@chatroom", outgoing = false))
        assertTrue(settings.allows(dy, "other-friend", outgoing = true))
    }

    @Test fun switchingModesKeepsBothIndependentLists() {
        val settings = freshSettings()
        enableDouyin()
        settings.saveTalkers(TalkerListMode.BLACKLIST, setOf("black-id"))
        settings.saveTalkers(TalkerListMode.WHITELIST, setOf("white-id"))

        settings.saveListMode(TalkerListMode.WHITELIST)
        assertFalse(settings.allows(dy, "black-id", outgoing = false))
        assertTrue(settings.allows(dy, "white-id", outgoing = false))
        settings.saveListMode(TalkerListMode.BLACKLIST)
        assertFalse(settings.allows(dy, "black-id", outgoing = false))
        assertTrue(settings.allows(dy, "white-id", outgoing = false))
    }

    @Test fun legacyAllowedTalkersUpgradeToWhitelistWithoutChangingBehavior() {
        prefs.edit().clear()
            .putBoolean(LinkParseSettings.KEY_ENABLE, true)
            .putBoolean(LinkParseSettings.KEY_DOUYIN, true)
            .putString(LinkParseSettings.KEY_ALLOWED_TALKERS, "[\"legacy-friend\",\"legacy-group@chatroom\"]")
            .commit()
        val settings = LinkParseSettings(context)

        assertEquals(TalkerListMode.WHITELIST, settings.listMode())
        assertTrue(settings.allows(dy, "legacy-friend", outgoing = false))
        assertTrue(settings.allows(dy, "legacy-group@chatroom", outgoing = false))
        assertFalse(settings.allows(dy, "new-friend", outgoing = false))
    }

    @Test fun legacyEmptyWhitelistAndSwitchOnlyConfigurationUpgradeToWhitelist() {
        prefs.edit().clear().putString(LinkParseSettings.KEY_ALLOWED_TALKERS, "").commit()
        val blankLegacyWhitelist = LinkParseSettings(context)
        assertEquals(TalkerListMode.WHITELIST, blankLegacyWhitelist.listMode())
        assertTrue(blankLegacyWhitelist.isTalkerListValid(TalkerListMode.WHITELIST))
        assertFalse(blankLegacyWhitelist.allows(dy, "new-friend", outgoing = false))

        prefs.edit().clear().putString(LinkParseSettings.KEY_ALLOWED_TALKERS, "[]").commit()
        assertEquals(TalkerListMode.WHITELIST, LinkParseSettings(context).listMode())

        prefs.edit().clear()
            .putBoolean(LinkParseSettings.KEY_ENABLE, true)
            .putBoolean(LinkParseSettings.KEY_DOUYIN, true)
            .commit()
        val switchOnly = LinkParseSettings(context)
        assertEquals(TalkerListMode.WHITELIST, switchOnly.listMode())
        assertFalse(switchOnly.allows(dy, "new-friend", outgoing = false))
    }

    @Test fun featureAndPlatformSwitchesRemainHighestPriority() {
        val settings = freshSettings()
        prefs.edit().putBoolean(LinkParseSettings.KEY_ENABLE, true).commit()
        settings.saveListMode(TalkerListMode.BLACKLIST)

        assertFalse(settings.allows(dy, "friend-id", outgoing = false))
        assertFalse(settings.allows(dy, "friend-id", outgoing = true))
        prefs.edit().putBoolean(LinkParseSettings.KEY_DOUYIN, true).commit()
        assertTrue(settings.allows(dy, "friend-id", outgoing = false))
        assertFalse(settings.allows(xhs, "friend-id", outgoing = false))
        prefs.edit().putBoolean(LinkParseSettings.KEY_ENABLE, false).commit()
        assertFalse(settings.allows(dy, "friend-id", outgoing = true))
    }

    @Test fun sameSettingsInstanceAppliesChangesImmediatelyAndOutgoingKeepsItsRule() {
        val settings = freshSettings()
        enableDouyin()
        assertTrue(settings.allows(dy, "immediate-id", outgoing = false))

        settings.saveTalkers(TalkerListMode.BLACKLIST, setOf("immediate-id"))
        assertFalse(settings.allows(dy, "immediate-id", outgoing = false))
        assertTrue(settings.allows(dy, "immediate-id", outgoing = true))
        settings.saveListMode(TalkerListMode.WHITELIST)
        assertFalse(settings.allows(dy, "immediate-id", outgoing = false))
        settings.saveTalkers(TalkerListMode.WHITELIST, setOf("immediate-id"))
        assertTrue(settings.allows(dy, "immediate-id", outgoing = false))
    }

    @Test fun invalidIdsMalformedListsAndUnknownModesFailClosedForOthers() {
        val settings = freshSettings()
        enableDouyin()

        listOf("", "   ", " friend-id", "friend-id\n").forEach { invalid ->
            assertFalse("Expected invalid ID to be denied: $invalid", settings.allows(dy, invalid, outgoing = false))
        }
        listOf("", "  \n", "not-json", "{\"friend\":\"id\"}", "[\"blocked\",7]", "[\"blocked\",{}]", "[\"\"]")
            .forEach { invalidJson ->
                prefs.edit().putString(LinkParseSettings.KEY_BLOCKED_TALKERS, invalidJson).commit()
                assertFalse("Expected invalid blacklist JSON to be rejected: $invalidJson",
                    settings.isTalkerListValid(TalkerListMode.BLACKLIST))
                assertFalse("Invalid blacklist JSON must deny others: $invalidJson",
                    settings.allows(dy, "ordinary-friend", outgoing = false))
            }

        prefs.edit().putString(LinkParseSettings.KEY_BLOCKED_TALKERS, "[]")
            .putString(LinkParseSettings.KEY_LIST_MODE, "future-mode").commit()
        assertEquals(TalkerListMode.WHITELIST, settings.listMode())
        assertFalse(settings.allows(dy, "ordinary-friend", outgoing = false))
    }

    @Test fun corruptedFeatureAndPlatformSwitchesFailClosed() {
        val settings = freshSettings()
        prefs.edit().putString(LinkParseSettings.KEY_ENABLE, "true")
            .putBoolean(LinkParseSettings.KEY_DOUYIN, true).commit()
        assertFalse(settings.allows(dy, "friend-id", outgoing = true))

        prefs.edit().putBoolean(LinkParseSettings.KEY_ENABLE, true)
            .putString(LinkParseSettings.KEY_DOUYIN, "true").commit()
        assertFalse(settings.allows(dy, "friend-id", outgoing = false))
    }
}
