package h.Hchat.hooks.items.messageblock

import android.content.Context
import io.github.libxposed.api.XposedInterface
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.model.WeChatMessageTypes
import h.Hchat.hooks.api.model.WeChatParsedMessage
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationRuntime
import h.Hchat.utils.HLog
import io.github.libxposed.api.XposedInterface.HookHandle
import java.util.Locale

class MessageBlockFeature : BaseFeature() {
    @Volatile private var hookHandle: HookHandle? = null
    @Volatile private var voipIncomingHooker: MessageBlockVoipIncomingCallHooker? = null

    override fun featureId(): String = ID

    override fun name(): String = "屏蔽消息"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MessageBlockSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        DexInstallScheduler.schedule(ID, name(), stage = DexInstallScheduler.Stage.WARMUP) {
            installBlockHook(context)
        }
        val incomingHooker = MessageBlockVoipIncomingCallHooker(context) { caller, type, selfWxId, verifiedIncoming ->
            evaluateIncomingVoip(context.hostContext(), caller, type, selfWxId, verifiedIncoming)
        }
        voipIncomingHooker = incomingHooker
        DexInstallScheduler.schedule(VOIP_HOOK_KEY, name(), stage = DexInstallScheduler.Stage.WARMUP) {
            incomingHooker.install()
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        voipIncomingHooker = null
    }

    private fun installBlockHook(context: FeatureContext): Boolean {
        val registry = HookRegistry.get()
        if (registry.isActive(hookHandle)) return true
        val finder = context.dexFinder()
        if (!finder.resolveAddMsgApi()) return false
        val parser = WeChatApis.messageParser() ?: return false
        val method = finder.addMsgPreProcessMethod ?: return false
        val generation = registry.installationGeneration()
        return try {
            hookHandle = registry.hookOnce(
                "message_block.addmsg_preprocess",
                method,
                XposedInterface.Hooker { chain ->
                    if (!registry.isInstallationGenerationActive(generation)) return@Hooker chain.proceed()
                    try {
                        val args = chain.args
                        val addMsg = args.getOrNull(0)
                        val settings = MessageBlockSettings(context.hostContext())
                        val enabled = settings.isEnabled()
                        if (addMsg != null && enabled) {
                            val selfWxId = WeChatApis.account()?.selfWxId().orEmpty()
                            val parsed = parser.parseAddMsg(addMsg, selfWxId)
                            val matched = parsed != null && shouldBlock(settings, parsed)
                            val notificationAllows = matched &&
                                KeywordNotificationRuntime.shouldAllowBlockedMessageIntoDatabase(context.hostContext(), parsed)
                            if (MessageBlockDecision.shouldIntercept(
                                    registry.isInstallationGenerationActive(generation),
                                    enabled,
                                    parsed != null,
                                    matched,
                                    notificationAllows
                                )) {
                                postBlockedMessage(context, parsed ?: return@Hooker chain.proceed())
                                return@Hooker null
                            }
                        }
                    } catch (error: Throwable) {
                        logError("AddMsg 前置判定异常，继续原消息链", error)
                    }
                    chain.proceed()
                },
                XposedInterface.PRIORITY_HIGHEST
            )
            registry.isActive(hookHandle).also { active ->
                HLog.i("[Hchat:${featureId()}] AddMsg 前置屏蔽 Hook=" + if (active) "installed" else "inactive")
            }
        } catch (error: Throwable) {
            hookHandle = null
            logError("AddMsg 前置屏蔽 Hook 安装失败", error)
            false
        }
    }

    private fun postBlockedMessage(context: FeatureContext, parsed: WeChatParsedMessage) {
        runCatching {
            context.eventBus().post(
                Events.MessageBlocked(
                    parsed.xml,
                    parsed.sender,
                    parsed.talker,
                    parsed.content,
                    parsed.type.toString(),
                    parsed.createTimeSeconds,
                    parsed.msgSvrId,
                    parsed.msgSource,
                    parsed.selfWxId,
                    parsed.nativeUrl
                )
            )
        }.onFailure {
            logError("屏蔽消息派发内部事件失败", it)
        }
    }

