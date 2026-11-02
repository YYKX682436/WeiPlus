package h.Hchat.hooks.items.customnotify

import android.content.SharedPreferences
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.MessageObserveLease
import h.Hchat.preferences.HchatStorage

class CustomNotificationFeature : BaseFeature() {
    private var observeLease: MessageObserveLease? = null
    private var preferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var destroyed = false

    private val messageListener = WeChatMessageObserveApi.Listener { message ->
        context()?.hostContext()?.let { CustomNotificationRuntime.handleMessage(it, message) }
    }

    override fun featureId(): String = ID

    override fun name(): String = "自定义通知"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(CustomNotificationSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        destroyed = false
        CustomNotificationRuntime.install(context)
        observeLease = MessageObserveLease().also(::trackSubscription)
        preferences = HchatStorage.preferences(
            context.hostContext(),
            CustomNotificationSettings.PREFS_NAME
        ).also { prefs ->
            preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == CustomNotificationSettings.KEY_ENABLE ||
                    key == CustomNotificationSettings.KEY_RULES ||
                    key == CustomNotificationSettings.KEY_DEFAULT_PRIVATE ||
                    key == CustomNotificationSettings.KEY_DEFAULT_GROUP ||
                    key == CustomNotificationSettings.KEY_DEFAULT_OFFICIAL
                ) {
                    synchronizeObserver()
                }
            }.also(prefs::registerOnSharedPreferenceChangeListener)
        }
        synchronizeObserver()
        subscribe(Events.DexReady::class.java) { synchronizeObserver() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        destroyed = true
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferenceListener = null
        preferences = null
        observeLease?.sync(null, enabled = false, listener = null)
        observeLease?.unsubscribe()
        observeLease = null
        CustomNotificationRuntime.setObservedMessageDemand(context.hostContext(), false)
    }

    private fun synchronizeObserver() {
        if (destroyed) return
        val hostContext = context()?.hostContext() ?: return
        val enabled = CustomNotificationSettings(hostContext).hasEffectiveNotificationRules()
        CustomNotificationRuntime.setObservedMessageDemand(hostContext, enabled)
        observeLease?.sync(
            WeChatApis.message().observe(),
            enabled = enabled,
            listener = if (enabled) messageListener else null
        )
    }

    companion object {
        const val ID = "custom_notification"
    }
}
