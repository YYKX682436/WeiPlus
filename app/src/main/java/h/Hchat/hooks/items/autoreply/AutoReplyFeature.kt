package h.Hchat.hooks.items.autoreply

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
import h.Hchat.utils.HLog

class AutoReplyFeature : BaseFeature() {
    private val observationState = AutoReplyObservationState()
    private val messageDeduplicator = AutoReplyMessageDeduplicator()
    private val retryBudget = AutoReplyObservationRetryBudget(RETRY_DELAYS_MS.size)
    private val retryHandler = Handler(Looper.getMainLooper())
    private val observationLock = Any()
    private var fallbackSubscription: h.Hchat.event.EventBus.Subscription? = null
    private var retryPending = false
    private var settings: AutoReplySettings? = null
    private var settingsPrefs: SharedPreferences? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var autoReplyEnabled = false
    @Volatile private var autoAcceptEnabled = false
    private var friendRequestSubscription: AutoReplyFriendRequestHook.NativeSubscription? = null
    private val retryObserver = Runnable {
        synchronized(observationLock) { retryPending = false }
        context()?.let(::tryInstallMessageObserver)
    }

    override fun featureId(): String = ID

    override fun name(): String = "自动回复"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoReplySettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        observationState.startFallback()
        retryBudget.reset()
        messageDeduplicator.clear()
        settings = AutoReplySettings(context.hostContext())
        settingsPrefs = settings?.getHostPreferences()
        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == AutoReplySettings.KEY_ENABLE || key == AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE) {
                refreshSettings(context)
                if (autoReplyEnabled || autoAcceptEnabled) tryInstallMessageObserver(context)
            }
        }.also { settingsPrefs?.registerOnSharedPreferenceChangeListener(it) }
        refreshSettings(context)
        fallbackSubscription = subscribe(Events.MessageReceived::class.java, ::handleFallbackMessage)
        subscribe(Events.DexReady::class.java) {
            if (autoAcceptEnabled) scheduleFriendRequestInstall()
            tryInstallMessageObserver(context)
        }
        tryInstallMessageObserver(context)
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        synchronized(observationLock) {
            DexInstallScheduler.cancel(FRIEND_REQUEST_INSTALL_KEY)
            retryHandler.removeCallbacks(retryObserver)
            retryPending = false
            observationState.stop()
            fallbackSubscription?.let(context.eventBus()::unsubscribe)
            fallbackSubscription = null
            messageDeduplicator.clear()
            settingsListener?.let { settingsPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
            settingsListener = null
            settingsPrefs = null
            settings = null
            autoReplyEnabled = false
            autoAcceptEnabled = false
            friendRequestSubscription?.unsubscribe()
            friendRequestSubscription = null
        }
        AutoReplyFriendRequestHook.uninstall()
    }

    private fun refreshSettings(context: FeatureContext) {
        synchronized(observationLock) {
            val currentSettings = settings ?: return
            autoReplyEnabled = currentSettings.isEnabled()
            val nextAutoAcceptEnabled = currentSettings.getBoolean(AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE, false)
            if (nextAutoAcceptEnabled == autoAcceptEnabled) return
            autoAcceptEnabled = nextAutoAcceptEnabled
            if (nextAutoAcceptEnabled) {
                val subscription = AutoReplyFriendRequestHook.subscribe { event ->
                    AutoReplyRuntime.handleNewFriend(context.hostContext(), event.wxid, event.ticket, event.scene)
                }
                friendRequestSubscription = subscription
                scheduleFriendRequestInstall()
            } else {
                friendRequestSubscription?.unsubscribe()
                friendRequestSubscription = null
                DexInstallScheduler.cancel(FRIEND_REQUEST_INSTALL_KEY)
                AutoReplyFriendRequestHook.stopDatabaseObserver()
            }
        }
    }

    private fun tryInstallMessageObserver(context: FeatureContext) {
        synchronized(observationLock) {
            if (!observationState.acceptsFallback()) return
            refreshSettings(context)
            if (!autoReplyEnabled && !autoAcceptEnabled) return
            if (autoAcceptEnabled) scheduleFriendRequestInstall()
            val observer = runCatching { WeChatApis.message().observe() }.getOrNull()
            val subscription = runCatching {
                observer?.takeIf { it.isAvailable }?.subscribe { message -> handleObservedMessage(message) }
            }.onFailure { HLog.e("$TAG 消息观察订阅失败: ${it.message}", it) }.getOrNull()
            if (subscription != null && observationState.activateObserver()) {
                trackSubscription(subscription)
                fallbackSubscription?.let(context.eventBus()::unsubscribe)
                fallbackSubscription = null
                retryHandler.removeCallbacks(retryObserver)
                retryPending = false
                return
            }
            subscription?.unsubscribe()
            if (observer == null || !observer.isAvailable) HLog.e("$TAG 消息观察 API 未就绪，暂用消息事件")
            scheduleObserverRetryLocked()
        }
    }

    private fun scheduleObserverRetryLocked() {
        if (retryPending) return
        val retryIndex = retryBudget.reserve() ?: return
        retryPending = true
        retryHandler.postDelayed(retryObserver, RETRY_DELAYS_MS[retryIndex])
    }

    private fun scheduleFriendRequestInstall() {
        DexInstallScheduler.schedule(
            FRIEND_REQUEST_INSTALL_KEY,
            "自动通过好友申请",
            stage = DexInstallScheduler.Stage.WARMUP
        ) {
            if (!autoAcceptEnabled) true else AutoReplyFriendRequestHook.install()
        }
    }

    private fun handleFallbackMessage(event: Events.MessageReceived) {
        if (!observationState.acceptsFallback() || (!autoReplyEnabled && !autoAcceptEnabled)) return
        val message = observedMessage(event)
        if (!messageDeduplicator.claim(messageKey(message))) return
        dispatchMessage(message)
    }

    private fun handleObservedMessage(message: WeChatMessageObserveApi.ObservedMessage) {
        if (!observationState.acceptsObserved() || (!autoReplyEnabled && !autoAcceptEnabled)) return
        if (!messageDeduplicator.claim(messageKey(message))) return
        dispatchMessage(message)
    }

    private fun dispatchMessage(message: WeChatMessageObserveApi.ObservedMessage) {
        val hostContext = context()?.hostContext() ?: return
        if (autoReplyEnabled) AutoReplyRuntime.handleMessage(hostContext, message)
        if (autoAcceptEnabled) AutoReplyFriendRequestHook.handleMessage(message)
    }

    private fun observedMessage(event: Events.MessageReceived): WeChatMessageObserveApi.ObservedMessage {
        val content = event.content.orEmpty()
        val type = event.msgType?.toIntOrNull() ?: WeChatMessage.inferType(content)
        val createTime = event.createTimeSeconds.takeIf { it > 0L }?.times(1000L)
            ?: System.currentTimeMillis()
        val outgoing = event.outgoing ||
            (!event.selfWxId.isNullOrBlank() && event.sender.equals(event.selfWxId, ignoreCase = true))
        val message = WeChatMessage.fromTransient(
            event.talker,
            event.sender,
            content,
            createTime,
            outgoing,
            type,
            event.msgSvrId,
            event.msgSource,
            event.selfWxId
        )
        val kind = when (WeChatMessageTypes.normalize(type)) {
            WeChatMessageTypes.TEXT -> WeChatMessageObserveApi.Kind.TEXT
            WeChatMessageTypes.IMAGE -> WeChatMessageObserveApi.Kind.IMAGE
            WeChatMessageTypes.VOICE -> WeChatMessageObserveApi.Kind.VOICE
            WeChatMessageTypes.VIDEO -> WeChatMessageObserveApi.Kind.VIDEO
            WeChatMessageTypes.EMOJI -> WeChatMessageObserveApi.Kind.EMOJI
            WeChatMessageTypes.LOCATION -> WeChatMessageObserveApi.Kind.LOCATION
            WeChatMessageTypes.APP -> WeChatMessageObserveApi.Kind.APP
            WeChatMessageTypes.SYSTEM, WeChatMessageTypes.RECALLED -> WeChatMessageObserveApi.Kind.SYSTEM
            else -> WeChatMessageObserveApi.Kind.UNKNOWN
        }
        val nativeUrl = runCatching {
            WeChatApis.messageParser().getXmlParamByTag(event.xml.orEmpty(), "nativeurl")
        }.getOrNull().orEmpty()
        return WeChatMessageObserveApi.ObservedMessage.transientMessage(
            event.source?.takeIf { it.isNotBlank() } ?: "add_msg",
            kind,
            event.talker.orEmpty(),
            event.sender.orEmpty(),
            content,
            event.xml.orEmpty(),
            nativeUrl,
            WeChatMessage.isGroupTalker(event.talker),
            outgoing,
            message
        )
    }

    private fun messageKey(message: WeChatMessageObserveApi.ObservedMessage): String? {
        val native = message.message ?: return null
        if (native.msgSvrId > 0L) return "svr:${native.msgSvrId}"
        if (native.msgId > 0L) return "local:${native.msgId}"
        var createTime = native.createTime
        if (createTime in 1L until 100_000_000_000L) createTime *= 1000L
        if (createTime <= 0L) return null
        return "fallback:${message.talker}:${message.sender}:${native.type}:$createTime:${native.content.hashCode()}"
    }

    companion object {
        const val ID = "auto_reply"
        private const val TAG = "[Hchat:AutoReply]"
        private const val FRIEND_REQUEST_INSTALL_KEY = "auto_reply_friend_request"
        private val RETRY_DELAYS_MS = longArrayOf(250L, 500L, 1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
