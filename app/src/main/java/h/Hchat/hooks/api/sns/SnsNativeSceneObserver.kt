package h.Hchat.hooks.api.sns

import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class NativeSnsPageCallback(
    val serverIds: List<String>,
    val responseCaptured: Boolean,
    val responseCount: Int,
    val downAll: Boolean?,
    val errType: Int,
    val errCode: Int,
    val errorMessage: String
)

internal enum class NativeSnsPageState { READ_FAILED, EMPTY, HAS_NEXT, COMPLETE }

internal data class NativeSnsPageDecision(
    val state: NativeSnsPageState,
    val error: String = ""
)

internal object NativeSnsPagePolicy {
    fun classify(callback: NativeSnsPageCallback, mappedIds: Collection<String>): NativeSnsPageDecision {
        if (callback.errType != 0 || callback.errCode != 0) {
            return NativeSnsPageDecision(NativeSnsPageState.READ_FAILED,
                callback.errorMessage.ifBlank { "朋友圈服务器返回错误：${callback.errType}/${callback.errCode}" })
        }
        if (!callback.responseCaptured || callback.downAll == null) {
            return NativeSnsPageDecision(NativeSnsPageState.READ_FAILED, "服务器响应或结束游标缺失")
        }
        if (callback.responseCount != callback.serverIds.size) {
            return NativeSnsPageDecision(NativeSnsPageState.READ_FAILED, "服务器动态数量与 SNS ID 数量不一致")
        }
        val responseIds = callback.serverIds.toSet()
        if (responseIds.size != callback.serverIds.size || mappedIds.toSet() != responseIds || mappedIds.size != callback.serverIds.size) {
            return NativeSnsPageDecision(NativeSnsPageState.READ_FAILED, "服务端 SNS ID 与本地动态映射不一致")
        }
        return when {
            callback.downAll && callback.serverIds.isEmpty() -> NativeSnsPageDecision(NativeSnsPageState.EMPTY)
            callback.downAll -> NativeSnsPageDecision(NativeSnsPageState.COMPLETE)
            else -> NativeSnsPageDecision(NativeSnsPageState.HAS_NEXT)
        }
    }
}

internal enum class NativeSnsDeleteCallbackKind {
    SERVER_ACCEPTED,
    SERVER_REJECTED,
    SUBMITTED_UNCONFIRMED
}

internal data class NativeSnsDeleteCallback(
    val kind: NativeSnsDeleteCallbackKind,
    val errType: Int,
    val errCode: Int,
    val errorMessage: String
)

internal enum class NativeSnsDeleteDecisionKind { NOT_SUBMITTED, SERVER_REJECTED, SUCCESS, UNCONFIRMED }

internal object NativeSnsDeletePolicy {
    fun classify(
        submitted: Boolean,
        callback: NativeSnsDeleteCallback?,
        timedOut: Boolean,
        accountCurrent: Boolean,
        localCheckSucceeded: Boolean?,
        localStillPresent: Boolean?
    ): NativeSnsDeleteDecisionKind = when {
        !submitted -> NativeSnsDeleteDecisionKind.NOT_SUBMITTED
        timedOut || callback == null -> NativeSnsDeleteDecisionKind.UNCONFIRMED
        callback.kind == NativeSnsDeleteCallbackKind.SERVER_REJECTED -> NativeSnsDeleteDecisionKind.SERVER_REJECTED
        callback.kind != NativeSnsDeleteCallbackKind.SERVER_ACCEPTED -> NativeSnsDeleteDecisionKind.UNCONFIRMED
        !accountCurrent || localCheckSucceeded != true || localStillPresent != false ->
            NativeSnsDeleteDecisionKind.UNCONFIRMED
        else -> NativeSnsDeleteDecisionKind.SUCCESS
    }
}

internal sealed class NativeSnsPageWaitResult {
    data class Completed(val callback: NativeSnsPageCallback) : NativeSnsPageWaitResult()
    data class NotStarted(val message: String) : NativeSnsPageWaitResult()
    data class Failed(val message: String) : NativeSnsPageWaitResult()
    data object TimedOut : NativeSnsPageWaitResult()
}

internal sealed class NativeSnsDeleteWaitResult {
    data class Callback(val value: NativeSnsDeleteCallback) : NativeSnsDeleteWaitResult()
    data object NotSubmitted : NativeSnsDeleteWaitResult()
    data object TimedOut : NativeSnsDeleteWaitResult()
    data class Failed(val message: String) : NativeSnsDeleteWaitResult()
}

