package h.Hchat.preferences

import android.content.Context
import h.Hchat.hooks.items.antirecall.AntiRecallSettings
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitSettings
import h.Hchat.hooks.items.emojisave.EmojiSaveSettings
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitSettings
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelSettings
import h.Hchat.hooks.items.hchatextra.HchatExtraSettings
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateSettings
import h.Hchat.hooks.items.moments.RemoveMomentsAdsSettings
import h.Hchat.hooks.items.voiceforward.VoiceForwardSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeatureSettingsDefaultsRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `requested settings default on and explicit false values remain stored`() {
        val toggles = listOf(
            Toggle(AntiRecallSettings.PREFS_NAME, AntiRecallSettings.KEY_ENABLE, AntiRecallSettings.DEFAULT_ENABLE),
            Toggle(AntiRecallSettings.PREFS_NAME, AntiRecallSettings.KEY_KEEP_SELF_RECALL, AntiRecallSettings.DEFAULT_KEEP_SELF_RECALL),
            Toggle(AntiRecallSettings.PREFS_NAME, AntiRecallSettings.KEY_SHOW_NOTICE, AntiRecallSettings.DEFAULT_SHOW_NOTICE),
            Toggle(CallMediaLimitSettings.PREFS_NAME, CallMediaLimitSettings.KEY_ENABLE, CallMediaLimitSettings.DEFAULT_ENABLE),
            Toggle(EmojiSaveSettings.PREFS_NAME, EmojiSaveSettings.KEY_ENABLE, EmojiSaveSettings.DEFAULT_ENABLE),
            Toggle(RemoveForwardLimitSettings.PREFS_NAME, RemoveForwardLimitSettings.KEY_ENABLE, RemoveForwardLimitSettings.DEFAULT_ENABLE),
            Toggle(HchatExtraSettings.PREFS_NAME, HchatExtraSettings.KEY_RED_PACKET_DETAILS, HchatExtraSettings.DEFAULT_RED_PACKET_DETAILS),
            Toggle(RemoveMomentsAdsSettings.PREFS_NAME, RemoveMomentsAdsSettings.KEY_ENABLE, RemoveMomentsAdsSettings.DEFAULT_ENABLE),
            Toggle(QuickGroupChatLabelSettings.PREFS_NAME, QuickGroupChatLabelSettings.KEY_ENABLE, QuickGroupChatLabelSettings.DEFAULT_ENABLE),
            Toggle(DisableHotUpdateSettings.PREFS_NAME, DisableHotUpdateSettings.KEY_ENABLE, DisableHotUpdateSettings.DEFAULT_ENABLE)
        )

        toggles.forEachIndexed { index, toggle ->
            val prefs = HchatStorage.preferences(context, "feature_settings_defaults_$index")
            try {
                prefs.edit().clear().commit()
                assertTrue("${toggle.prefsName}/${toggle.key} default", toggle.defaultValue)
                assertFalse("${toggle.prefsName}/${toggle.key} starts absent", prefs.contains(toggle.key))
                assertTrue("${toggle.prefsName}/${toggle.key} reads enabled by default", prefs.getBoolean(toggle.key, toggle.defaultValue))

                prefs.edit().putBoolean(toggle.key, false).commit()
                assertFalse("${toggle.prefsName}/${toggle.key} keeps an explicit false", prefs.getBoolean(toggle.key, toggle.defaultValue))
            } finally {
                prefs.edit().clear().commit()
            }
        }
    }

    @Test
    fun `voice forward children inherit the enabled default and keep explicit choices`() {
        val prefs = HchatStorage.preferences(context, "voice_forward_defaults_regression")
        val childKeys = listOf(
            VoiceForwardSettings.KEY_CHAT_FORWARD_ENABLE,
            VoiceForwardSettings.KEY_CHAT_SAVE_ENABLE,
            VoiceForwardSettings.KEY_CHAT_MULTI_FORWARD_ENABLE,
            VoiceForwardSettings.KEY_CHAT_MULTI_MERGE_ENABLE,
            VoiceForwardSettings.KEY_FAVORITE_FORWARD_ENABLE,
            VoiceForwardSettings.KEY_FAVORITE_SAVE_ENABLE
        )

        try {
            prefs.edit().clear().commit()
            val defaultEnabled = prefs.getBoolean(VoiceForwardSettings.KEY_ENABLE, VoiceForwardSettings.DEFAULT_ENABLE)
            assertTrue("voice forward parent defaults enabled", defaultEnabled)
            childKeys.forEach { key ->
                assertTrue("$key inherits the enabled default", prefs.getBoolean(key, defaultEnabled))
            }

            prefs.edit().putBoolean(VoiceForwardSettings.KEY_ENABLE, false).commit()
            val savedDisabled = prefs.getBoolean(VoiceForwardSettings.KEY_ENABLE, VoiceForwardSettings.DEFAULT_ENABLE)
            assertFalse("saved parent false remains disabled", savedDisabled)
            childKeys.forEach { key ->
                assertFalse("$key inherits an explicitly saved disabled parent", prefs.getBoolean(key, savedDisabled))
            }

            prefs.edit()
                .putBoolean(VoiceForwardSettings.KEY_ENABLE, true)
                .putBoolean(VoiceForwardSettings.KEY_CHAT_SAVE_ENABLE, false)
                .commit()
            val enabledParent = prefs.getBoolean(VoiceForwardSettings.KEY_ENABLE, VoiceForwardSettings.DEFAULT_ENABLE)
            assertTrue(enabledParent)
            assertFalse(
                "an explicitly saved child false is preserved",
                prefs.getBoolean(VoiceForwardSettings.KEY_CHAT_SAVE_ENABLE, enabledParent)
            )
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private data class Toggle(
        val prefsName: String,
        val key: String,
        val defaultValue: Boolean
    )
}
