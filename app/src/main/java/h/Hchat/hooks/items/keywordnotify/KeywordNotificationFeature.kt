package h.Hchat.hooks.items.keywordnotify

import android.content.SharedPreferences
import h.Hchat.event.Events
import h.Hchat.event.EventBus
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.MessageObserveLease
import h.Hchat.preferences.HchatStorage

class KeywordNotificationFeature : BaseFeature() {
    private var observeLease: MessageObserveLease? = null
    private var preferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var blockedSubscription: EventBus.Subscription? = null
    private var blockedGeneration = 0L
    @Volatile private var destroyed = false

    private val messageListener = WeChatMessageObserveApi.Listener { message ->
        context()?.hostContext()?.let { KeywordNotificationRuntime.handleMessage(it, message) }
    }

    override fun featureId(): String = ID

    override fun name(): String = "关键词通知"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(KeywordNotificationSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        destroyed = false
        observeLease = MessageObserveLease().also(::trackSubscription)
        preferences = HchatStorage.preferences(
            context.hostContext(),
            KeywordNotificationSettings.PREFS_NAME
        ).also { prefs ->
            preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                // Ignore diagnostic timestamps/last-match writes. Only source configuration
                // changes can change whether this feature needs the shared message stream.
                if (key == null || key in DEMAND_CONFIG_KEYS) synchronizeDemand()
            }.also(prefs::registerOnSharedPreferenceChangeListener)
        }
        synchronizeDemand()
        subscribe(Events.DexReady::class.java) { synchronizeDemand() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        destroyed = true
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferenceListener = null
        preferences = null
        observeLease?.sync(null, enabled = false, listener = null)
        observeLease?.unsubscribe()
        observeLease = null
        releaseBlockedSubscription(context)
        KeywordNotificationRuntime.setFeatureActive(context.hostContext(), false)
    }

    private fun synchronizeDemand() {
        if (destroyed) return
        val featureContext = context() ?: return
        val hostContext = featureContext.hostContext()
        val enabled = KeywordNotificationSettings(hostContext).hasEffectiveTriggers()
        KeywordNotificationRuntime.setFeatureActive(hostContext, enabled)
        observeLease?.sync(
            WeChatApis.message().observe(),
            enabled = enabled,
            listener = if (enabled) messageListener else null
        )
        synchronizeBlockedSubscription(featureContext, enabled)
    }

    private fun synchronizeBlockedSubscription(context: FeatureContext, enabled: Boolean) {
        if (!enabled) {
            releaseBlockedSubscription(context)
            return
        }
        if (blockedSubscription != null) return
        val generation = ++blockedGeneration
        blockedSubscription = context.eventBus().subscribe(Events.MessageBlocked::class.java) { event ->
            if (!destroyed && generation == blockedGeneration) {
                KeywordNotificationRuntime.handleBlockedMessage(context.hostContext(), event)
            }
        }
    }

    private fun releaseBlockedSubscription(context: FeatureContext) {
        blockedGeneration++
        val subscription = blockedSubscription
        blockedSubscription = null
        if (subscription != null) {
            runCatching { context.eventBus().unsubscribe(subscription) }
        }
    }

    companion object {
        const val ID = "keyword_notification"
        private val DEMAND_CONFIG_KEYS = setOf(
            KeywordNotificationSettings.KEY_ENABLE,
            KeywordNotificationSettings.KEY_KEYWORDS,
            KeywordNotificationSettings.KEY_ANY_GROUP,
            KeywordNotificationSettings.KEY_ANY_PRIVATE,
            KeywordNotificationSettings.KEY_AT_ME,
            KeywordNotificationSettings.KEY_AT_ALL,
            KeywordNotificationSettings.KEY_FILTER_MODE,
            KeywordNotificationSettings.KEY_INCLUDE_CONTACTS,
            KeywordNotificationSettings.KEY_EXCLUDE_CONTACTS
        )
    }
}
