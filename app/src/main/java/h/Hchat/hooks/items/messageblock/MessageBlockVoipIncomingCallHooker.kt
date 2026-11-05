package h.Hchat.hooks.items.messageblock

import io.github.libxposed.api.XposedInterface
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import io.github.libxposed.api.XposedInterface.HookHandle
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Refuses a matched one-to-one VoIP invite at WeChat's incoming InviteNotify handler,
 * before that handler enters the call manager or its notification/ring flow.
 */
internal class MessageBlockVoipIncomingCallHooker(
    private val context: FeatureContext,
    private val evaluateRule: (caller: String, messageType: Int, selfWxId: String) -> MessageBlockIncomingVoipEvaluation
) {
    private val methodCache = DexMethodCache.prefs(context.hostContext(), CACHE_PREFS)

    @Volatile
    private var installed = false
    @Volatile
    private var hookHandle: HookHandle? = null

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val access = locateAccess() ?: return false
        val registry = HookRegistry.get()
        if (registry.isActive(hookHandle)) {
            installed = true
            return true
        }
        val generation = registry.installationGeneration()
        return runCatching {
            val handle = registry.hookOnce(
                HOOK_ID,
                access.incoming,
                XposedInterface.Hooker { chain ->
                    if (!registry.isInstallationGenerationActive(generation)) return@Hooker chain.proceed()
                    try {
                        val receiver = chain.thisObject ?: return@Hooker chain.proceed()
                        val payload = chain.args.getOrNull(0) as? ByteArray ?: return@Hooker chain.proceed()
                        val room = parseInvite(access, payload) ?: return@Hooker chain.proceed()
                        val messageType = MessageBlockVoipCallDecision.messageTypeForRoomType(room.roomType)
                        if (messageType == 0 || room.roomId == 0 || room.roomKey == 0L) {
                            return@Hooker chain.proceed()
                        }

                        val selfWxId = WeChatApis.account()?.selfWxId()?.trim().orEmpty()
                        if (selfWxId.isBlank() || readLocalWxId(access, receiver) != selfWxId) {
                            return@Hooker chain.proceed()
                        }
                        val evaluation = evaluateRule(room.caller, messageType, selfWxId)
                        val ackReceiver = access.ackReceiverField.get(receiver)
                            ?: return@Hooker chain.proceed()
                        val canReject = access.acknowledge.declaringClass.isInstance(ackReceiver)
                        if (!MessageBlockVoipCallDecision.shouldReject(
                                registry.isInstallationGenerationActive(generation),
                                evaluation.featureEnabled,
                                true,
                                evaluation.privateCaller,
                                room.roomType,
                                evaluation.ruleMatched,
                                canReject
                            )
                        ) return@Hooker chain.proceed()

                        // This is the same ackstatus=2 response used by WeChat's own busy branch in
                        // this InviteNotify handler. Returning only after that request succeeds keeps
                        // the invite from reaching h2.h()/IncomingCallManager and the UI/ring path.
                        val ackArgs = arrayOf<Any?>(
                            room.roomId,
                            room.roomKey,
                            BUSY_ACK_STATUS,
                            null,
                            null,
                            selfWxId
                        )
                        access.acknowledge.invoke(ackReceiver, *ackArgs)
                        HLog.i("[Hchat:${MessageBlockFeature.ID}] 私人 VoIP 来电已按屏蔽规则拒绝，type=$messageType")
                        null
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:${MessageBlockFeature.ID}] VoIP 来电拒绝失败，继续微信原流程", error)
                        chain.proceed()
                    }
                },
                XposedInterface.PRIORITY_HIGHEST
            )
            hookHandle = handle
            installed = registry.isActive(handle)
            HLog.i("[Hchat:${MessageBlockFeature.ID}] InviteNotify 来电屏蔽 Hook=" + if (installed) "installed" else "inactive")
            installed
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] InviteNotify 来电屏蔽 Hook 安装失败", it)
            false
        }
    }

    private fun locateAccess(): IncomingAccess? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        if (runtimeKey.isBlank()) return null
        val cached = DexMethodCache.loadList(
            methodCache,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_METHODS
        )
        if (cached.size == 3) createAccess(cached[0], cached[1], cached[2])?.let { return it }
        if (cached.isNotEmpty()) DexMethodCache.clear(methodCache, runtimeKey, CACHE_METHODS)
        if (methodCache.getString(CACHE_SCAN_RUNTIME, "") == runtimeKey) return null

        val matches = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(*INCOMING_NOTIFY_ANCHORS) })
                }
            )
        }.getOrElse {
            HLog.e("[Hchat:${MessageBlockFeature.ID}] 定位 VoIP InviteNotify 入口失败", it)
            return null
        }

        val accesses = matches.mapNotNull { data ->
            val incoming = runCatching { data.getMethodInstance(context.hostClassLoader()) }
                .getOrNull()?.takeIf(::isIncomingMethod) ?: return@mapNotNull null
            val invokes = runCatching { data.invokes }.getOrDefault(emptyList())
                .mapNotNull { invoke -> runCatching { invoke.getMethodInstance(context.hostClassLoader()) }.getOrNull() }
                .distinctBy { it.toGenericString() }
            val managerMethods = invokes.filter(::isIncomingManagerMethod)
            val acknowledgements = invokes.filter(::isAcknowledgeMethod)
            val manager = managerMethods.singleOrNull() ?: return@mapNotNull null
            val acknowledge = acknowledgements.singleOrNull() ?: return@mapNotNull null
            createAccess(incoming, manager, acknowledge)
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
            access.acknowledge
        ))
        return access
    }

    private fun createAccess(incoming: Method, manager: Method, acknowledge: Method): IncomingAccess? {
        if (!isIncomingMethod(incoming) || !isIncomingManagerMethod(manager) || !isAcknowledgeMethod(acknowledge)) {
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
        val ackReceiverField = findField(incoming.declaringClass, acknowledge.declaringClass)
            ?: return null
        val localWxIdField = findDeclaredField(incoming.declaringClass, "h", String::class.java)
            ?: return null
        runCatching {
            acknowledge.isAccessible = true
            ackReceiverField.isAccessible = true
            localWxIdField.isAccessible = true
        }.getOrElse { return null }

        return IncomingAccess(
            incoming,
            manager,
            acknowledge,
            constructor,
            parser,
            findDeclaredField(roomInfoClass, "d", Integer.TYPE) ?: return null,
            findDeclaredField(roomInfoClass, "e", java.lang.Long.TYPE) ?: return null,
            findDeclaredField(roomInfoClass, "i", String::class.java) ?: return null,
            findDeclaredField(roomInfoClass, "m", Integer.TYPE) ?: return null,
            ackReceiverField,
            localWxIdField
        )
    }

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

    private fun isAcknowledgeMethod(method: Method): Boolean {
        val types = method.parameterTypes
        return types.size == 6 &&
            types[0] == Integer.TYPE &&
            types[1] == java.lang.Long.TYPE &&
            types[2] == Integer.TYPE &&
            types[3] == ByteArray::class.java &&
            types[4] == ByteArray::class.java &&
            types[5] == String::class.java &&
            method.returnType == Void.TYPE &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers)
    }

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

    private fun findField(type: Class<*>, fieldType: Class<*>): Field? {
        val matches = generateSequence(type as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.type == fieldType }
            .toList()
        return matches.singleOrNull()
    }

    private data class IncomingAccess(
        val incoming: Method,
        val incomingManager: Method,
        val acknowledge: Method,
        val roomInfoConstructor: Constructor<*>,
        val roomInfoParser: Method,
        val roomIdField: Field,
        val roomKeyField: Field,
        val callerField: Field,
        val roomTypeField: Field,
        val ackReceiverField: Field,
        val localWxIdField: Field
    )

    private data class IncomingInvite(
        val roomId: Int,
        val roomKey: Long,
        val caller: String,
        val roomType: Int
    )

    private companion object {
        const val HOOK_ID = "message_block.voip_incoming_invite_notify"
        const val CACHE_PREFS = "Hchat_message_block_voip_method_cache"
        const val CACHE_METHODS = "incoming_invite_access_v1"
        const val CACHE_SCAN_RUNTIME = "incoming_invite_scan_runtime_v1"
        const val BUSY_ACK_STATUS = 2
        const val MIN_INVITE_BYTES = 10
        const val INVITE_NOTIFY_PREFIX: Byte = 1
        val INCOMING_NOTIFY_ANCHORS = arrayOf(
            "devin:recvInvite:",
            "doTaskCallin in onInviteNotify, roomid:%d, roomkey:%s, time:%d callserUsername:%s",
            "doTaskCallIn implVoip is connected, IncomingCallManager has take over this call!"
        )
    }
}