    private fun shouldBlock(settings: MessageBlockSettings, parsed: WeChatParsedMessage): Boolean {
        val talker = parsed.talker
        val sender = parsed.sender
        if (talker.isBlank() || sender.isBlank()) return false
        if (parsed.selfWxId.isNotBlank() && sender == parsed.selfWxId) return false

        val message = buildMessage(parsed)
        val templates = settings.templates().filter { it.enabled }
        val templatesById = templates.associateBy { it.id }
        val bindings = settings.bindings()
        if (bindings.isNotEmpty()) {
            val matched = bindings.filter { settings.bindingMatches(it, parsed.talker, parsed.sender) }
            if (matched.isNotEmpty()) {
                if (matched.any { it.quickBlockAll }) return true
                val activeMatched = matched.filter { it.enabled }
                if (activeMatched.isEmpty()) return false
                if (activeMatched.any(::bindingOverridesDefault)) {
                    if (activeMatched.any {
                            it.action == MessageBlockSettings.ACTION_EXCLUDE &&
                                bindingScopeActive(it, templatesById)
                        }) return false
                    val blockBindings = activeMatched.filter { it.action == MessageBlockSettings.ACTION_BLOCK }
                    if (blockBindings.any {
                            it.customRules &&
                                shouldBlockBindingType(settings, it, parsed, message)
                        }) return true
                    val boundTemplates = blockBindings
                        .filter { !it.customRules }
                        .flatMap { it.templateIds }
                        .mapNotNull { templatesById[it] }
                        .distinctBy { it.id }
                    return boundTemplates.any { shouldBlockTemplateType(settings, it, parsed, message) }
                }
                // Empty historical/list-picker rows have no rule of their own and follow the default rule.
            }
        }
        val defaultRule = when {
            message.isOfficialAccount() -> settings.defaultOfficialRule()
            message.isGroupChat() -> settings.defaultGroupRule()
            else -> settings.defaultPrivateRule()
        }
        if (defaultRule.enabled) {
            return shouldBlockDefaultRule(settings, defaultRule, templatesById, parsed, message)
        }
        if (templates.isEmpty()) return false
        if (templates.any { templateExcludes(settings, it, parsed) }) return false
        return templates.any { shouldApplyLegacyTemplate(settings, it, parsed, message) }
    }

    internal fun evaluateIncomingVoip(
        context: Context,
        caller: String,
        messageType: Int,
        selfWxId: String,
        allowMissingSelfIdentity: Boolean = false
    ): MessageBlockIncomingVoipEvaluation {
        val settings = MessageBlockSettings(context)
        val enabled = settings.isEnabled()
        if (!enabled || messageType !in VOIP_MESSAGE_TYPES) {
            return MessageBlockIncomingVoipEvaluation(enabled, privateCaller = false, ruleMatched = false)
        }

        val cleanCaller = caller.trim()
        val cleanSelf = selfWxId.trim()
        val message = WeChatMessage.fromTransient(
            cleanCaller,
            cleanCaller,
            "",
            System.currentTimeMillis(),
            false,
            messageType,
            0L,
            "",
            cleanSelf
        )
        val callerIdentityTrusted = if (cleanSelf.isNotBlank()) {
            cleanCaller != cleanSelf
        } else {
            allowMissingSelfIdentity
        }
        val privateCaller = cleanCaller.isNotBlank() &&
            callerIdentityTrusted &&
            message.isPrivateChat() &&
            message.isVoip()
        if (!privateCaller) {
            return MessageBlockIncomingVoipEvaluation(enabled, privateCaller = false, ruleMatched = false)
        }

        val nowSeconds = System.currentTimeMillis() / 1000L
        val parsed = WeChatParsedMessage(
            "",
            "",
            cleanCaller,
            cleanSelf,
            cleanCaller,
            cleanCaller,
            "",
            "",
            groupMessage = false,
            redPacketMessage = false,
            type = messageType,
            createTimeSeconds = nowSeconds,
            msgSvrId = 0L,
            msgSource = "",
            selfWxId = cleanSelf
        )
        val matched = runCatching { shouldBlock(settings, parsed) }
            .onFailure { logError("呼入 VoIP 屏蔽规则判定异常，继续微信来电流程", it) }
            .getOrDefault(false)
        return MessageBlockIncomingVoipEvaluation(enabled, privateCaller = true, ruleMatched = matched)
    }

