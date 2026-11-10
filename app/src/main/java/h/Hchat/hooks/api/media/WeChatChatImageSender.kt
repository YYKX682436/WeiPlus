package h.Hchat.hooks.api.media

import h.Hchat.dexkit.DexFinder
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicLong

/** Chat image fields and task events verified against the 8.0.80 (3220) APK. */
internal class WeChatChatImageSender private constructor(
    private val api: WeChatImageApi,
    private val params: Class<*>,
    private val cross: Class<*>,
    loader: ClassLoader
) {
    data class Live(val path: String, val durationMs: Int, val coverTimeMs: Long)
    data class Group(val id: String, val count: Int)
    data class Submission(val sendId: String, val invoke: Callable<Any?>)
    data class GifLimits(val bytes: Int, val edge: Int)

    private val live = loader.loadClass("qa0.j")
    private val group = loader.loadClass("ga5.f")
    private val common = loader.loadClass("ga5.d")
    private val crossCommon = field(cross, "t", common)
    private val mediaId = field(cross, "u", java.lang.Long.TYPE)
    private val crossLive = field(cross, "v", live)
    private val livePath = field(live, "a", String::class.java)
    private val liveDuration = field(live, "b", Integer.TYPE)
    private val liveCoverTime = field(live, "c", java.lang.Long.TYPE)
    private val groupId = method(group, "k", Void.TYPE, String::class.java)
    private val groupCount = method(group, "j", Void.TYPE, Integer.TYPE)
    private val groupType = method(group, "l", Void.TYPE, Integer.TYPE)
    private val putGroup = method(common, "p", Void.TYPE, group)
    private val batch = field(params, "g", String::class.java)
    private val index = field(params, "h", Integer.TYPE)
    private val sendId = field(params, "f", String::class.java)
    private val talker = field(params, "d", String::class.java)
    // Keep constructor defaults: raw image source and zero additional rotations.
    private val groupEligible = method(loader.loadClass("com.tencent.mm.storage.z3"), "h4", java.lang.Boolean.TYPE, String::class.java)
    private val emojiLimits = loader.loadClass("rp.c")
    private val event = loader.loadClass("ta0.c")
    private val phase = field(event, "b", loader.loadClass("ta0.d"))
    private val original = field(event, "a", params)
    private val localId = field(event, "c", java.lang.Long.TYPE)
    private val result = field(event, "i", loader.loadClass("ta0.h"))
    private val resultType = field(result.type, "a", loader.loadClass("ta0.i"))
    private val publish = method(loader.loadClass("gb0.r"), "P7", Void.TYPE, event)

    fun canGroup(talker: String): Boolean = KavaReflector.invokeOrThrow(groupEligible, null, talker) == true

    fun gifLimits() = GifLimits(positiveLimit("e", 10 * 1024 * 1024), positiveLimit("c", 1024))

    private fun positiveLimit(name: String, default: Int): Int =
        (KavaReflector.invoke(KavaReflector.findDeclaredMethod(emojiLimits, name), null) as? Int)
            ?.takeIf { it > 0 } ?: default

    fun prepare(
        to: String, from: String, path: String, batchId: String, position: Int,
        liveData: Live?, groupData: Group?
    ): Submission {
        check(from.isNotBlank())
        val extras = checkNotNull(api.newAsyncCrossParams())
        mediaId.setLong(extras, mediaIds.decrementAndGet())
        if (liveData != null) {
            require(liveData.coverTimeMs in 0 until liveData.durationMs.toLong())
            val value = make(live)
            livePath.set(value, liveData.path)
            liveDuration.setInt(value, liveData.durationMs)
            liveCoverTime.setLong(value, liveData.coverTimeMs)
            crossLive.set(extras, value)
        }
        if (groupData != null) {
            require(groupData.count >= 3)
            val value = make(group)
            KavaReflector.invokeOrThrow(groupId, value, groupData.id)
            KavaReflector.invokeOrThrow(groupCount, value, groupData.count)
            KavaReflector.invokeOrThrow(groupType, value, 1)
            val extCommon = make(common)
            KavaReflector.invokeOrThrow(putGroup, extCommon, value)
            crossCommon.set(extras, extCommon)
        }
        val request = checkNotNull(api.newAsyncParams(to, path, 1, from, extras))
        batch.set(request, batchId)
        index.setInt(request, position)
        val id = (sendId.get(request) as String).also { check(it.isNotBlank()) }
        return Submission(id, checkNotNull(api.prepareAsyncSubmit(request)))
    }

    fun observe(
        featureId: String,
        isPending: (String, String) -> Boolean,
        onEvent: (String, Long, String?) -> Unit
    ): XposedInterface.HookHandle = HookRegistry.get().withFeature(featureId) {
        HookRegistry.get().hookOnce("$featureId:chat_image_task", publish, XposedInterface.Hooker { chain ->
            val returned = chain.proceed()
            val value = chain.args.getOrNull(0)
            if (event.isInstance(value)) runCatching {
                val request = original.get(value)
                val id = sendId.get(request) as String
                if (isPending(id, talker.get(request) as String)) {
                    when ((phase.get(value) as Enum<*>).name) {
                        "Progress" -> localId.getLong(value).takeIf { it > 0 }?.let { onEvent(id, it, null) }
                        "Finish" -> {
                            val state = result.get(value)?.let { (resultType.get(it) as Enum<*>).name }
                            onEvent(id, localId.getLong(value), state ?: "Unknown")
                        }
                    }
                }
            }
            returned
        })
    }

    companion object {
        // MediaStore IDs are positive. Use a process-random negative range and a monotonic counter.
        private val mediaIds = AtomicLong(Long.MIN_VALUE / 2 + (UUID.randomUUID().leastSignificantBits ushr 17))

        fun locate(api: WeChatImageApi, dex: DexFinder, version: Long): WeChatChatImageSender? {
            if (version != 3220L || !api.canSendAsync()) return null
            val params = dex.sendImageAsyncParamsClass
            val cross = dex.sendImageCrossParamsClass
            val submit = dex.sendImageAsyncSubmitMethod
            if (params.name != "ta0.g" || cross.name != "ma0.i0" || submit.name != "Uj" ||
                submit.declaringClass.name != "xa0.w" || !submit.parameterTypes.contentEquals(arrayOf(params)) ||
                !submit.returnType.name.startsWith("kotlinx.coroutines.flow.")) return null
            return runCatching { WeChatChatImageSender(api, params, cross, checkNotNull(params.classLoader)) }.getOrNull()
        }

        private fun field(owner: Class<*>, name: String, type: Class<*>): Field =
            checkNotNull(KavaReflector.findFieldRecursive(owner, name)).also {
                check(it.type == type && !KavaReflector.isStatic(it))
            }

        private fun method(owner: Class<*>, name: String, returns: Class<*>, vararg types: Class<*>): Method =
            checkNotNull(KavaReflector.findDeclaredMethod(owner, name, *types)).also { check(it.returnType == returns) }

        private fun make(type: Class<*>): Any = checkNotNull(KavaReflector.newInstance(KavaReflector.findConstructor(type)))
    }
}
