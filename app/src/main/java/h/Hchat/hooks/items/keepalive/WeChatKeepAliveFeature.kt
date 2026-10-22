package h.Hchat.hooks.items.keepalive

import android.content.SharedPreferences
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.preferences.HchatStorage

class WeChatKeepAliveFeature : BaseFeature() {
    private var settingsPrefs: SharedPreferences? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun featureId(): String = ID

    override fun name(): String = "微信强保活"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(WeChatKeepAliveSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        settingsPrefs = HchatStorage.preferences(context.hostContext(), WeChatKeepAliveSettings.PREFS_NAME)
        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in WeChatKeepAliveSettings.CONFIG_KEYS) {
                WeChatKeepAliveRuntime.apply(context.hostContext(), context.moduleContext())
            }
        }.also { settingsPrefs?.registerOnSharedPreferenceChangeListener(it) }
        WeChatKeepAliveRuntime.apply(context.hostContext(), context.moduleContext())
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        settingsListener?.let { settingsPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        settingsListener = null
        settingsPrefs = null
        WeChatKeepAliveRuntime.stop(context.hostContext(), context.moduleContext())
    }

    companion object {
        const val ID = "wechat_keep_alive"
    }
}
