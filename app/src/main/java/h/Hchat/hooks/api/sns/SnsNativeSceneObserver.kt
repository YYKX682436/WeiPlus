package h.Hchat.hooks.api.sns

import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class NativeSnsFeed(val id: Long, val author: String, val time: Long)

internal data class NativeSnsPage(
    val feeds: List<NativeSnsFeed>,
    val count: Int,
    val continuation: Long,
    val ret: Int,
    val lastId: Long = 0,
    val downAll: Boolean = false,
    val errType: Int = -1,
    val errCode: Int = -1
)

internal enum class SnsDetailState { OWN_PRESENT, OTHER_PRESENT, UNAVAILABLE, UNKNOWN }

internal data class SnsSceneResponse(
    val errType: Int,
    val errCode: Int,
    val page: NativeSnsPage? = null,
    val detail: NativeSnsFeed? = null,
    val unavailableReason: Int = 0
)

internal sealed interface SnsSceneResult {
    data class Completed(val response: SnsSceneResponse) : SnsSceneResult
    data class NotSubmitted(val stage: String) : SnsSceneResult
    data object Unknown : SnsSceneResult
}

/** Bind before submission; a host scene for the same author can never complete our ticket. */
internal class SnsSceneTickets<T : Any> {
    private val active = IdentityHashMap<Any, T>()
    @Synchronized fun put(scene: Any, value: T) { active[scene] = value }
    @Synchronized fun get(scene: Any?): T? = active[scene]
    @Synchronized fun remove(scene: Any) { active.remove(scene) }
}

internal class SnsNativeSceneObserver(private val loader: ClassLoader) {
    private class Ticket {
        val ready = CountDownLatch(1)
        @Volatile var response: SnsSceneResponse? = null
    }

    private val tickets = SnsSceneTickets<Ticket>()
    private val hooked = HashSet<Class<*>>()

    fun request(scene: Any, timeoutMs: Long, submit: () -> Boolean): SnsSceneResult {
        if (!install(scene.javaClass)) return SnsSceneResult.NotSubmitted("hook")
        val ticket = Ticket()
        tickets.put(scene, ticket)
        try {
            // Reflection can throw after dispatch. Treat that case as unknown and never resubmit.
            val queued = try { submit() } catch (_: Exception) { return SnsSceneResult.Unknown }
            if (!queued) return SnsSceneResult.NotSubmitted("queue")
            if (!ticket.ready.await(timeoutMs, TimeUnit.MILLISECONDS)) return SnsSceneResult.Unknown
            return ticket.response?.let(SnsSceneResult::Completed) ?: SnsSceneResult.Unknown
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return SnsSceneResult.Unknown
        } finally {
            tickets.remove(scene)
        }
    }

    @Synchronized
    private fun install(type: Class<*>): Boolean {
        if (type in hooked) return true
        val rrType = KavaReflector.loadClass("com.tencent.mm.network.y0", loader) ?: return false
        val method = KavaReflector.findMethodRecursive(
            type, "onGYNetEnd", Integer.TYPE, Integer.TYPE, Integer.TYPE,
            String::class.java, rrType, ByteArray::class.java
        ) ?: return false
        return runCatching {
            HookRegistry.get().hook(method, callback = { chain ->
                val scene = chain.thisObject
                val ticket = tickets.get(scene)
                if (ticket == null) return@hook chain.proceed()
                val args = chain.args
                val errType = (args[1] as? Number)?.toInt() ?: -1
                val errCode = (args[2] as? Number)?.toInt() ?: -1
                val raw = runCatching { capture(type.name, args[4], errType, errCode) }.getOrNull()
                val result = chain.proceed()
                try {
                    ticket.response = runCatching { raw?.let {
                        if (it.page == null) it else it.copy(page = it.page.copy(
                            lastId = (KavaReflector.invokeMethod(scene, "D") as? Number)?.toLong() ?: 0,
                            downAll = KavaReflector.invokeMethod(scene, "i") == true
                        ))
                    } }.getOrNull()
                } finally {
                    ticket.ready.countDown()
                }
                result
            })
            hooked += type
            true
        }.getOrDefault(false)
    }

    private fun capture(type: String, rr: Any?, errType: Int, errCode: Int): SnsSceneResponse {
        val response = SnsSceneResponse(errType, errCode)
        if (type == DELETE) return response
        val wrapper = KavaReflector.readField(rr, "b")
        val proto = KavaReflector.readField(wrapper, "a")
        if (type == DETAIL) return response.copy(
            detail = feed(KavaReflector.readField(proto, "d")),
            unavailableReason = (KavaReflector.readField(proto, "e") as? Number)?.toInt() ?: 0
        )
        val objects = KavaReflector.readField(proto, "f") as? List<*> ?: return response
        val count = (KavaReflector.readField(proto, "e") as? Number)?.toInt() ?: return response
        val continuation = (KavaReflector.readField(proto, "p") as? Number)?.toLong() ?: return response
        val ret = (KavaReflector.invokeMethod(wrapper, "getRetCode") as? Number)?.toInt() ?: return response
        return response.copy(page = NativeSnsPage(objects.mapNotNull(::feed), count, continuation, ret,
            errType = errType, errCode = errCode))
    }

    private fun feed(obj: Any?): NativeSnsFeed? {
        val id = (KavaReflector.readField(obj, "Id") as? Number)?.toLong() ?: return null
        if (id == 0L) return null
        val author = KavaReflector.readField(obj, "Username") as? String ?: ""
        val time = (KavaReflector.readField(obj, "CreateTime") as? Number)?.toInt() ?: 0
        return NativeSnsFeed(id, author, Integer.toUnsignedLong(time))
    }

    companion object {
        const val PAGE = "com.tencent.mm.plugin.sns.model.r3"
        const val DETAIL = "com.tencent.mm.plugin.sns.model.q2"
        const val DELETE = "com.tencent.mm.plugin.sns.model.r2"
    }
}
