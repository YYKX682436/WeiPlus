package h.Hchat.hooks.items.textspeech

import android.app.Activity
import android.content.SharedPreferences
import android.view.KeyEvent
import io.github.libxposed.api.XposedInterface
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.FeatureRuntimeSlot
import h.Hchat.hooks.core.MessageObserveLease
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector

class TextSpeechFeature : BaseFeature() {
    private val runtimeSlot = FeatureRuntimeSlot<TextSpeechRuntime>()
    private var observeLease: MessageObserveLease? = null
    private var preferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var destroyed = false

    private val messageListener = WeChatMessageObserveApi.Listener { message ->
        runtimeSlot.current()?.handleMessage(message)
    }

    override fun featureId(): String = ID

    override fun name(): String = "文字转语音播报"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(TextSpeechSettingsProvider())
    }

    @Synchronized
    override fun onFeatureInstall(context: FeatureContext) {
        destroyed = false
        observeLease = MessageObserveLease().also(::trackSubscription)
        preferences = HchatStorage.preferences(
            context.hostContext(),
            TextSpeechSettings.PREFS_NAME
        ).also { prefs ->
            preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == TextSpeechSettings.KEY_ENABLE ||
                    key == TextSpeechSettings.KEY_ALLOWED_CONTACTS ||
                    key == TextSpeechSettings.KEY_VOLUME_CONTROL
                ) {
                    synchronizeRuntime()
                }
            }.also(prefs::registerOnSharedPreferenceChangeListener)
        }
        installVolumeKeyHook()
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
        runtimeSlot.clear(TextSpeechRuntime::shutdown)
    }

    @Synchronized
    private fun synchronizeRuntime() {
        if (destroyed) return
        val hostContext = context()?.hostContext() ?: return
        val settings = TextSpeechSettings(hostContext)
        val messageDemand = settings.hasEffectiveMessageTargets()
        val runtimeDemand = settings.requiresRuntime()
        if (runtimeDemand) {
            runtimeSlot.getOrCreate { TextSpeechRuntime(hostContext) }
        } else {
            runtimeSlot.clear(TextSpeechRuntime::shutdown)
        }
        observeLease?.sync(
            WeChatApis.message().observe(),
            enabled = messageDemand,
            listener = if (messageDemand) messageListener else null
        )
    }

    private fun installVolumeKeyHook() {
        val method = KavaReflector.findDeclaredMethod(
            Activity::class.java,
            "dispatchKeyEvent",
            KeyEvent::class.java
        )
        if (method == null) {
            HLog.e("$TAG 未找到 Activity.dispatchKeyEvent")
            return
        }
        runCatching {
            HookRegistry.get().hook(method, XposedInterface.Hooker { chain ->
                val args = chain.args
                var value: Any? = null
                var intercepted = false
                run before@ {
                    val event = args.getOrNull(0) as? KeyEvent ?: return@before
                    if (runtimeSlot.current()?.handleVolumeKey(event) == true) {
                        run { intercepted = true; value = true }
                    }
                }
                if (!intercepted) value = chain.proceed()
                value
            })
        }.onFailure {
            HLog.e("$TAG 音量键 Hook 安装失败: ${it.message}", it)
        }
    }

    companion object {
        const val ID = "text_speech"
        private const val TAG = "[Hchat:TextSpeech]"
    }
}
