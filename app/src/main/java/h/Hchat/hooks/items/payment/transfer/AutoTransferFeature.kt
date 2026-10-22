package h.Hchat.hooks.items.payment.transfer

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.content.SharedPreferences
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.payment.TransferOperationParams
import h.Hchat.hooks.api.payment.TransferQueryParams
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.preferences.HchatStorage
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AutoTransferFeature : BaseFeature() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val handledTransfers = ConcurrentHashMap<String, Long>()
    private val runSession = AutoTransferRunSession()
    private val transferExecutor = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(ACTION_QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "Hchat-AutoTransfer").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    ).apply { allowCoreThreadTimeOut(true) }
    private val actionDispatcher = AutoTransferActionDispatcher(runSession, transferExecutor)
    @Volatile private var observerInstalled = false
    @Volatile private var feedback: TransferSuccessFeedback? = null
    @Volatile private var messageSubscription: WeChatMessageObserveApi.Subscription? = null
    private var settingsPrefs: SharedPreferences? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private data class TransferAttempt(val success: Boolean, val failure: String = "")

    override fun featureId(): String = ID

    override fun name(): String = "自动收款"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoTransferSettingsProvider())
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        settingsPrefs = HchatStorage.preferences(context.hostContext(), AutoTransferSettings.PREFS_NAME)
        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != AutoTransferSettings.KEY_ENABLE) return@OnSharedPreferenceChangeListener
            if (transferEnabled(context)) scheduleInstall(context, runSession.activate()) else stopFeatureWork()
        }.also { settingsPrefs?.registerOnSharedPreferenceChangeListener(it) }
        if (transferEnabled(context)) scheduleInstall(context, runSession.activate())
        subscribe(Events.DexReady::class.java) {
            if (transferEnabled(context)) scheduleInstall(context, runSession.activate())
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        settingsListener?.let { settingsPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        settingsListener = null
        settingsPrefs = null
        stopFeatureWork()
    }

    private fun scheduleInstall(context: FeatureContext, session: Long) {
        if (!runSession.isCurrent(session) || !transferEnabled(context)) return
        DexInstallScheduler.schedule(ID, name(), stage = DexInstallScheduler.Stage.WARMUP) {
            try {
                if (!runSession.isCurrent(session) || !transferEnabled(context)) return@schedule true
                TransferReceiveAccountStore.install(context, ::logError)
                if (!runSession.isCurrent(session) || !transferEnabled(context)) return@schedule true
                if (feedback == null) {
                    feedback = TransferSuccessFeedback(context.hostContext(), ::logError)
                }
                if (observerInstalled) return@schedule true
                val observer = WeChatApis.message().observe()
                if (observer == null || !observer.isAvailable) {
                    logError("消息观察不可用，自动收款未安装", null)
                    return@schedule false
                } else {
                    val subscription = observer.subscribe { message ->
                        dispatchTransferAction(session) { handleMessage(context, message, session) }
                    }
                    if (!runSession.isCurrent(session) || !transferEnabled(context)) {
                        subscription?.unsubscribe()
                        return@schedule true
                    }
                    messageSubscription?.unsubscribe()
                    messageSubscription = subscription
                    observerInstalled = true
                    if (!transferEnabled(context)) stopFeatureWork()
                    return@schedule true
                }
            } catch (e: Throwable) {
                logError("自动收款安装失败", e)
                false
            }
        }
    }

    private fun handleMessage(
        context: FeatureContext,
        message: WeChatMessageObserveApi.ObservedMessage?,
        session: Long
    ) {
        if (message == null || !runSession.isCurrent(session) || !transferEnabled(context)) return
        val rawContent = message.content.ifBlank { message.xml }
        if (message.outgoing) {
            return
        }
        val settings = AutoTransferSettings(context.hostContext())
        if (!settings.isEnabled()) {
            return
        }

        val info = TransferMessageParser.parse(rawContent, message.sender)
        if (info == null) {
            return
        }
        if (!info.pending) {
            return
        }

        val selfWxId = WeChatApis.contact().account()?.selfWxId().orEmpty()
        if (info.receiverUsername.isNotBlank() && selfWxId.isNotBlank() && info.receiverUsername != selfWxId) {
            return
        }
        if (selfWxId.isNotBlank() && info.payerUsername == selfWxId) {
            return
        }

        val rule = TransferRuleResolver(settings).resolve(
            message.talker,
            info.payerUsername.ifBlank { message.sender }
        )
        if (!runSession.isCurrent(session)) return
        if (!rule.enabled || !markTransferOnce(info)) return
        val rejectReason = rejectReason(rule, info, message)
        val delay = rule.nextDelayMillis()
        val action = Runnable {
            dispatchTransferAction(session) {
                if (!settings.isEnabled()) return@dispatchTransferAction
                if (rejectReason == null) {
                    receiveTransfer(settings, rule, message, info, session)
                } else if (rule.refundRejected) {
                    refundTransfer(settings, message, info, rejectReason, session)
                }
            }
        }
        if (delay > 0L) {
            mainHandler.postDelayed(action, delay)
        } else {
            action.run()
        }
    }

    private fun transferEnabled(context: FeatureContext): Boolean =
        AutoTransferSettings(context.hostContext()).isEnabled()

    private fun stopFeatureWork() {
        runSession.invalidate()
        DexInstallScheduler.cancel(ID)
        messageSubscription?.unsubscribe()
        messageSubscription = null
        observerInstalled = false
        mainHandler.removeCallbacksAndMessages(null)
        transferExecutor.queue.clear()
        feedback?.shutdown()
        feedback = null
    }

    private fun dispatchTransferAction(session: Long, action: () -> Unit) {
        if (!actionDispatcher.dispatch(session, action) && runSession.isCurrent(session)) {
            logError("自动收款任务队列已满，跳过本次转账动作", null)
        }
    }

    private fun rejectReason(
        rule: TransferEffectiveRule,
        info: TransferMessageInfo,
        message: WeChatMessageObserveApi.ObservedMessage
    ): String? {
        val payer = info.payerUsername.ifBlank { message.sender }
        val talker = message.talker
        val group = message.group || talker.endsWith("@chatroom")
        if (rule.isInQuietTime()) return "当前处于禁收时段"
        when (rule.listMode) {
            1 -> {
                val allowed = idInList(payer, rule.whitelist) || (group && idInList(talker, rule.whitelist))
                if (!allowed) return "非白名单"
            }
            2 -> {
                val blocked = idInList(payer, rule.blacklist) || (group && idInList(talker, rule.blacklist))
                if (blocked) return "黑名单"
            }
        }

        if (rule.amountEnabled) {
            val limit = rule.amountValue.toDoubleOrNull() ?: 0.0
            val matched = when (rule.amountCondition) {
                0 -> info.amount > limit
                2 -> kotlin.math.abs(info.amount - limit) < 0.01
                else -> info.amount < limit
            }
            val action = rule.amountAction
            if (action == 0 && matched) return "金额规则"
            if (action == 1 && !matched) return "金额规则"
        }

        val keywordMode = rule.keywordMode
        val hasKeyword = TransferMessageParser.containsKeywords(info.rawXml, rule.keywords)
        if (keywordMode == 1 && !hasKeyword) return "缺少关键词"
        if (keywordMode == 2 && hasKeyword) return "关键词规则"
        return null
    }

    private fun receiveTransfer(
        settings: AutoTransferSettings,
        rule: TransferEffectiveRule,
        message: WeChatMessageObserveApi.ObservedMessage,
        info: TransferMessageInfo,
        session: Long
    ) {
        if (!runSession.isCurrent(session) || !settings.isEnabled()) return
        val selectedKey = rule.receiveAccount
        if (selectedKey == TransferReceiveAccountStore.DEFAULT_KEY) {
            finishReceive(settings, rule, message, info, null, session)
            return
        }
        val cachedAccount = TransferReceiveAccountStore.find(settings.hostContext(), selectedKey)
        if (cachedAccount != null) {
            finishReceive(settings, rule, message, info, cachedAccount, session)
            return
        }
        val api = WeChatApis.payment().transfers()
        if (api == null || !api.canQuery()) {
            finishReceive(settings, rule, message, info, null, session)
            return
        }
        var started = false
        runSession.runIfCurrent(session) {
            if (!settings.isEnabled()) return@runIfCurrent
            started = api.query(
                TransferQueryParams(
                    transactionId = info.transactionId,
                    transId = info.transId,
                    invalidTime = info.invalidTime,
                    groupUsername = if (message.talker.endsWith("@chatroom")) message.talker else "",
                    transferAttach = info.transferAttach
                ), callback = { result ->
                    dispatchTransferAction(session) {
                        if (!settings.isEnabled()) return@dispatchTransferAction
                        val accounts = if (result != null && result.errorCode == 0) {
                            TransferReceiveAccountStore.captureResponse(settings.hostContext(), result.response)
                        } else {
                            emptyList()
                        }
                        val matched = TransferReceiveAccountStore.find(accounts, selectedKey)
                        finishReceive(settings, rule, message, info, matched, session)
                    }
                }
            )
        }
        if (!started && runSession.isCurrent(session) && settings.isEnabled()) {
            finishReceive(settings, rule, message, info, null, session)
        }
    }

    private fun finishReceive(
        settings: AutoTransferSettings,
        rule: TransferEffectiveRule,
        message: WeChatMessageObserveApi.ObservedMessage,
        info: TransferMessageInfo,
        account: TransferReceiveAccount?,
        session: Long
    ) {
        runSession.runIfCurrent(session) {
            if (!settings.isEnabled()) return@runIfCurrent
            val attempt = operateTransfer(message, info, "confirm", account)
            if (!attempt.success) {
                logError(
                    if (attempt.failure.isBlank()) "自动收款失败" else "自动收款失败: ${attempt.failure}",
                    null
                )
                return@runIfCurrent
            }
            feedback?.onReceived(transferKey(info), message, info, rule)
        }
    }

    private fun refundTransfer(
        settings: AutoTransferSettings,
        message: WeChatMessageObserveApi.ObservedMessage,
        info: TransferMessageInfo,
        reason: String,
        session: Long
    ) {
        runSession.runIfCurrent(session) {
            if (!settings.isEnabled()) return@runIfCurrent
            val attempt = operateTransfer(message, info, "refuse", null)
            if (!attempt.success) {
                logError(
                    buildString {
                        append("自动退回失败: ")
                        append(reason)
                        if (attempt.failure.isNotBlank()) append("; ").append(attempt.failure)
                    },
                    null
                )
            }
        }
    }

    private fun operateTransfer(
        message: WeChatMessageObserveApi.ObservedMessage,
        info: TransferMessageInfo,
        op: String,
        account: TransferReceiveAccount?
    ): TransferAttempt {
        if (TextUtils.isEmpty(info.transactionId) || TextUtils.isEmpty(info.transId)) {
            return TransferAttempt(false, "转账单号缺失")
        }
        val api = WeChatApis.payment().transfers()
        if (api == null || !api.canOperate()) {
            return TransferAttempt(
                false,
                "转账 API 未就绪: api=${api != null} canOperate=${api?.canOperate()}"
            )
        }
        val params = TransferOperationParams(
            transactionId = info.transactionId,
            transId = info.transId,
            totalFee = info.totalFee,
            username = info.payerUsername,
            invalidTime = info.invalidTime,
            groupUsername = message.talker.takeIf {
                message.group || it.endsWith("@chatroom")
            }.orEmpty(),
            recvAccountType = account?.accountType ?: 0,
            bindSerial = account?.bindSerial.orEmpty(),
            subRecvChannelId = account?.subChannelId ?: 0L,
            transferAttach = info.transferAttach
        )
        val success = if (op == "refuse") api.refund(params) else api.receive(params)
        return TransferAttempt(success, if (success) "" else api.lastFailureReason())
    }

    private fun markTransferOnce(info: TransferMessageInfo): Boolean {
        val key = transferKey(info)
        if (key == "::") return true
        cleanupHandledTransfers()
        return handledTransfers.putIfAbsent(key, System.currentTimeMillis()) == null
    }

    private fun cleanupHandledTransfers() {
        if (handledTransfers.size < 64) return
        val deadline = System.currentTimeMillis() - HANDLED_TRANSFER_TTL_MS
        handledTransfers.entries.removeIf { it.value < deadline }
    }

    private fun transferKey(info: TransferMessageInfo): String = buildString {
        append(info.transactionId)
        append(':')
        append(info.transId)
        append(':')
        append(info.payerUsername)
    }

    private fun idInList(id: String?, value: String): Boolean {
        if (id.isNullOrBlank()) return false
        return value.split("|", ",", "，", "\n", "\r")
            .any { it.trim() == id }
    }

    companion object {
        const val ID = "auto_transfer"
        private const val HANDLED_TRANSFER_TTL_MS = 60 * 60 * 1000L
        private const val ACTION_QUEUE_CAPACITY = 64
    }
}
