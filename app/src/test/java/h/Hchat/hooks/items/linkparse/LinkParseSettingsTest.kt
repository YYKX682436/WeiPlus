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
    @Test fun platformAndGroupSwitchesFailClosed() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = HchatStorage.preferences(context, LinkParseSettings.PREFS_NAME)
        prefs.edit().clear().commit()
        val settings = LinkParseSettings(context)
        val dy = WorkLinkMatcher.Platform.DOUYIN
        assertFalse(settings.allows(dy, false, false))
        prefs.edit().putBoolean(LinkParseSettings.KEY_ENABLE, true).putBoolean(LinkParseSettings.KEY_DOUYIN, true).commit()
        assertTrue(settings.allows(dy, false, false))
        assertFalse(settings.allows(WorkLinkMatcher.Platform.KUAISHOU, false, false))
        assertFalse(settings.allows(dy, true, true))
        prefs.edit().putString(LinkParseSettings.KEY_GROUP_MODE, LinkParseSettings.GROUP_AT).commit()
        assertFalse(settings.allows(dy, true, false))
        assertTrue(settings.allows(dy, true, true))
        prefs.edit().putString(LinkParseSettings.KEY_GROUP_MODE, LinkParseSettings.GROUP_AUTO).commit()
        assertTrue(settings.allows(dy, true, false))
        prefs.edit().putString(LinkParseSettings.KEY_GROUP_MODE, "unexpected").commit()
        assertFalse(settings.allows(dy, true, true))
        prefs.edit().clear().commit()
    }
}
