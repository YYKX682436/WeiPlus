package h.Hchat.hooks.items.backgroundassist

import android.content.SharedPreferences
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.preferences.HchatStorage

class BackgroundRunAssistFeature : BaseFeature() {
    private var settingsPrefs: SharedPreferences? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun featureId(): String = ID

    override fun name(): String = "微信后台运行辅助"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(BackgroundRunAssistSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val hostContext = context.hostContext()
        settingsPrefs = HchatStorage.preferences(hostContext, BackgroundRunAssistSettings.PREFS_NAME)
        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in BackgroundRunAssistSettings.CONFIG_KEYS) {
                BackgroundRunAssistRuntime.apply(hostContext)
            }
        }.also { settingsPrefs?.registerOnSharedPreferenceChangeListener(it) }
        BackgroundRunAssistRuntime.apply(hostContext)
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        settingsListener?.let { settingsPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        settingsListener = null
        settingsPrefs = null
        BackgroundRunAssistRuntime.stop(context.hostContext())
    }

    companion object {
        // Keep the persisted feature switch stable; this is still the same user-facing feature.
        const val ID = "wechat_keep_alive"
    }
}
