package h.Hchat.hooks.items.messageblock

import android.content.Context
import io.github.libxposed.api.XposedInterface
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import io.github.libxposed.api.XposedInterface.HookHandle
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import org.json.JSONObject

/**
 * Silently drops matched private VoIP invites before the classic or ILINK call manager receives them.
 */
internal class MessageBlockVoipIncomingCallHooker private constructor(
    private val hostContext: Context,
    private val hostClassLoader: ClassLoader,
    private val dexKitBridge: () -> DexKitBridge,
    private val allowReceiverIdentityFallback: Boolean,
    private val evaluateRule: (
        caller: String,
        messageType: Int,
        selfWxId: String,
        allowMissingSelfIdentity: Boolean
    ) -> MessageBlockIncomingVoipEvaluation
) {
    constructor(
        context: FeatureContext,
        evaluateRule: (
            caller: String,
            messageType: Int,
            selfWxId: String,
            allowMissingSelfIdentity: Boolean
        ) -> MessageBlockIncomingVoipEvaluation
    ) : this(
        context.hostContext(),
        context.hostClassLoader(),
        { context.dexKitBridge() },
        false,
        evaluateRule
    )

    private val methodCache = DexMethodCache.prefs(hostContext, CACHE_PREFS)

    @Volatile
    private var installed = false
    @Volatile
    private var hookHandle: HookHandle? = null
    @Volatile
    private var ilinkHookHandles: Map<Method, HookHandle> = emptyMap()
    @Volatile
    private var nativeOutputHookHandles: Map<Method, HookHandle> = emptyMap()
    @Volatile
    private var ilinkEventContextHandle: HookHandle? = null
    private val ilinkEventDispatch = ThreadLocal<Boolean>()

    @Synchronized
    fun install(): Boolean {
        val registry = HookRegistry.get()
        val ilinkFullyInstalled = registry.isActive(ilinkEventContextHandle) &&
            ilinkHookHandles.size == EXPECTED_ILINK_HANDLERS &&
            ilinkHookHandles.values.all(registry::isActive)
        val outputFullyInstalled = nativeOutputHookHandles.size == EXPECTED_NATIVE_OUTPUT_HOOKS &&
            nativeOutputHookHandles.values.all(registry::isActive)
        if (installed && ilinkFullyInstalled && outputFullyInstalled) {
            return true
        }
        val generation = registry.installationGeneration()
        val access = locateAccess()
        if (access != null && !registry.isActive(hookHandle)) {
            runCatching {
                hookHandle = registry.hookOnce(
                    HOOK_ID,
                    access.incoming,
                    XposedInterface.Hooker { chain ->
                        if (!registry.isInstallationGenerationActive(generation)) {
                            HLog.i("[Hchat:${MessageBlockFeature.ID}] VoIP InviteNotify 放行：Hook generation 已失效")
                            return@Hooker chain.proceed()
                        }
                        try {
                            val receiver = chain.thisObject
                            val payload = chain.args.getOrNull(0) as? ByteArray
                            if (receiver == null || payload == null) {
                                return@Hooker chain.proceed()
                            }
                            val room = parseInvite(access, payload) ?: return@Hooker chain.proceed()
                            val messageType = MessageBlockVoipCallDecision.messageTypeForRoomType(room.roomType)
                            if (messageType == 0 || room.roomId == 0 || room.roomKey == 0L) {
                                return@Hooker chain.proceed()
                            }

                            val receiverWxId = readLocalWxId(access, receiver)?.trim().orEmpty()
                            val accountWxId = WeChatApis.account()?.selfWxId()?.trim().orEmpty()
                            val accountMatchesReceiver = accountWxId.isNotBlank() && accountWxId == receiverWxId
                            val receiverMatchesAccount = MessageBlockVoipCallDecision.isReceiverIdentityTrusted(
                                receiverWxId.isNotBlank(),
                                accountWxId.isNotBlank(),
                                accountMatchesReceiver,
                                allowReceiverIdentityFallback
                            )
                            if (!receiverMatchesAccount) return@Hooker chain.proceed()
                            val evaluation = evaluateRule(room.caller, messageType, receiverWxId, false)
                            val shouldIgnore = MessageBlockVoipCallDecision.shouldIgnore(
                                registry.isInstallationGenerationActive(generation),
                                evaluation.featureEnabled,
                                true,
                                true,
                                evaluation.privateCaller,
                                room.roomType,
                                evaluation.ruleMatched
                            )
                            HLog.i(
                                "[Hchat:${MessageBlockFeature.ID}] InviteNotify 判定：" +
                                    "type=$messageType, enabled=${evaluation.featureEnabled}, " +
                                    "privateCaller=${evaluation.privateCaller}, ruleMatched=${evaluation.ruleMatched}, " +
                                    "ignore=$shouldIgnore"
                            )
                            if (!shouldIgnore) return@Hooker chain.proceed()

                            // Returning before the InviteNotify manager sends no signaling request.
                            HLog.i("[Hchat:${MessageBlockFeature.ID}] 私人 VoIP InviteNotify 已本机忽略")
                            null
                        } catch (error: Throwable) {
                            HLog.e("[Hchat:${MessageBlockFeature.ID}] VoIP InviteNotify 判定失败，继续微信原流程", error)
                            chain.proceed()
                        }
                    },
                    XposedInterface.PRIORITY_HIGHEST
                )
            }.onFailure {
                HLog.e("[Hchat:${MessageBlockFeature.ID}] InviteNotify Hook 安装失败，继续微信原流程", it)
            }
        }

        ilinkHookHandles = installIlinkHooks(registry, generation)
        nativeOutputHookHandles = installNativeVoipOutputHooks(registry, generation)
        val classicInstalled = registry.isActive(hookHandle)
        val ilinkInstalled = ilinkHookHandles.values.any(registry::isActive)
        val outputInstalled = nativeOutputHookHandles.values.any(registry::isActive)
        installed = classicInstalled || ilinkInstalled || outputInstalled
        if (classicInstalled) {
            HLog.i("[Hchat:${MessageBlockFeature.ID}] InviteNotify 来电屏蔽 Hook=installed")
        }
        if (!ilinkInstalled) {
            HLog.i("[Hchat:${MessageBlockFeature.ID}] iLink 邀请入口未唯一确认；继续微信原流程")
        }
        if (nativeOutputHookHandles.size != EXPECTED_NATIVE_OUTPUT_HOOKS) {
            HLog.i(
                "[Hchat:${MessageBlockFeature.ID}] VoIPMP 可见输出 Hook 未完整定位 " +
                    "(${nativeOutputHookHandles.size}/$EXPECTED_NATIVE_OUTPUT_HOOKS)"
            )
        }
        return installed
    }

    private fun locateAccess(): IncomingAccess? {
        val runtimeKey = DexMethodCache.runtimeKey(hostContext, hostClassLoader)
        if (runtimeKey.isBlank()) return null
        val cached = DexMethodCache.loadList(
            methodCache,
            runtimeKey,
            hostClassLoader,
            CACHE_METHODS
        )
        if (cached.size == 3) createAccess(cached[0], cached[1], cached[2])?.let { return it }
        if (cached.isNotEmpty()) DexMethodCache.clear(methodCache, runtimeKey, CACHE_METHODS)
        if (methodCache.getString(CACHE_SCAN_RUNTIME, "") == runtimeKey) return null

        val matches = runCatching {
            dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(*INCOMING_NOTIFY_ANCHORS) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] 定位 VoIP InviteNotify 入口失败", it)
            return null
        }

        val accesses = matches.mapNotNull { data ->
            val incoming = runCatching { data.getMethodInstance(hostClassLoader) }
                .getOrNull()?.takeIf(::isIncomingMethod) ?: return@mapNotNull null
            val invokes = runCatching { data.invokes }.getOrDefault(emptyList())
                .mapNotNull { invoke -> runCatching { invoke.getMethodInstance(hostClassLoader) }.getOrNull() }
                .distinctBy { it.toGenericString() }
            val managerMethods = invokes.filter(::isIncomingManagerMethod)
            val manager = managerMethods.singleOrNull() ?: return@mapNotNull null
            val inviteHandlers = invokes.filter { isInviteHandlerMethod(it, incoming, manager) }
            val inviteHandler = inviteHandlers.singleOrNull() ?: return@mapNotNull null
            createAccess(incoming, manager, inviteHandler)
        }.distinctBy { it.incoming.toGenericString() }

        methodCache.edit().putString(CACHE_SCAN_RUNTIME, runtimeKey).apply()
        val access = accesses.singleOrNull() ?: run {
            DexMethodCache.clear(methodCache, runtimeKey, CACHE_METHODS)
            HLog.i("[Hchat:${MessageBlockFeature.ID}] 当前微信版本未唯一确认 VoIP InviteNotify 入口；跳过来电 Hook")
            return null
        }
        DexMethodCache.saveList(methodCache, runtimeKey, CACHE_METHODS, listOf(
            access.incoming,
            access.incomingManager,
            access.inviteHandler
        ))
        return access
    }

    private fun createAccess(incoming: Method, manager: Method, inviteHandler: Method): IncomingAccess? {
        if (!isIncomingMethod(incoming) || !isIncomingManagerMethod(manager) ||
            !isInviteHandlerMethod(inviteHandler, incoming, manager)
        ) {
            return null
        }
        val roomInfoClass = manager.parameterTypes.single()
        if (!hasField(roomInfoClass, "d", Integer.TYPE) ||
            !hasField(roomInfoClass, "e", java.lang.Long.TYPE) ||
            !hasField(roomInfoClass, "i", String::class.java) ||
            !hasField(roomInfoClass, "m", Integer.TYPE)
        ) return null

        val constructor = runCatching { roomInfoClass.getDeclaredConstructor().apply { isAccessible = true } }
            .getOrNull() ?: return null
        val parser = roomInfoClass.methods.singleOrNull { method ->
            method.name == "parseFrom" &&
                method.parameterTypes.contentEquals(arrayOf(ByteArray::class.java)) &&
                method.returnType.isAssignableFrom(roomInfoClass)
        }?.apply { isAccessible = true } ?: return null
        val localWxIdField = findDeclaredField(incoming.declaringClass, "h", String::class.java)
            ?: return null
        runCatching {
            localWxIdField.isAccessible = true
        }.getOrElse { return null }

        return IncomingAccess(
            incoming,
            manager,
            inviteHandler,
            constructor,
            parser,
            findDeclaredField(roomInfoClass, "d", Integer.TYPE) ?: return null,
            findDeclaredField(roomInfoClass, "e", java.lang.Long.TYPE) ?: return null,
            findDeclaredField(roomInfoClass, "i", String::class.java) ?: return null,
            findDeclaredField(roomInfoClass, "m", Integer.TYPE) ?: return null,
            localWxIdField
        )
    }

    /**
     * WeChat 8.0.80 routes event 12 through the ILINK bridge instead of h2.C. Hook both verified
     * concrete implementations of the interface call made by that event branch so the message
     * never reaches the native call manager, notification, ring, or local call-record path.
     */
    private fun installIlinkHooks(registry: HookRegistry, generation: Long): Map<Method, HookHandle> {
        val access = locateIlinkInviteAccess() ?: return emptyMap()
        HLog.i(
            "[Hchat:${MessageBlockFeature.ID}] iLink 来源入口确认：event12=true, " +
                "newSync=${access.auxiliaryIngressVerified}, bypass=${access.auxiliaryIngressVerified}"
        )
        if (!registry.isActive(ilinkEventContextHandle)) {
            runCatching {
                ilinkEventContextHandle = registry.hookOnce(
                    "$HOOK_ID.ilink_event_context",
                    access.eventCallback,
                    XposedInterface.Hooker { chain ->
                        if (!registry.isInstallationGenerationActive(generation)) return@Hooker chain.proceed()
                        val eventCode = runCatching { readVoipEventCode(chain.args.getOrNull(0)) }.getOrNull()
                        if (eventCode != ILINK_INVITE_EVENT_CODE) return@Hooker chain.proceed()
                        val alreadyDispatching = ilinkEventDispatch.get() == true
                        ilinkEventDispatch.set(true)
                        try {
                            chain.proceed()
                        } finally {
                            if (alreadyDispatching) ilinkEventDispatch.set(true) else ilinkEventDispatch.remove()
                        }
                    },
                    XposedInterface.PRIORITY_HIGHEST
                )
            }.onFailure {
                HLog.e("[Hchat:${MessageBlockFeature.ID}] iLink 事件上下文 Hook 安装失败，继续微信原流程", it)
            }
        }
        if (!registry.isActive(ilinkEventContextHandle)) return emptyMap()
        val methods = access.handlers
        val handles = LinkedHashMap<Method, HookHandle>()
        methods.forEach { method ->
            runCatching {
                val id = "$HOOK_ID.ilink.${method.declaringClass.name}"
                val handle = registry.hookOnce(
                    id,
                    method,
                    XposedInterface.Hooker { chain ->
                        if (!registry.isInstallationGenerationActive(generation)) return@Hooker chain.proceed()
                        try {
                            if (ilinkEventDispatch.get() != true) return@Hooker chain.proceed()
                            val args = chain.args
                            val subtype = args.getOrNull(0) as? String ?: return@Hooker chain.proceed()
                            val xml = args.getOrNull(1) as? String ?: return@Hooker chain.proceed()
                            val caller = args.getOrNull(3) as? String ?: return@Hooker chain.proceed()
                            val notifySource = args.getOrNull(4)
                            val source = when {
                                notifySource === access.notifySources[ILINK_SOURCE_DATA_NOTIFY] &&
                                    ilinkEventDispatch.get() == true -> ILINK_SOURCE_EVENT_12
                                access.auxiliaryIngressVerified &&
                                    notifySource === access.notifySources[ILINK_SOURCE_NEW_SYNC] -> ILINK_SOURCE_NEW_SYNC
                                access.auxiliaryIngressVerified &&
                                    notifySource === access.notifySources[ILINK_SOURCE_BYPASS] -> ILINK_SOURCE_BYPASS
                                else -> ""
                            }
                            val validInvite = MessageBlockVoipCallDecision.isIlinkInviteShape(
                                subtype,
                                hasIlinkInvitePayload(xml),
                                source.isNotEmpty()
                            )
                            if (!validInvite || caller.isBlank()) return@Hooker chain.proceed()

                            // This bridge does not expose the voice/video discriminator until the
                            // native engine receives the invite. Require both supported VoIP
                            // message types to match, so unknown type details cannot widen a rule.
                            val selfWxId = WeChatApis.account()?.selfWxId()?.trim().orEmpty()
                            if (selfWxId.isBlank()) {
                                HLog.i("[Hchat:${MessageBlockFeature.ID}] iLink 私人来电放行：本地账号未知")
                                return@Hooker chain.proceed()
                            }
                            val voice = evaluateRule(caller, VOIP_VOICE_MESSAGE_TYPE, selfWxId, false)
                            val video = evaluateRule(caller, VOIP_VIDEO_MESSAGE_TYPE, selfWxId, false)
                            val privateCaller = voice.privateCaller && video.privateCaller
                            val enabled = voice.featureEnabled && video.featureEnabled
                            val shouldIgnore = MessageBlockVoipCallDecision.shouldIgnoreUnknownVoipVariant(
                                registry.isInstallationGenerationActive(generation),
                                enabled,
                                true,
                                true,
                                privateCaller,
                                voice.ruleMatched,
                                video.ruleMatched
                            )
                            HLog.i(
                                "[Hchat:${MessageBlockFeature.ID}] iLink 来电判定：" +
                                    "enabled=$enabled, callerReady=${caller.isNotBlank()}, " +
                                    "privateCaller=$privateCaller, voiceRule=${voice.ruleMatched}, " +
                                    "videoRule=${video.ruleMatched}, source=$source, ignore=$shouldIgnore"
                            )
                            if (!shouldIgnore) return@Hooker chain.proceed()

                            // No native invite processing or response signal is performed here.
                            HLog.i("[Hchat:${MessageBlockFeature.ID}] 私人 iLink VoIP 来电已本机忽略")
                            null
                        } catch (error: Throwable) {
                            HLog.e("[Hchat:${MessageBlockFeature.ID}] iLink VoIP 判定异常，继续微信原流程", error)
                            chain.proceed()
                        }
                    },
                    XposedInterface.PRIORITY_HIGHEST
                )
                handles[method] = handle
            }.onFailure {
                HLog.e("[Hchat:${MessageBlockFeature.ID}] iLink 来电 Hook 安装失败，继续微信原流程", it)
            }
        }
        HLog.i(
            "[Hchat:${MessageBlockFeature.ID}] iLink 邀请处理 Hook=" +
                if (handles.size == methods.size) "installed (${handles.size})" else "partial (${handles.size}/${methods.size})"
        )
        return handles
    }

    /** Fallback gates for native callbacks reached through another receive path. */
    private fun installNativeVoipOutputHooks(
        registry: HookRegistry,
        generation: Long
    ): Map<Method, HookHandle> {
        val access = locateNativeOutputAccess() ?: return emptyMap()
        val handles = LinkedHashMap<Method, HookHandle>()
        val routes = mutableListOf(
            NativeOutputRoute(access.incomingCard, "incoming_card") { args ->
                val roomType = args.getOrNull(1) as? Int
                if (roomType == null) null else outputInvite(
                    args.getOrNull(5), MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(roomType)
                )
            },
            NativeOutputRoute(access.startRing, "ring") { args ->
                val roomType = args.getOrNull(2) as? Int
                if (roomType == null) null else outputInvite(
                    args.getOrNull(1), MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(roomType)
                )
            },
            NativeOutputRoute(access.launchNotify, "notification") { args ->
                val roomType = args.getOrNull(2) as? Int
                if (roomType == null) null else outputInvite(
                    args.getOrNull(1), MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(roomType)
                )
            },
            NativeOutputRoute(access.insertCallRecord, "call_record") { args ->
                val localCaller = args.getOrNull(1) as? Boolean
                val recordType = args.getOrNull(2) as? Int
                if (localCaller == null || recordType == null ||
                    !MessageBlockVoipCallDecision.isIncomingPrivateVoipRecord(localCaller, recordType)
                ) {
                    null
                } else {
                    outputInvite(
                        args.getOrNull(0),
                        MessageBlockVoipCallDecision.messageTypeForIlinkRecordType(recordType),
                        allowMissingSelfIdentity = true
                    )
                }
            }
        )
        val messageNotificationMethod = locateVoipMessageNotificationMethod()
        if (messageNotificationMethod != null) {
            routes += NativeOutputRoute(messageNotificationMethod, "message_notification", ::extractVoipMessageNotification)
        } else {
            HLog.i("[Hchat:${MessageBlockFeature.ID}] VoIP 消息通知入口未唯一确认；保留其他输出 Hook")
        }

        routes.forEach { outputRoute ->
            val method = outputRoute.method
            val route = outputRoute.name
            runCatching {
                val handle = registry.hookOnce(
                    "$HOOK_ID.voipmp_output.$route",
                    method,
                    XposedInterface.Hooker { chain ->
                        if (!registry.isInstallationGenerationActive(generation)) return@Hooker chain.proceed()
                        try {
                            val args = chain.args
                            val invite = outputRoute.extract(args) ?: return@Hooker chain.proceed()
                            if (invite.messageType == 0 || invite.caller.isBlank()) return@Hooker chain.proceed()
                            if (!shouldIgnoreNativeVoipOutput(invite, route, generation, registry)) {
                                return@Hooker chain.proceed()
                            }
                            HLog.i(
                                "[Hchat:${MessageBlockFeature.ID}] iLink 本机输出已按规则跳过，path=$route"
                            )
                            null
                        } catch (error: Throwable) {
                            HLog.e(
                                "[Hchat:${MessageBlockFeature.ID}] iLink 输出判定异常，继续微信原流程，path=$route",
                                error
                            )
                            chain.proceed()
                        }
                    },
                    XposedInterface.PRIORITY_HIGHEST
                )
                handles[method] = handle
            }.onFailure {
                HLog.e(
                    "[Hchat:${MessageBlockFeature.ID}] iLink 输出 Hook 安装失败，继续微信原流程，path=$route",
                    it
                )
            }
        }
        HLog.i(
            "[Hchat:${MessageBlockFeature.ID}] VoIPMP 输出 Hook=" +
                if (handles.size == EXPECTED_NATIVE_OUTPUT_HOOKS) "installed (${handles.size})" else "partial (${handles.size}/${EXPECTED_NATIVE_OUTPUT_HOOKS})"
        )
        return handles
    }

    private fun outputInvite(
        callerValue: Any?,
        messageType: Int,
        allowMissingSelfIdentity: Boolean = false
    ): NativeOutputInvite? {
        if (messageType == 0) return null
        val caller = when (callerValue) {
            is String -> callerValue.trim()
            is ByteArray -> String(callerValue, StandardCharsets.UTF_8).trim()
            else -> ""
        }
        return caller.takeIf { it.isNotBlank() }?.let {
            NativeOutputInvite(it, messageType, allowMissingSelfIdentity)
        }
    }

    /**
     * WeChat 8.0.80's x.d notification dispatcher computes needSound/needShake and triggers
     * vibration before calling the later Notification builder. Filter the verified message row
     * here, before either side effect can occur.
     */
    private fun extractVoipMessageNotification(args: List<Any?>): NativeOutputInvite? {
        val notificationType = args.getOrNull(3) as? Int ?: return null
        if (notificationType !in VOIP_DATABASE_MESSAGE_TYPES) return null
        val snapshot = notificationMessageSnapshot(args) ?: return null
        val storedType = snapshot.optInt("type", 0)
        val isSend = snapshot.optInt("isSend", -1)
        if (!MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(
                notificationType,
                storedType,
                isSend
            )
        ) return null

        val notificationTalker = (args.getOrNull(1) as? String)?.trim().orEmpty()
        val messageTalker = snapshot.optString("talker", "").trim()
        if (notificationTalker.isBlank() || messageTalker.isBlank() || notificationTalker != messageTalker) {
            return null
        }
        val content = snapshot.optString("content", "")
        val messageType = when {
            content.contains("voip_content_video", ignoreCase = true) ||
                notificationType == VOIP_VIDEO_DATABASE_MESSAGE_TYPE -> VOIP_VIDEO_MESSAGE_TYPE
            else -> VOIP_VOICE_MESSAGE_TYPE
        }
        // isSend=0 plus a type 50 call row verifies incoming direction when account lookup is
        // unavailable in the push process. evaluateIncomingVoip still rejects group/official talkers.
        return outputInvite(notificationTalker, messageType, allowMissingSelfIdentity = true)
    }

    private fun notificationMessageSnapshot(args: List<Any?>): JSONObject? {
        val dispatcher = args.getOrNull(0) ?: return null
        val messageField = runCatching { dispatcher.javaClass.getDeclaredField("d") }
            .getOrNull()
            ?.takeIf { it.type.name == VOIP_MESSAGE_DATA_CLASS }
            ?.apply { isAccessible = true }
            ?: return null
        val messageObject = runCatching { messageField.get(dispatcher) }.getOrNull() ?: return null
        return runCatching {
            messageObject.javaClass.getMethod("toJSON").invoke(messageObject) as? JSONObject
        }.getOrNull()
    }

    private fun shouldIgnoreNativeVoipOutput(
        invite: NativeOutputInvite,
        path: String,
        generation: Long,
        registry: HookRegistry
    ): Boolean {
        val selfWxId = WeChatApis.account()?.selfWxId()?.trim().orEmpty()
        if (selfWxId.isBlank() && !invite.allowMissingSelfIdentity) {
            HLog.i("[Hchat:${MessageBlockFeature.ID}] iLink 本机输出放行：账号未知，path=$path")
            return false
        }
        val evaluation = evaluateRule(
            invite.caller,
            invite.messageType,
            selfWxId,
            invite.allowMissingSelfIdentity
        )
        val ignore = MessageBlockVoipCallDecision.shouldIgnoreMessageType(
            registry.isInstallationGenerationActive(generation),
            evaluation.featureEnabled,
            true,
            true,
            evaluation.privateCaller,
            invite.messageType,
            evaluation.ruleMatched
        )
        HLog.i(
            "[Hchat:${MessageBlockFeature.ID}] iLink 本机输出判定：path=$path, " +
                "enabled=${evaluation.featureEnabled}, privateCaller=${evaluation.privateCaller}, " +
                "ruleMatched=${evaluation.ruleMatched}, ignore=$ignore"
        )
        return ignore
    }

    private fun locateNativeOutputAccess(): NativeOutputAccess? {
        val runtimeKey = DexMethodCache.runtimeKey(hostContext, hostClassLoader)
        if (runtimeKey.isBlank()) return null
        val cached = DexMethodCache.loadList(methodCache, runtimeKey, hostClassLoader, OUTPUT_CACHE_METHODS)
        if (cached.size == EXPECTED_NATIVE_OUTPUT_ACCESS_METHODS &&
            isIncomingCardCallback(cached[0]) &&
            isNativeRoomCallback(cached[1]) &&
            isNativeRoomCallback(cached[2]) &&
            isNativeRecordInsertMethod(cached[3])
        ) {
            return NativeOutputAccess(cached[0], cached[1], cached[2], cached[3])
        }
        if (cached.isNotEmpty()) DexMethodCache.clear(methodCache, runtimeKey, OUTPUT_CACHE_METHODS)

        val card = findUniqueMethod(VOIPMP_CARD_ANCHORS, ::isIncomingCardCallback) ?: return null
        val ring = findUniqueMethod(VOIPMP_RING_ANCHORS, ::isNativeRoomCallback) ?: return null
        val notification = findUniqueMethod(VOIPMP_NOTIFY_ANCHORS, ::isNativeRoomCallback) ?: return null
        val record = findUniqueMethod(VOIPMP_RECORD_ANCHORS, ::isNativeRecordInsertMethod) ?: return null
        if (!hasVerifiedRecordInsertCaller(record)) {
            // The fixed 8.0.80 DEX audit confirms DVO6HrxwB.ZIDL_DCV invokes this unique Ji method.
            // Some DexKit builds omit interface invoke edges at runtime, so keep the exact anchor and
            // descriptor validation as the runtime gate and fail open inside the record callback.
            HLog.i("[Hchat:${MessageBlockFeature.ID}] VoIPMP Ji runtime invoke edge unavailable; unique descriptor retained")
        }
        val methods = listOf(card, ring, notification, record)
        DexMethodCache.saveList(methodCache, runtimeKey, OUTPUT_CACHE_METHODS, methods)
        return NativeOutputAccess(card, ring, notification, record)
    }

    private fun locateVoipMessageNotificationMethod(): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(hostContext, hostClassLoader)
        if (runtimeKey.isBlank()) return null
        val cached = DexMethodCache.loadList(
            methodCache,
            runtimeKey,
            hostClassLoader,
            VOIP_MESSAGE_NOTIFICATION_CACHE_METHODS
        )
        if (cached.size == 1 && isVoipMessageNotificationMethod(cached.single())) return cached.single()
        if (cached.isNotEmpty()) {
            DexMethodCache.clear(methodCache, runtimeKey, VOIP_MESSAGE_NOTIFICATION_CACHE_METHODS)
        }
        val method = findUniqueMethod(
            VOIP_MESSAGE_NOTIFICATION_ANCHORS,
            ::isVoipMessageNotificationMethod
        ) ?: return null
        DexMethodCache.saveList(methodCache, runtimeKey, VOIP_MESSAGE_NOTIFICATION_CACHE_METHODS, listOf(method))
        return method
    }

    private fun findUniqueMethod(anchors: Array<String>, accepts: (Method) -> Boolean): Method? {
        val matches = runCatching {
            dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(*anchors) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] VoIPMP 输出方法定位失败", it)
            return null
        }
        return matches.mapNotNull { data ->
            runCatching { data.getMethodInstance(hostClassLoader) }.getOrNull()?.takeIf(accepts)
        }.distinctBy { it.toGenericString() }.singleOrNull()
    }

    private fun hasVerifiedRecordInsertCaller(record: Method): Boolean {
        val callers = runCatching {
            dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(*VOIPMP_RECORD_CALLER_ANCHORS) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] VoIPMP 记录回调关系定位失败", it)
            return false
        }
        val expectedParameters = record.parameterTypes
        val verified = callers.mapNotNull { data ->
            val method = runCatching { data.getMethodInstance(hostClassLoader) }
                .getOrNull()
                ?.takeIf(::isNativeRecordBridgeCallback)
                ?: return@mapNotNull null
            val invokesRecord = runCatching { data.invokes }.getOrDefault(emptyList())
                .mapNotNull { invoke -> runCatching { invoke.getMethodInstance(hostClassLoader) }.getOrNull() }
                .any {
                    it.name == ILINK_RECORD_METHOD_NAME &&
                        it.parameterTypes.contentEquals(expectedParameters) &&
                        it.returnType == Void.TYPE
                }
            method.takeIf { invokesRecord }
        }.distinctBy { it.toGenericString() }
        return verified.size == 1
    }

    private fun isIncomingCardCallback(method: Method): Boolean =
        method.parameterTypes.contentEquals(
            arrayOf(
                java.lang.Long.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                ByteArray::class.java,
                Array<ByteArray>::class.java,
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE
            )
        ) && method.returnType == Void.TYPE && !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)

    private fun isNativeRoomCallback(method: Method): Boolean =
        method.parameterTypes.contentEquals(
            arrayOf(java.lang.Long.TYPE, ByteArray::class.java, Integer.TYPE, java.lang.Boolean.TYPE)
        ) && method.returnType == Void.TYPE && !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)

    private fun isNativeRecordInsertMethod(method: Method): Boolean =
        method.name == ILINK_RECORD_METHOD_NAME &&
            method.parameterTypes.contentEquals(
                arrayOf(
                    String::class.java,
                    java.lang.Boolean.TYPE,
                    Integer.TYPE,
                    java.lang.Long.TYPE,
                    java.lang.Long.TYPE,
                    java.lang.Long.TYPE,
                    Integer.TYPE
                )
            ) && method.returnType == Void.TYPE &&
            !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)

    private fun isVoipMessageNotificationMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return params.size == 6 &&
            params[0].name == VOIP_MESSAGE_NOTIFICATION_DISPATCH_CLASS &&
            params[1] == String::class.java &&
            params[2] == String::class.java &&
            params[3] == Integer.TYPE &&
            params[4] == Integer.TYPE &&
            params[5] == java.lang.Boolean.TYPE &&
            method.returnType == Void.TYPE &&
            Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)
    }

    private fun isNativeRecordBridgeCallback(method: Method): Boolean =
        method.parameterTypes.contentEquals(
            arrayOf(
                java.lang.Long.TYPE,
                ByteArray::class.java,
                java.lang.Boolean.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE
            )
        ) && method.returnType == Void.TYPE && !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)

    private fun locateIlinkInviteAccess(): IlinkAccess? {
        val runtimeKey = DexMethodCache.runtimeKey(hostContext, hostClassLoader)
        if (runtimeKey.isBlank()) return null
        val callback = locateIlinkEventCallback() ?: return null
        val auxiliaryIngressVerified = verifyAuxiliaryIlinkIngresses(callback.interfaceMethod)
        val notifySources = readIncomingNotifySources(callback.interfaceMethod.parameterTypes[4])
        if (notifySources == null) {
            HLog.i("[Hchat:${MessageBlockFeature.ID}] iLink 呼入来源枚举未唯一确认；跳过 iLink Hook")
            return null
        }
        val cached = DexMethodCache.loadList(methodCache, runtimeKey, hostClassLoader, ILINK_CACHE_METHODS)
        if (cached.size == EXPECTED_ILINK_HANDLERS && cached.all {
                isIlinkHandlerMethod(it, callback.interfaceMethod.declaringClass)
            }
        ) {
            return IlinkAccess(
                callback.callback,
                callback.interfaceMethod,
                cached,
                notifySources,
                auxiliaryIngressVerified
            )
        }
        if (cached.isNotEmpty()) DexMethodCache.clear(methodCache, runtimeKey, ILINK_CACHE_METHODS)
        if (methodCache.getString(ILINK_CACHE_SCAN_RUNTIME, "") == runtimeKey) return null

        val interfaceMethod = callback.interfaceMethod
        val handlerMatches = runCatching {
            dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(*ILINK_HANDLER_ANCHORS) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] 定位 iLink 邀请处理方法失败", it)
            return null
        }
        val candidates = handlerMatches.mapNotNull { data ->
            val method = runCatching { data.getMethodInstance(hostClassLoader) }
                .getOrNull()
                ?.takeIf { isIlinkHandlerMethod(it, interfaceMethod.declaringClass) }
                ?: return@mapNotNull null
            val invokes = runCatching { data.invokes }.getOrDefault(emptyList())
                .mapNotNull { invoke -> runCatching { invoke.getMethodInstance(hostClassLoader) }.getOrNull() }
            method to invokes
        }.distinctBy { it.first.toGenericString() }
        val methods = candidates.map { it.first }
        val methodDescriptors = methods.map { it.toGenericString() }.toSet()
        val hasConcreteDelegation = candidates.any { (_, invokes) ->
            invokes.any { it.toGenericString() in methodDescriptors }
        }
        val hasNativeBridge = candidates.any { (method, invokes) ->
            invokes.any { isIlinkNativeBridge(it, method) }
        }
        if (methods.size != EXPECTED_ILINK_HANDLERS || !hasConcreteDelegation || !hasNativeBridge) {
            markIlinkScanComplete(runtimeKey)
            HLog.i(
                "[Hchat:${MessageBlockFeature.ID}] iLink 处理链未唯一确认 " +
                    "(handlers=${methods.size}, delegates=$hasConcreteDelegation, nativeBridge=$hasNativeBridge)；跳过 iLink Hook"
            )
            return null
        }

        DexMethodCache.saveList(methodCache, runtimeKey, ILINK_CACHE_METHODS, methods)
        markIlinkScanComplete(runtimeKey)
        return IlinkAccess(callback.callback, interfaceMethod, methods, notifySources, auxiliaryIngressVerified)
    }

    /** Confirms the two additional inbound sysmsg paths that converge on the same native bridge. */
    private fun verifyAuxiliaryIlinkIngresses(interfaceMethod: Method): Boolean =
        hasIlinkIngressCallsite(ILINK_EXTENSION_INGRESS_ANCHORS, interfaceMethod) &&
            hasIlinkIngressCallsite(ILINK_BYPASS_INGRESS_ANCHORS, interfaceMethod)

    private fun hasIlinkIngressCallsite(anchors: Array<String>, interfaceMethod: Method): Boolean {
        val matches = runCatching {
            dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(*anchors) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] iLink 辅助呼入来源定位失败，继续微信原流程", it)
            return false
        }
        val callers = matches.mapNotNull { data ->
            val method = runCatching { data.getMethodInstance(hostClassLoader) }.getOrNull()
                ?: return@mapNotNull null
            val invokesInterface = runCatching { data.invokes }
                .getOrDefault(emptyList())
                .mapNotNull { invoke -> runCatching { invoke.getMethodInstance(hostClassLoader) }.getOrNull() }
                .any { it.toGenericString() == interfaceMethod.toGenericString() }
            method.takeIf { invokesInterface }
        }.distinctBy { it.toGenericString() }
        return callers.size == 1
    }

    private fun locateIlinkEventCallback(): IlinkCallbackAccess? {
        val matches = runCatching {
            dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(VOIP_EVENT_CALLBACK_ANCHOR) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] 定位 iLink 邀请事件调用点失败", it)
            return null
        }
        val callbacks = matches.mapNotNull { data ->
            val method = runCatching { data.getMethodInstance(hostClassLoader) }
                .getOrNull()
                ?.takeIf(::isVoipEventCallback)
                ?: return@mapNotNull null
            val invokes = runCatching { data.invokes }.getOrDefault(emptyList())
                .mapNotNull { invoke -> runCatching { invoke.getMethodInstance(hostClassLoader) }.getOrNull() }
            val interfaceMethod = invokes.singleOrNull(::isIlinkInterfaceMethod)
                ?: return@mapNotNull null
            IlinkCallbackAccess(method, interfaceMethod)
        }.distinctBy { it.callback.toGenericString() }
        return callbacks.singleOrNull()
    }

    private fun readVoipEventCode(event: Any?): Int? {
        if (event == null || event.javaClass.name != VOIP_EVENT_CLASS) return null
        val data = runCatching { event.javaClass.getDeclaredField("g").apply { isAccessible = true }.get(event) }
            .getOrNull() ?: return null
        val codeField = runCatching { data.javaClass.getDeclaredField("b") }
            .getOrNull()
            ?.takeIf { it.type == Integer.TYPE }
            ?.apply { isAccessible = true }
            ?: return null
        return runCatching { codeField.getInt(data) }.getOrNull()
    }

    private fun markIlinkScanComplete(runtimeKey: String) {
        methodCache.edit().putString(ILINK_CACHE_SCAN_RUNTIME, runtimeKey).apply()
    }

    private fun hasIlinkInvitePayload(xml: String): Boolean {
        if (!xml.startsWith(ILINK_INVITE_XML_PREFIX) || !xml.endsWith(ILINK_INVITE_XML_SUFFIX)) return false
        val contentStart = ILINK_INVITE_XML_PREFIX.length
        val contentEnd = xml.length - ILINK_INVITE_XML_SUFFIX.length
        return contentEnd > contentStart && xml.substring(contentStart, contentEnd).isNotBlank()
    }

    private fun isVoipEventCallback(method: Method): Boolean =
        method.parameterTypes.size == 1 &&
            method.parameterTypes.single().name == VOIP_EVENT_INTERFACE &&
            method.returnType == java.lang.Boolean.TYPE &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)

    private fun isIlinkInterfaceMethod(method: Method): Boolean =
        method.declaringClass.isInterface &&
            method.name == ILINK_HANDLER_NAME &&
            method.parameterTypes.size == 5 &&
            method.parameterTypes[0] == String::class.java &&
            method.parameterTypes[1] == String::class.java &&
            method.parameterTypes[2] == java.lang.Long.TYPE &&
            method.parameterTypes[3] == String::class.java &&
            method.parameterTypes[4].isEnum &&
            method.returnType == Void.TYPE

    private fun isIlinkHandlerMethod(method: Method, interfaceClass: Class<*>): Boolean =
        hasIlinkHandlerSignature(method) && interfaceClass.isAssignableFrom(method.declaringClass)

    private fun hasIlinkHandlerSignature(method: Method): Boolean =
        method.name == ILINK_HANDLER_NAME &&
            method.parameterTypes.size == 5 &&
            method.parameterTypes[0] == String::class.java &&
            method.parameterTypes[1] == String::class.java &&
            method.parameterTypes[2] == java.lang.Long.TYPE &&
            method.parameterTypes[3] == String::class.java &&
            method.parameterTypes[4].isEnum &&
            method.returnType == Void.TYPE &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)

    private fun isIlinkNativeBridge(method: Method, handler: Method): Boolean =
        method.parameterTypes.size == handler.parameterTypes.size + 1 &&
            method.parameterTypes.take(handler.parameterTypes.size) == handler.parameterTypes.toList() &&
            method.returnType == Void.TYPE

    private fun readIncomingNotifySources(enumClass: Class<*>): Map<String, Any>? = runCatching {
        if (!enumClass.isEnum) return null
        val fields = listOf(
            ILINK_SOURCE_DATA_NOTIFY,
            ILINK_SOURCE_NEW_SYNC,
            ILINK_SOURCE_BYPASS
        ).associateWith { name ->
            val field = enumClass.getDeclaredField(name).apply { isAccessible = true }
            check(Modifier.isStatic(field.modifiers) && field.type == enumClass)
            field.get(null)?.takeIf(enumClass::isInstance) ?: error("Missing notify source")
        }
        fields
    }.getOrNull()

    private fun parseInvite(access: IncomingAccess, payload: ByteArray): IncomingInvite? {
        // h2.C([B)V itself accepts only the prefixed InviteNotify form and strips byte 0x01.
        if (payload.size < MIN_INVITE_BYTES || payload[0] != INVITE_NOTIFY_PREFIX) return null
        val message = access.roomInfoConstructor.newInstance()
        val parsed = access.roomInfoParser.invoke(message, payload.copyOfRange(1, payload.size))
            ?.takeIf { access.incomingManager.parameterTypes.single().isInstance(it) }
            ?: return null
        val caller = (access.callerField.get(parsed) as? String)?.trim().orEmpty()
        if (caller.isBlank()) return null
        return IncomingInvite(
            roomId = access.roomIdField.getInt(parsed),
            roomKey = access.roomKeyField.getLong(parsed),
            caller = caller,
            roomType = access.roomTypeField.getInt(parsed)
        )
    }

    private fun readLocalWxId(access: IncomingAccess, receiver: Any): String? =
        access.localWxIdField.get(receiver) as? String

    private fun isIncomingMethod(method: Method): Boolean =
        method.parameterTypes.contentEquals(arrayOf(ByteArray::class.java)) &&
            method.returnType == Void.TYPE &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)

    private fun isIncomingManagerMethod(method: Method): Boolean =
        method.parameterTypes.size == 1 &&
            method.returnType == java.lang.Boolean.TYPE &&
            hasRoomInfoFields(method.parameterTypes.single()) &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)

    private fun isInviteHandlerMethod(method: Method, incoming: Method, manager: Method): Boolean =
        method.declaringClass == incoming.declaringClass &&
            method.parameterTypes.contentEquals(manager.parameterTypes) &&
            method.returnType == Void.TYPE &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)

    private fun hasRoomInfoFields(type: Class<*>): Boolean =
        hasField(type, "d", Integer.TYPE) &&
            hasField(type, "e", java.lang.Long.TYPE) &&
            hasField(type, "i", String::class.java) &&
            hasField(type, "m", Integer.TYPE)

    private fun hasField(type: Class<*>, name: String, fieldType: Class<*>): Boolean =
        findDeclaredField(type, name, fieldType) != null

    private fun findDeclaredField(type: Class<*>, name: String, fieldType: Class<*>): Field? =
        runCatching { type.getDeclaredField(name) }
            .getOrNull()
            ?.takeIf { it.type == fieldType }

    private data class IncomingAccess(
        val incoming: Method,
        val incomingManager: Method,
        val inviteHandler: Method,
        val roomInfoConstructor: Constructor<*>,
        val roomInfoParser: Method,
        val roomIdField: Field,
        val roomKeyField: Field,
        val callerField: Field,
        val roomTypeField: Field,
        val localWxIdField: Field
    )

    private data class IncomingInvite(
        val roomId: Int,
        val roomKey: Long,
        val caller: String,
        val roomType: Int
    )

    private data class IlinkCallbackAccess(
        val callback: Method,
        val interfaceMethod: Method
    )

    private data class IlinkAccess(
        val eventCallback: Method,
        val interfaceMethod: Method,
        val handlers: List<Method>,
        val notifySources: Map<String, Any>,
        val auxiliaryIngressVerified: Boolean
    )

    private data class NativeOutputAccess(
        val incomingCard: Method,
        val startRing: Method,
        val launchNotify: Method,
        val insertCallRecord: Method
    )

    private data class NativeOutputRoute(
        val method: Method,
        val name: String,
        val extract: (List<Any?>) -> NativeOutputInvite?
    )

    private data class NativeOutputInvite(
        val caller: String,
        val messageType: Int,
        val allowMissingSelfIdentity: Boolean
    )

    companion object Factory {
        const val HOOK_ID = "message_block.voip_incoming_invite_notify"
        const val CACHE_PREFS = "Hchat_message_block_voip_method_cache"
        const val CACHE_METHODS = "incoming_invite_access_v3"
        const val CACHE_SCAN_RUNTIME = "incoming_invite_scan_runtime_v3"
        const val ILINK_CACHE_METHODS = "ilink_invite_handler_access_v1"
        const val ILINK_CACHE_SCAN_RUNTIME = "ilink_invite_handler_scan_runtime_v1"
        const val OUTPUT_CACHE_METHODS = "ilink_native_output_access_v1"
        const val VOIP_MESSAGE_NOTIFICATION_CACHE_METHODS = "voip_message_notification_dispatch_access_v2"
        const val ILINK_HANDLER_NAME = "bb"
        const val ILINK_RECORD_METHOD_NAME = "Ji"
        const val ILINK_SOURCE_DATA_NOTIFY = "e"
        const val ILINK_SOURCE_NEW_SYNC = "f"
        const val ILINK_SOURCE_BYPASS = "g"
        const val ILINK_SOURCE_EVENT_12 = "event12"
        const val ILINK_INVITE_XML_PREFIX = "<sysmsg type=\"ilinkvoip\"><voipmt><invite>"
        const val ILINK_INVITE_XML_SUFFIX = "</invite></voipmt></sysmsg>"
        const val EXPECTED_ILINK_HANDLERS = 2
        const val EXPECTED_NATIVE_OUTPUT_ACCESS_METHODS = 4
        const val EXPECTED_NATIVE_OUTPUT_HOOKS = 5
        const val VOIP_VOICE_MESSAGE_TYPE = 1000052
        const val VOIP_VIDEO_MESSAGE_TYPE = 1000053
        const val VOIP_VIDEO_DATABASE_MESSAGE_TYPE = 1000053
        const val VOIP_MESSAGE_DATA_CLASS = "com.tencent.mm.storage.f9"
        const val VOIP_MESSAGE_NOTIFICATION_DISPATCH_CLASS = "com.tencent.mm.booter.notification.x"
        val VOIP_DATABASE_MESSAGE_TYPES = setOf(50, 1000052, 1000053)
        const val MIN_INVITE_BYTES = 10
        const val INVITE_NOTIFY_PREFIX: Byte = 1
        const val VOIP_EVENT_INTERFACE = "com.tencent.mm.sdk.event.IEvent"
        const val VOIP_EVENT_CLASS = "com.tencent.mm.autogen.events.VoipEvent"
        const val ILINK_INVITE_EVENT_CODE = 12
        const val VOIP_EVENT_CALLBACK_ANCHOR = "voipInviteInspect event invite"
        val ILINK_HANDLER_ANCHORS = arrayOf("msgSubType", "newxmlMsg", "username", "notifyFrom")
        val ILINK_EXTENSION_INGRESS_ANCHORS = arrayOf(
            "MicroMsg.ILinkVoIP.ILinkVoIPExtension",
            "onRecieveMsg: msgInfo is null"
        )
        val ILINK_BYPASS_INGRESS_ANCHORS = arrayOf(
            "VoipBypSyncHandler",
            "[onReceive] voipmsg by byp content "
        )
        val VOIPMP_CARD_ANCHORS = arrayOf("launchInComingCardAsync: roomType=")
        val VOIPMP_RING_ANCHORS = arrayOf(
            "MicroMsg.VoIPMP.CoreV2",
            "startRing() called with: username = "
        )
        val VOIPMP_NOTIFY_ANCHORS = arrayOf("MicroMsg.VoIPMP.CoreV2", "launchNotify")
        val VOIPMP_RECORD_ANCHORS = arrayOf(
            "MicroMsg.VoIPMP.Launcher",
            "insertMsg() called with: roomType = "
        )
        val VOIPMP_RECORD_CALLER_ANCHORS = arrayOf(
            "insertMsg() called with: toUser = ",
            "caller = "
        )
        val VOIP_MESSAGE_NOTIFICATION_ANCHORS = arrayOf(
            "jacks dealNotify, talker:%s, msgtype:%d, tipsFlag:%d, isRevokeMesasge:%B content:%s"
        )
        val INCOMING_NOTIFY_ANCHORS = arrayOf(
            "devin:recvInvite:",
            "doTaskCallin in onInviteNotify, roomid:%d, roomkey:%s, time:%d callserUsername:%s",
            "doTaskCallIn implVoip is connected, IncomingCallManager has take over this call!"
        )

        /** Creates the same narrowly scoped hook for a WeChat sub-process. */
        @JvmStatic
        fun forHostProcess(
            hostContext: Context,
            hostClassLoader: ClassLoader,
            dexKitBridge: DexKitBridge,
            allowReceiverIdentityFallback: Boolean
        ): MessageBlockVoipIncomingCallHooker {
            val feature = MessageBlockFeature()
            return MessageBlockVoipIncomingCallHooker(
                hostContext,
                hostClassLoader,
                { dexKitBridge },
                allowReceiverIdentityFallback
            ) { caller, messageType, selfWxId, allowMissingSelfIdentity ->
                feature.evaluateIncomingVoip(
                    hostContext,
                    caller,
                    messageType,
                    selfWxId,
                    allowMissingSelfIdentity
                )
            }
        }
    }
}
