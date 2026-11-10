package h.Hchat.hooks.items.inputhint

import h.Hchat.hooks.core.HookErrorBoundary
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import h.Hchat.event.Events
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.items.sendbutton.NativeSendButtonHook
import h.Hchat.utils.KavaReflector
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

class InputHintFeature : BaseFeature() {
    private var runtime: InputHintRuntime? = null
    private var statisticsInstalled = false
    private var statisticsGeneration = Long.MIN_VALUE
    private var statisticsSubscription: OutgoingMessageStatsRepository.Subscription? = null

    override fun featureId(): String = ID

    override fun name(): String = "输入框提示"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(InputHintSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = InputHintRuntime(context.hostContext(), context.hostClassLoader(), ::logError)
        runtime?.installInputHook()
        installStatistics()
        subscribe(Events.DexReady::class.java) {
            runtime?.installInputHook()
            installStatistics()
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
        statisticsSubscription?.unsubscribe()
        statisticsSubscription = null
        statisticsInstalled = false
        statisticsGeneration = Long.MIN_VALUE
    }

    private fun installStatistics() {
        val current = statisticsSubscription
        if (statisticsInstalled
            && current?.isActive == true
            && HookRegistry.get().isInstallationGenerationActive(statisticsGeneration)
        ) return
        current?.unsubscribe()
        statisticsSubscription = null
        statisticsInstalled = false
        val subscription = runtime?.installStatistics() ?: return
        trackSubscription(subscription)
        statisticsSubscription = subscription
        statisticsGeneration = HookRegistry.get().installationGeneration()
        statisticsInstalled = subscription.isActive
            && HookRegistry.get().isInstallationGenerationActive(statisticsGeneration)
    }

    companion object {
        const val ID = "input_hint"
    }
}

private class InputHintRuntime(
    context: Context,
    private val classLoader: ClassLoader,
    private val logger: (String, Throwable?) -> Unit
) {
    private val appContext = context.applicationContext ?: context
    private val prefs = InputHintSettings.preferences(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val legacyInputOverrideLogged = AtomicBoolean(false)
    private val inputBindings = Collections.synchronizedMap(
        WeakHashMap<Any, InputHintBinding>()
    )
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == InputHintSettings.KEY_ENABLE ||
            key == InputHintSettings.KEY_TEMPLATE ||
            key == InputHintSettings.KEY_STATISTICS_ENABLE
        ) {
            refreshAttachedInputs()
        }
    }

    @Volatile
    private var active = true

    @Volatile
    private var inputHookInstalled = false

    @Volatile
    private var legacyInputConfigHookInstalled = false

    @Volatile
    private var legacyInputHookUnavailable = false

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @Synchronized
    fun installInputHook(): Boolean {
        val footerClass = KavaReflector.loadClass(CHAT_FOOTER_CLASS, classLoader) ?: run {
            logger("未找到微信聊天输入区类", null)
            return false
        }
        installLegacyInputConfigHook()
        if (inputHookInstalled) return true
        val attachMethod = KavaReflector.declaredMethods(footerClass).singleOrNull { method ->
            method.name == "onAttachedToWindow" &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        } ?: run {
            logger("未找到聊天输入区挂载方法", null)
            return false
        }
        return runCatching {
            HookRegistry.get().hook(attachMethod, XposedInterface.Hooker { chain ->
                val receiver = chain.thisObject
                val value = chain.proceed()
                HookErrorBoundary.run("输入提示绑定失败") after@ {
                    bindFooter(receiver)
                }
                value
            })
            inputHookInstalled = true
            true
        }.getOrElse {
            logger("聊天输入框提示 Hook 安装失败", it)
            false
        }
    }

    /**
     * WeChat 8.0.80 reads RepairerConfigInputBarVoiceInput from several paths, including
     * ChatFooter.c1() and ChattingVoice2TxtComponent initialization. Override the shared
     * config lookup so every consumer observes the legacy value, not just one call site.
     */
    private fun installLegacyInputConfigHook() {
        if (legacyInputConfigHookInstalled || legacyInputHookUnavailable) return
        val configBaseClass = KavaReflector.loadClass(WECHAT_CONFIG_BASE_CLASS, classLoader) ?: run {
            legacyInputHookUnavailable = true
            logger("未找到微信实验配置基类", null)
            return
        }
        val configManagerClass = KavaReflector.loadClass(WECHAT_CONFIG_MANAGER_CLASS, classLoader) ?: run {
            legacyInputHookUnavailable = true
            logger("未找到微信实验配置管理器", null)
            return
        }
        val method = KavaReflector.declaredMethods(configManagerClass).singleOrNull { candidate ->
            candidate.name == RESTORE_LEGACY_INPUT_CONFIG_METHOD &&
                candidate.returnType == Int::class.javaPrimitiveType &&
                candidate.parameterTypes.contentEquals(arrayOf(configBaseClass))
        } ?: run {
            legacyInputHookUnavailable = true
            logger("当前微信版本未找到输入栏语音配置读取方法", null)
            return
        }
        runCatching {
            val restoreLegacyInput = prefs.getBoolean(
                InputHintSettings.KEY_RESTORE_LEGACY_INPUT,
                InputHintSettings.DEFAULT_RESTORE_LEGACY_INPUT
            )
            HookRegistry.get().hookOnce(
                "input-hint:restore-legacy-input-config",
                method,
                XposedInterface.Hooker { chain ->
                    val config = chain.getArg(0)
                    if (active &&
                        prefs.getBoolean(
                            InputHintSettings.KEY_RESTORE_LEGACY_INPUT,
                            InputHintSettings.DEFAULT_RESTORE_LEGACY_INPUT
                        ) &&
                        config?.javaClass?.name == RESTORE_LEGACY_INPUT_CONFIG_CLASS
                    ) {
                        if (legacyInputOverrideLogged.compareAndSet(false, true)) {
                            logger("微信输入栏长按语音灰度配置已强制关闭", null)
                        }
                        0
                    } else {
                        chain.proceed()
                    }
                }
            )
            legacyInputConfigHookInstalled = true
            logger("旧版输入框兼容配置 Hook 已安装，当前设置=$restoreLegacyInput", null)
        }.onFailure {
            logger("旧版输入框兼容配置 Hook 安装失败", it)
        }
    }