    private fun buildMessage(parsed: WeChatParsedMessage): WeChatMessage {
        return WeChatMessage.fromTransient(
            parsed.talker,
            parsed.sender,
            parsed.content,
            if (parsed.createTimeSeconds > 0L) parsed.createTimeSeconds * 1000L else System.currentTimeMillis(),
            false,
            parsed.type,
            parsed.msgSvrId,
            parsed.msgSource,
            parsed.selfWxId
        )
    }

    private fun templateExcludes(
        settings: MessageBlockSettings,
        template: MessageBlockTemplate,
        parsed: WeChatParsedMessage
    ): Boolean {
        return settings.targetListMatches(template.excludes, parsed.talker, parsed.sender) ||
            settings.groupMemberListMatches(template.excludeGroupMembers, parsed.talker, parsed.sender)
    }

    private fun shouldApplyLegacyTemplate(
        settings: MessageBlockSettings,
        template: MessageBlockTemplate,
        parsed: WeChatParsedMessage,
        message: WeChatMessage
    ): Boolean {
        val inScope = if (template.mode == MessageBlockSettings.MODE_ALL) {
            true
        } else {
            settings.targetListMatches(template.targets, parsed.talker, parsed.sender) ||
                settings.groupMemberListMatches(template.targetGroupMembers, parsed.talker, parsed.sender)
        }
        if (!inScope) return false
        return shouldBlockTemplateType(settings, template, parsed, message)
    }

    private fun shouldBlockTemplateType(
        settings: MessageBlockSettings,
        template: MessageBlockTemplate,
        parsed: WeChatParsedMessage,
        message: WeChatMessage
    ): Boolean {
        return shouldBlockRuleType(settings, template.typeAll, template.types, template.textKeywords, parsed, message)
    }

    private fun shouldBlockBindingType(
        settings: MessageBlockSettings,
        binding: MessageBlockBinding,
        parsed: WeChatParsedMessage,
        message: WeChatMessage
    ): Boolean {
        return shouldBlockRuleType(settings, binding.typeAll, binding.types, binding.textKeywords, parsed, message)
    }

    private fun shouldBlockDefaultRule(
        settings: MessageBlockSettings,
        rule: MessageBlockDefaultRule,
        templatesById: Map<String, MessageBlockTemplate>,
        parsed: WeChatParsedMessage,
        message: WeChatMessage
    ): Boolean {
        if (rule.customRules) {
            return shouldBlockRuleType(settings, rule.typeAll, rule.types, rule.textKeywords, parsed, message)
        }
        return rule.templateIds
            .mapNotNull { templatesById[it] }
            .distinctBy { it.id }
            .any { shouldBlockTemplateType(settings, it, parsed, message) }
    }

    private fun shouldBlockRuleType(
        settings: MessageBlockSettings,
        typeAll: Boolean,
        types: Set<String>,
        textKeywords: String,
        parsed: WeChatParsedMessage,
        message: WeChatMessage
    ): Boolean {
        if (typeAll) {
            return !isTextLikeMessage(message) ||
                settings.keywordMatches(textRuleContent(parsed, message), textKeywords)
        }
        if (isTextLikeMessage(message) &&
            !settings.keywordMatches(textRuleContent(parsed, message), textKeywords)
        ) return false
        return types.any { typeTokenMatches(it.lowercase(Locale.US), message) }
    }

    private fun isTextLikeMessage(message: WeChatMessage): Boolean {
        if (message.isText()) return true
        if (!message.isQuote()) return false
        return runCatching { message.getQuoteMsg()?.title?.isNotBlank() == true }
            .getOrDefault(false)
    }

    private fun textRuleContent(parsed: WeChatParsedMessage, message: WeChatMessage): String {
        if (!message.isQuote()) return parsed.content
        return runCatching { message.getQuoteMsg()?.title.orEmpty() }
            .getOrDefault("")
            .ifBlank { parsed.content }
    }

