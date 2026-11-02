package h.Hchat.hooks.items.automessageforward

import android.content.SharedPreferences
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.MessageObserveLease
import h.Hchat.hooks.core.FeatureRuntimeSlot
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog

class AutoMessageForwardFeature : BaseFeature() {
    private val runtimeSlot = FeatureRuntimeSlot<AutoMessageForwardRuntime>()
    private var observeLease: MessageObserveLease? = null
    private var preferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var recallSubscription: h.Hchat.event.EventBus.Subscription? = null
    private var recallGeneration = 0L
    @Volatile private var destroyed = false

    private val messageListener = WeChatMessageObserveApi.Listener { message ->
        runtimeSlot.current()?.handleMessage(message)
    }

    override fun featureId(): String = ID

    override fun name(): String = "消息自动转发"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoMessageForwardSettingsProvider())
    }

    @Synchronized
    override fun onFeatureInstall(context: FeatureContext) {
        destroyed = false
        observeLease = MessageObserveLease().also(::trackSubscription)
        preferences = HchatStorage.preferences(
            context.hostContext(),
            AutoMessageForwardSettings.PREFS_NAME
        ).also { prefs ->
            preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == AutoMessageForwardSettings.KEY_ENABLED ||
                    key == AutoMessageForwardSettings.KEY_RULES
                ) {
                    synchronizeRuntime()
                }
            }.also(prefs::registerOnSharedPreferenceChangeListener)
        }
        synchronizeRuntime()
        subscribe(Events.DexReady::class.java) { synchronizeRuntime() }
    }

    @Synchronized
    override fun onFeatureDestroy(context: FeatureContext) {
        destroyed = true
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferenceListener = null
        preferences = null
        observeLease?.sync(null, enabled = false, listener = null)
        observeLease?.unsubscribe()
        observeLease = null
        releaseRecallSubscription(context)
        runtimeSlot.clear(AutoMessageForwardRuntime::shutdown)
    }

    @Synchronized
    private fun synchronizeRuntime() {
        if (destroyed) return
        val currentContext = context()?.hostContext() ?: return
        val enabled = AutoMessageForwardSettings.hasEffectiveRules(currentContext)
        if (enabled) {
            runtimeSlot.getOrCreate {
                AutoMessageForwardRuntime(currentContext) { message, error ->
                    HLog.e("$TAG $message", error)
                }
            }
            observeLease?.sync(
                WeChatApis.message().observe(),
                enabled = true,
                listener = messageListener
            )
            synchronizeRecallSubscription(
                AutoMessageForwardSettings.hasRecallFollowRules(currentContext)
            )
        } else {
            observeLease?.sync(null, enabled = false, listener = null)
            releaseRecallSubscription(context())
            runtimeSlot.clear(AutoMessageForwardRuntime::shutdown)
        }
    }

    private fun synchronizeRecallSubscription(enabled: Boolean) {
        val currentContext = context() ?: return
        if (!enabled) {
            releaseRecallSubscription(currentContext)
            return
        }
        if (recallSubscription != null) return
        val generation = ++recallGeneration
        recallSubscription = currentContext.eventBus().subscribe(
            Events.MessageRecalled::class.java
        ) { event ->
            if (!destroyed && generation == recallGeneration) {
                runtimeSlot.current()?.handleRecall(event)
            }
        }
    }

    private fun releaseRecallSubscription(context: FeatureContext?) {
        recallGeneration++
        val subscription = recallSubscription
        recallSubscription = null
        if (subscription != null && context != null) {
            runCatching { context.eventBus().unsubscribe(subscription) }
        }
    }

    companion object {
        const val ID = "auto_message_forward"
        private const val TAG = "[Hchat:AutoMessageForward]"
    }
}
