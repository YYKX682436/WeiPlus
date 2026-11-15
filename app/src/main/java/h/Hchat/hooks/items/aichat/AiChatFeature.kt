package h.Hchat.hooks.items.aichat

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.model.WeChatMessageTypes
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class AiChatFeature : BaseFeature() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var observer: WeChatMessageObserveApi.Subscription? = null
    private var prefs: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var alive = false
    private val poll = object : Runnable {
        override fun run() {
            if (!alive) return
            context()?.let { installObserver(it); AiChatRuntime.scheduleMemory(it.hostContext()) }
            handler.postDelayed(this, 30_000L)
        }
    }
    override fun featureId() = ID
    override fun name() = "AI 聊天"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(ID, "AI 聊天", "独立管理聊天对象、模型和记忆", FeatureSettingsProvider.CATEGORY_PRACTICAL) {})
    }
    override fun onFeatureInstall(context: FeatureContext) {
        alive = true
        prefs = AiChatSettings(context.hostContext()).prefs
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AiChatSettings.KEY_ENABLE) handler.post { installObserver(context) }
            if (key?.startsWith("targets:") == true) AiChatRuntime.scheduleMemory(context.hostContext())
        }.also { prefs?.registerOnSharedPreferenceChangeListener(it) }
        subscribe(Events.MessageReceived::class.java) { event ->
            if (alive && observer == null && prefs?.getBoolean(AiChatSettings.KEY_ENABLE, false) == true) {
                AiChatRuntime.onMessage(context.hostContext(), fallbackMessage(event))
            }
        }
        subscribe(Events.DexReady::class.java) { installObserver(context) }
        DexInstallScheduler.schedule("ai_chat_send_xml", "AI 引用小尾巴", stage = DexInstallScheduler.Stage.WARMUP) {
            if (!alive) true else {
                val api = WeChatApis.messages()
                if (api?.canSendXml() == false) context.dexFinder().resolveSendXmlApi()
                api?.canSendXml() == true
            }
        }
        AiChatRuntime.start(context.hostContext())
        handler.post(poll)
    }
    private fun installObserver(context: FeatureContext) {
        if (!alive || observer != null || prefs?.getBoolean(AiChatSettings.KEY_ENABLE, false) != true) return
        val api = runCatching { WeChatApis.message().observe() }.getOrNull() ?: return
        if (!api.isAvailable) return
        observer = api.subscribe { AiChatRuntime.onMessage(context.hostContext(), it) }
    }
    private fun fallbackMessage(event: Events.MessageReceived): WeChatMessageObserveApi.ObservedMessage {
        val content = event.content.orEmpty().ifBlank { event.xml.orEmpty() }
        val type = event.msgType?.toIntOrNull() ?: WeChatMessage.inferType(content)
        val outgoing = event.outgoing || (!event.selfWxId.isNullOrBlank() && event.sender.equals(event.selfWxId, true))
        val message = WeChatMessage.fromTransient(event.talker, event.sender, content,
            event.createTimeSeconds.takeIf { it > 0 }?.times(1000) ?: System.currentTimeMillis(),
            outgoing, type, event.msgSvrId, event.msgSource, event.selfWxId)
        val kind = when (WeChatMessageTypes.normalize(type)) {
            WeChatMessageTypes.TEXT -> WeChatMessageObserveApi.Kind.TEXT
            WeChatMessageTypes.IMAGE -> WeChatMessageObserveApi.Kind.IMAGE
            WeChatMessageTypes.VOICE -> WeChatMessageObserveApi.Kind.VOICE
            WeChatMessageTypes.VIDEO -> WeChatMessageObserveApi.Kind.VIDEO
            WeChatMessageTypes.APP -> WeChatMessageObserveApi.Kind.APP
            else -> WeChatMessageObserveApi.Kind.UNKNOWN
        }
        return WeChatMessageObserveApi.ObservedMessage.transientMessage(event.source.orEmpty().ifBlank { "add_msg" },
            kind, event.talker.orEmpty(), event.sender.orEmpty(), content, event.xml.orEmpty(), "",
            WeChatMessage.isGroupTalker(event.talker), outgoing, message)
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        alive = false
        DexInstallScheduler.cancel("ai_chat_send_xml")
        handler.removeCallbacks(poll)
        observer?.unsubscribe(); observer = null
        listener?.let { prefs?.unregisterOnSharedPreferenceChangeListener(it) }
        listener = null; prefs = null
        AiChatRuntime.stop()
    }
    companion object { const val ID = "ai_chat" }
}