    private fun bindingScopeActive(
        binding: MessageBlockBinding,
        templatesById: Map<String, MessageBlockTemplate>
    ): Boolean {
        if (binding.customRules) return true
        return binding.templateIds.any { templatesById.containsKey(it) }
    }

    private fun bindingOverridesDefault(binding: MessageBlockBinding): Boolean {
        return binding.action == MessageBlockSettings.ACTION_EXCLUDE ||
            binding.customRules ||
            binding.templateIds.isNotEmpty()
    }

    private fun typeTokenMatches(token: String, message: WeChatMessage): Boolean {
        return when {
            token == MessageBlockSettings.TYPE_TEXT || token == "文字" || token == "文本" -> isTextLikeMessage(message)
            token == MessageBlockSettings.TYPE_QUOTE || token == "引用" || token == "引用消息" -> message.isQuote()
            token == MessageBlockSettings.TYPE_IMAGE || token == "图片" -> message.isImage()
            token == MessageBlockSettings.TYPE_VIDEO || token == "视频" || token == "小视频" -> message.isVideo()
            token == MessageBlockSettings.TYPE_VOICE || token == "语音" -> message.isVoice()
            token == MessageBlockSettings.TYPE_LINK || token == "链接" || token == "文章" || token == "文章/链接" || token == "文件" -> message.isLink() || message.isFile() || message.isNote()
            token == MessageBlockSettings.TYPE_MUSIC || token == "音乐" -> message.isMusic()
            token == MessageBlockSettings.TYPE_MINI_PROGRAM || token == "miniprogram" || token == "小程序" -> message.isMiniProgram()
            token == MessageBlockSettings.TYPE_CARD || token == "名片" -> message.isShareCard()
            token == MessageBlockSettings.TYPE_EMOJI || token == "表情" || token == "动画表情" -> message.isEmoji()
            token == MessageBlockSettings.TYPE_RED_PACKET || token == "redpacket" || token == "红包" -> message.isRedPacket()
            token == MessageBlockSettings.TYPE_TRANSFER || token == "转账" -> message.isTransfer()
            token == MessageBlockSettings.TYPE_VOIP || token == "通话" || token == "视频/语音聊天" || token == "视频语音聊天" || token == "视频聊天" || token == "语音聊天" -> message.isVoip()
            token == MessageBlockSettings.TYPE_LOCATION || token == "位置" || token == "地图" || token == "地图位置" -> message.isLocation()
            token == MessageBlockSettings.TYPE_SYSTEM || token == "系统" -> message.isSystem()
            token == MessageBlockSettings.TYPE_PAT || token == "拍一拍" -> message.isPat()
            token == MessageBlockSettings.TYPE_VIDEO_NUMBER || token == "视频号" || token == "视频号链接" -> message.isVideoNumberVideo()
            token == MessageBlockSettings.TYPE_UNKNOWN || token == "未知" || token == "未知类型" || token == "其他" -> !isKnownMessageType(message)
            token == "app" || token == "appmsg" -> WeChatMessageTypes.normalize(message.type) == WeChatMessageTypes.APP
            else -> false
        }
    }

    private fun isKnownMessageType(message: WeChatMessage): Boolean {
        return isTextLikeMessage(message) ||
            message.isQuote() ||
            message.isImage() ||
            message.isVideo() ||
            message.isVoice() ||
            message.isLink() ||
            message.isFile() ||
            message.isNote() ||
            message.isMusic() ||
            message.isMiniProgram() ||
            message.isShareCard() ||
            message.isEmoji() ||
            message.isRedPacket() ||
            message.isTransfer() ||
            message.isVoip() ||
            message.isLocation() ||
            message.isSystem() ||
            message.isPat() ||
            message.isVideoNumberVideo()
    }

    companion object {
        const val ID = "message_block"
        internal const val VOIP_HOOK_KEY = "$ID.voip_incoming"
        private val VOIP_MESSAGE_TYPES = setOf(1000052, 1000053)
    }
}

internal data class MessageBlockIncomingVoipEvaluation(
    val featureEnabled: Boolean,
    val privateCaller: Boolean,
    val ruleMatched: Boolean
)