/** Observes the exact native SNS scenes used by WeChat 8.0.80. */
internal class SnsNativeSceneObserver(
    private val classLoader: ClassLoader,
    private val logger: (String) -> Unit
) {
    private class PageTicket(val userName: String) {
        private val latch = CountDownLatch(1)
        private val idLock = Any()
        private val ids = LinkedHashSet<String>()

        @Volatile private var sawResponse = false
        @Volatile private var completion: NativeSnsPageCallback? = null

        fun capture(response: Any?) {
            val protocolResponse = KavaReflector.readField(response, "b")
            val protobuf = KavaReflector.readField(protocolResponse, "a")
            val feedList = KavaReflector.readField(protobuf, "f") as? Iterable<*> ?: return
            sawResponse = true
            synchronized(idLock) {
                feedList.forEach { feed ->
                    val rawId = KavaReflector.readField(feed, "Id") as? Number ?: return@forEach
                    val snsId = rawId.toLong()
                    if (snsId != 0L) ids += java.lang.Long.toUnsignedString(snsId)
                }
            }
        }

        fun complete(scene: Any, errType: Int, errCode: Int, errorMessage: String) {
            val serverIds = synchronized(idLock) { ids.toList() }
            val responseCount = (KavaReflector.invokeMethod(scene, "l") as? Number)?.toInt() ?: serverIds.size
            val downAll = KavaReflector.invokeMethod(scene, "i") as? Boolean
            completion = NativeSnsPageCallback(
                serverIds = serverIds,
                responseCaptured = sawResponse,
                responseCount = responseCount,
                downAll = downAll,
                errType = errType,
                errCode = errCode,
                errorMessage = errorMessage
            )
            latch.countDown()
        }

        fun await(timeoutMs: Long): NativeSnsPageCallback? =
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) completion else null
    }

    private class DeleteTicket {
        private val latch = CountDownLatch(1)
        @Volatile private var result: NativeSnsDeleteCallback? = null

        fun complete(errType: Int, errCode: Int, errorMessage: String) {
            val kind = when {
                errType == 0 && errCode == 0 -> NativeSnsDeleteCallbackKind.SERVER_ACCEPTED
                errType == 0 -> NativeSnsDeleteCallbackKind.SERVER_REJECTED
                else -> NativeSnsDeleteCallbackKind.SUBMITTED_UNCONFIRMED
            }
            result = NativeSnsDeleteCallback(kind, errType, errCode, errorMessage)
            latch.countDown()
        }

        fun await(timeoutMs: Long): NativeSnsDeleteCallback? =
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) result else null
    }

    private val pageTickets = ConcurrentHashMap<String, PageTicket>()
    private val deleteTickets = Collections.synchronizedMap(IdentityHashMap<Any, DeleteTicket>())
    private val hookedMethods = ConcurrentHashMap.newKeySet<Method>()
    @Volatile private var pageHooksReady = false
    @Volatile private var deleteHookReady = false

    fun requestPage(
        userName: String,
        timeoutMs: Long,
        submit: () -> Boolean
    ): NativeSnsPageWaitResult {
        if (!installPageHooks()) return NativeSnsPageWaitResult.Failed("朋友圈历史分页观察入口未就绪")
        val ticket = PageTicket(userName)
        if (pageTickets.putIfAbsent(userName, ticket) != null) {
            return NativeSnsPageWaitResult.Failed("该账号已有朋友圈历史请求正在执行")
        }
        try {
            val started = runCatching(submit).getOrElse {
                logger("提交朋友圈历史请求失败: ${it.message}")
                false
            }
            if (!started) return NativeSnsPageWaitResult.NotStarted("微信未接受本次朋友圈分页请求")
            val callback = ticket.await(timeoutMs)
                ?: return NativeSnsPageWaitResult.TimedOut
            return NativeSnsPageWaitResult.Completed(callback)
        } finally {
            pageTickets.remove(userName, ticket)
        }
    }

    fun requestDelete(
        request: Any,
        timeoutMs: Long,
        submit: () -> Boolean
    ): NativeSnsDeleteWaitResult {
        if (!installDeleteHook()) return NativeSnsDeleteWaitResult.Failed("微信原生朋友圈删除回调未就绪")
        val ticket = DeleteTicket()
        synchronized(deleteTickets) {
            deleteTickets[request] = ticket
        }
        try {
            val started = runCatching(submit).getOrElse {
                logger("提交微信原生朋友圈删除请求失败: ${it.message}")
                false
            }
            if (!started) return NativeSnsDeleteWaitResult.NotSubmitted
            val callback = ticket.await(timeoutMs) ?: return NativeSnsDeleteWaitResult.TimedOut
            return NativeSnsDeleteWaitResult.Callback(callback)
        } finally {
            synchronized(deleteTickets) { deleteTickets.remove(request) }
        }
    }

    @Synchronized
    private fun installPageHooks(): Boolean {
        if (pageHooksReady) return true
        val sceneClass = KavaReflector.loadClass(USER_PAGE_SCENE_CLASS, classLoader) ?: return false
        val serverClass = KavaReflector.loadClass(SNS_SERVER_CLASS, classLoader) ?: return false
        val netResponseClass = KavaReflector.loadClass(NET_RESPONSE_CLASS, classLoader) ?: return false
        val errorSceneClass = KavaReflector.loadClass(ERROR_SCENE_CLASS, classLoader) ?: return false
        val onNetworkEnd = KavaReflector.findMethodRecursive(
            sceneClass,
            "onGYNetEnd",
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
            netResponseClass,
            ByteArray::class.java
        ) ?: return false
        val onSceneEnd = KavaReflector.findMethodRecursive(
            serverClass,
            "onSceneEnd",
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
            errorSceneClass
        ) ?: return false

        val hookedNetwork = hookOnce(onNetworkEnd) { chain ->
            val scene = chain.thisObject
            val userName = (KavaReflector.invokeMethod(scene, "getUserName") as? String)
                ?.trim()
                .orEmpty()
            val ticket = pageTickets[userName]
            if (ticket != null) {
                runCatching { ticket.capture(chain.args?.getOrNull(4)) }
                    .onFailure { logger("解析朋友圈服务器分页响应失败: ${it.message}") }
            }
            chain.proceed()
        }
        val hookedCallback = hookOnce(onSceneEnd) { chain ->
            val hostResult = chain.proceed()
            runCatching {
                val args = chain.args ?: return@runCatching
                val scene = args.getOrNull(3) ?: return@runCatching
                if (scene.javaClass.name != USER_PAGE_SCENE_CLASS) return@runCatching
                val userName = (KavaReflector.invokeMethod(scene, "getUserName") as? String)
                    ?.trim()
                    .orEmpty()
                val ticket = pageTickets[userName] ?: return@runCatching
                ticket.complete(
                    scene = scene,
                    errType = (args.getOrNull(0) as? Number)?.toInt() ?: UNKNOWN_NATIVE_CODE,
                    errCode = (args.getOrNull(1) as? Number)?.toInt() ?: UNKNOWN_NATIVE_CODE,
                    errorMessage = args.getOrNull(2)?.toString().orEmpty()
                )
            }.onFailure { logger("读取朋友圈分页完成状态失败: ${it.message}") }
            hostResult
        }
        pageHooksReady = hookedNetwork && hookedCallback
        if (!pageHooksReady) logger("朋友圈历史分页Hook安装不完整")
        return pageHooksReady
    }

    @Synchronized
    private fun installDeleteHook(): Boolean {
        if (deleteHookReady) return true
        val sceneClass = KavaReflector.loadClass(DELETE_SCENE_CLASS, classLoader) ?: return false
        val netResponseClass = KavaReflector.loadClass(NET_RESPONSE_CLASS, classLoader) ?: return false
        val method = KavaReflector.findMethodRecursive(
            sceneClass,
            "onGYNetEnd",
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
            netResponseClass,
            ByteArray::class.java
        ) ?: return false
        deleteHookReady = hookOnce(method) { chain ->
            val hostResult = chain.proceed()
            runCatching {
                val scene = chain.thisObject
                val operation = (KavaReflector.invokeMethod(scene, "L") as? Number)?.toInt()
                if (operation != DELETE_OWN_POST_OPERATION) return@runCatching
                val ticket = synchronized(deleteTickets) { deleteTickets[scene] } ?: return@runCatching
                val args = chain.args ?: return@runCatching
                ticket.complete(
                    errType = (args.getOrNull(1) as? Number)?.toInt() ?: UNKNOWN_NATIVE_CODE,
                    errCode = (args.getOrNull(2) as? Number)?.toInt() ?: UNKNOWN_NATIVE_CODE,
                    errorMessage = args.getOrNull(3)?.toString().orEmpty()
                )
            }.onFailure { logger("读取微信原生朋友圈删除结果失败: ${it.message}") }
            hostResult
        }
        if (!deleteHookReady) logger("微信原生朋友圈删除Hook安装失败")
        return deleteHookReady
    }

    private fun hookOnce(method: Method, callback: XposedInterface.Hooker): Boolean {
        if (method in hookedMethods) return true
        return runCatching {
            HookRegistry.get().hook(KavaReflector.accessible(method) ?: method, callback)
            hookedMethods += method
            true
        }.onFailure {
            logger("安装微信朋友圈场景Hook失败: ${method.toGenericString()} ${it.message}")
        }.getOrDefault(false)
    }

    companion object {
        const val USER_PAGE_SCENE_CLASS = "com.tencent.mm.plugin.sns.model.r3"
        const val DELETE_SCENE_CLASS = "com.tencent.mm.plugin.sns.model.r2"
        const val DELETE_OWN_POST_OPERATION = 1
        private const val SNS_SERVER_CLASS = "com.tencent.mm.plugin.sns.model.h6"
        private const val NET_RESPONSE_CLASS = "com.tencent.mm.network.y0"
        private const val ERROR_SCENE_CLASS = "com.tencent.mm.modelbase.n1"
        private const val UNKNOWN_NATIVE_CODE = Int.MIN_VALUE
    }
}
