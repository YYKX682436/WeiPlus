package h.Hchat.hooks.api.sns

import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class NativeSnsFeed(val id: Long, val author: String, val time: Long)

internal data class SnsObjectEvidence(
    val likes: SnsLikeEvidence?,
    val contentPresent: Boolean
)

internal data class NativeSnsPage(
    val feeds: List<NativeSnsFeed>,
    val count: Int,
    val continuation: Long,
    val ret: Int,
    val lastId: Long = 0,
    val downAll: Boolean = false,
    val errType: Int = -1,
    val errCode: Int = -1,
    val evidence: Map<Long, SnsObjectEvidence> = emptyMap(),
    val recentLimitId: Long = 0,
    val privacy: Boolean = false
)

internal enum class SnsDetailState { OWN_PRESENT, OTHER_PRESENT, UNAVAILABLE, UNKNOWN }

internal data class SnsSceneResponse(
    val errType: Int,
    val errCode: Int,
    val page: NativeSnsPage? = null,
    val detail: NativeSnsFeed? = null,
    val unavailableReason: Int = 0,
    val detailEvidence: SnsObjectEvidence? = null,
    val protoRet: Int? = null
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

internal class SnsNativeSceneObserver(
    private val loader: ClassLoader,
    private val withoutPostDispatch: ((() -> Any?) -> Any?)
) {
    private class Ticket {
        val ready = CountDownLatch(1)
        @Volatile var response: SnsSceneResponse? = null
    }

    private val tickets = SnsSceneTickets<Ticket>()
    private val hooked = HashSet<Class<*>>()
    private val quiet = ArrayList<WeakReference<Any>>()

    @Synchronized
    private fun markQuiet(scene: Any) {
        quiet.removeAll { it.get() == null }
        quiet += WeakReference(scene)
    }

    @Synchronized
    private fun takeQuiet(scene: Any?): Boolean {
        var matched = false
        quiet.removeAll {
            val value = it.get()
            if (value != null && value === scene) matched = true
            value == null || value === scene
        }
        return matched
    }

    fun request(scene: Any, timeoutMs: Long, quietRead: Boolean = false, submit: () -> Boolean): SnsSceneResult =
        requestBuilt(scene.javaClass, timeoutMs, quietRead) { bind -> bind(scene) && submit() }

    /** A builder may bind its scene synchronously inside the native enqueue call. */
    fun requestBuilt(
        type: Class<*>, timeoutMs: Long, quietRead: Boolean,
        submit: ((Any) -> Boolean) -> Boolean
    ): SnsSceneResult {
        if (!install(type)) return SnsSceneResult.NotSubmitted("hook")
        val ticket = Ticket()
        var scene: Any? = null
        try {
            // Reflection can throw after dispatch. Treat that case as unknown and never resubmit.
            val queued = try {
                submit { actual ->
                    if (scene != null || actual.javaClass != type) false else {
                        scene = actual
                        tickets.put(actual, ticket)
                        if (quietRead) markQuiet(actual)
                        true
                    }
                }
            } catch (_: Exception) { return SnsSceneResult.Unknown }
            if (!queued) return SnsSceneResult.NotSubmitted("queue")
            if (!ticket.ready.await(timeoutMs, TimeUnit.MILLISECONDS)) return SnsSceneResult.Unknown
            return ticket.response?.let(SnsSceneResult::Completed) ?: SnsSceneResult.Unknown
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return SnsSceneResult.Unknown
        } finally {
            scene?.let(tickets::remove)
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
                val quietCallback = takeQuiet(scene)
                if (ticket == null) {
                    return@hook if (quietCallback) withoutPostDispatch { chain.proceed() } else chain.proceed()
                }
                val args = chain.args
                val errType = (args[1] as? Number)?.toInt() ?: -1
                val errCode = (args[2] as? Number)?.toInt() ?: -1
                val raw = runCatching { capture(type.name, args[4], errType, errCode, quietCallback) }.getOrNull()
                val result = if (quietCallback) withoutPostDispatch { chain.proceed() } else chain.proceed()
                try {
                    ticket.response = runCatching { raw?.let {
                        if (it.page == null) it else it.copy(page = it.page.copy(
                            lastId = (KavaReflector.invokeMethod(scene, "D") as? Number)?.toLong() ?: 0,
                            downAll = KavaReflector.invokeMethod(scene, "i") == true,
                            privacy = quietCallback && KavaReflector.invokeMethod(scene, "q") == true
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

    private fun capture(type: String, rr: Any?, errType: Int, errCode: Int, withLikes: Boolean): SnsSceneResponse {
        val response = SnsSceneResponse(errType, errCode)
        if (type == DELETE) return response
        val wrapper = KavaReflector.readField(rr, "b")
        val proto = KavaReflector.readField(wrapper, "a")
        val ret = (KavaReflector.invokeMethod(wrapper, "getRetCode") as? Number)?.toInt()
        if (type == DETAIL || type == LIKE) {
            val obj = KavaReflector.readField(proto, "d")
            return response.copy(
                detail = feed(obj),
                unavailableReason = if (type == DETAIL) (KavaReflector.readField(proto, "e") as? Number)?.toInt() ?: 0 else 0,
                detailEvidence = if (withLikes) obj?.let(::snsObjectEvidence) else null,
                protoRet = ret
            )
        }
        val objects = KavaReflector.readField(proto, "f") as? List<*> ?: return response
        val count = (KavaReflector.readField(proto, "e") as? Number)?.toInt() ?: return response
        val continuation = (KavaReflector.readField(proto, "p") as? Number)?.toLong() ?: return response
        val recentLimitId = (KavaReflector.readField(proto, "o") as? Number)?.toLong() ?: 0L
        val evidence = if (withLikes) objects.filterNotNull().mapNotNull { obj ->
            feed(obj)?.let { it to snsObjectEvidence(obj) }
        }.groupBy { it.first.id }.mapNotNull { (id, entries) ->
            val first = entries.first()
            // Contradictory copies cannot provide identity or a selectable like state.
            if (entries.any { it.first != first.first }) null
            else id to first.second.copy(likes = first.second.likes.takeIf { entries.all { it.second == first.second } })
        }.toMap() else emptyMap()
        return response.copy(page = NativeSnsPage(objects.mapNotNull(::feed), count, continuation, ret ?: return response,
            errType = errType, errCode = errCode, evidence = evidence, recentLimitId = recentLimitId))
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
        const val LIKE = "com.tencent.mm.plugin.sns.model.o2"
        const val DELETE = "com.tencent.mm.plugin.sns.model.r2"
    }
}

internal fun snsObjectEvidence(obj: Any): SnsObjectEvidence {
    val noChange = KavaReflector.readField(obj, "NoChange") as? Number
    val desc = KavaReflector.readField(obj, "ObjectDesc")
    val descBytes = KavaReflector.readField(KavaReflector.readField(desc, "f"), "a") as? ByteArray
    val contentPresent = noChange?.toInt() == 0 && descBytes != null && descBytes.isNotEmpty()
    val flag = (KavaReflector.readField(obj, "LikeFlag") as? Number)?.toInt()
    val list = KavaReflector.readField(obj, "LikeUserList") as? List<*>
    val users = list?.map { KavaReflector.readField(it, "d") as? String }
    val likes = if (noChange?.toInt() == 0 && flag != null && users != null && users.none { it.isNullOrBlank() }) {
        SnsLikeEvidence(flag, users.filterNotNull().toSet())
    } else null
    return SnsObjectEvidence(likes, contentPresent)
}