    fun installStatistics(): OutgoingMessageStatsRepository.Subscription? {
        if (!OutgoingMessageStatsRepository.install(logger)) {
            logger("消息变更 API 未就绪，发送统计暂不可用", null)
            return null
        }
        return OutgoingMessageStatsRepository.subscribe(::refreshAttachedInputs)
    }

    fun destroy() {
        active = false
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        val inputs = synchronized(inputBindings) {
            inputBindings.entries.map { it.key to it.value }.also { inputBindings.clear() }
        }
        inputs.forEach { (input, binding) ->
            postToInput(input) {
                if (binding.appliedHint != null && TextUtils.equals(readHint(input), binding.appliedHint)) {
                    writeHint(input, binding.originalHint)
                }
            }
        }
    }

    private fun bindFooter(footer: Any?) {
        if (!active || footer == null) return
        val input = NativeSendButtonHook.findInputView(footer)
        if (input != null) {
            bindInput(input)
            postToInput(input) { bindInput(input) }
            return
        }
        (footer as? View)?.post {
            NativeSendButtonHook.findInputView(footer)?.let(::bindInput)
        }
    }

    private fun bindInput(input: Any) {
        NativeSendButtonHook.trackInputView(input)
        if (!supportsHint(input)) return
        synchronized(inputBindings) {
            if (!inputBindings.containsKey(input)) {
                inputBindings[input] = InputHintBinding(readHint(input))
            }
        }
        applyHint(input)
    }

    private fun refreshAttachedInputs() {
        val inputs = synchronized(inputBindings) { inputBindings.keys.toList() }
        mainHandler.post {
            inputs.forEach { input ->
                if ((input as? View)?.isAttachedToWindow != false) applyHint(input)
            }
        }
    }

    private fun applyHint(input: Any) {
        if (!active) return
        val binding = synchronized(inputBindings) { inputBindings[input] } ?: return
        if (!prefs.getBoolean(InputHintSettings.KEY_ENABLE, InputHintSettings.DEFAULT_ENABLE)) {
            if (binding.appliedHint != null && TextUtils.equals(readHint(input), binding.appliedHint)) {
                writeHint(input, binding.originalHint)
            }
            binding.appliedHint = null
            return
        }
        val statisticsEnabled = prefs.getBoolean(
            InputHintSettings.KEY_STATISTICS_ENABLE,
            InputHintSettings.DEFAULT_STATISTICS_ENABLE
        )
        val stats = if (statisticsEnabled) {
            OutgoingMessageStatsRepository.current()
        } else {
            InputHintStats()
        }
        val renderedHint = InputHintSettings.renderTemplate(InputHintSettings.template(prefs), stats)
        val currentHint = readHint(input)
        if (binding.appliedHint == null || !TextUtils.equals(currentHint, binding.appliedHint)) {
            binding.originalHint = currentHint
        }
        binding.appliedHint = renderedHint
        writeHint(input, renderedHint)
    }

    private fun supportsHint(input: Any): Boolean {
        return input is TextView || KavaReflector.findMethodRecursive(
            input.javaClass,
            "setHint",
            CharSequence::class.java
        ) != null
    }

    private fun readHint(input: Any): CharSequence? {
        if (input is TextView) return input.hint
        return KavaReflector.invoke(
            KavaReflector.findMethodRecursive(input.javaClass, "getHint"),
            input
        ) as? CharSequence
    }

    private fun writeHint(input: Any, hint: CharSequence?) {
        if (input is TextView) {
            input.hint = hint
            return
        }
        KavaReflector.invokeSuccessfully(
            KavaReflector.findMethodRecursive(
                input.javaClass,
                "setHint",
                CharSequence::class.java
            ),
            input,
            hint
        )
    }

    private fun postToInput(input: Any, block: () -> Unit) {
        val view = input as? View
        if (view != null && view.isAttachedToWindow) {
            view.post { block() }
        } else {
            mainHandler.post { block() }
        }
    }

    companion object {
        private const val CHAT_FOOTER_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"
        private const val WECHAT_CONFIG_MANAGER_CLASS = "tc2.e"
        private const val WECHAT_CONFIG_BASE_CLASS = "ee5.e"
        private const val RESTORE_LEGACY_INPUT_CONFIG_CLASS =
            "com.tencent.mm.repairer.config.chatting.RepairerConfigInputBarVoiceInput"
        private const val RESTORE_LEGACY_INPUT_CONFIG_METHOD = "c"
    }

    private data class InputHintBinding(
        var originalHint: CharSequence?,
        var appliedHint: CharSequence? = null
    )
}
