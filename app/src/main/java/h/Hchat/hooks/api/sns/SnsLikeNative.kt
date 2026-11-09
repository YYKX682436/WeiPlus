package h.Hchat.hooks.api.sns

import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.utils.KavaReflector as R

/** 3220: h6.m builds the native request; o2 owns the network response and storage update. */
internal class SnsLikeNative(
    private val loader: ClassLoader,
    private val observer: SnsNativeSceneObserver,
    private val enqueue: (Any) -> Boolean
) {
    private class Build(val feed: NativeSnsFeed, val self: String, val source: Int) {
        var observed = false
        var action: Any? = null
        var tag: String? = null
    }

    private val active = ThreadLocal<Build>()
    private val registry get() = HookRegistry.get()
    @Volatile private var installedGeneration = -1L

    private val service by lazy { R.loadClass("com.tencent.mm.plugin.sns.model.h6", loader) }
    private val core by lazy { R.loadClass("com.tencent.mm.plugin.sns.model.p4", loader) }
    private val objectConstructor by lazy {
        R.loadClass("com.tencent.mm.protocal.protobuf.SnsObject", loader)?.let { R.findConstructor(it) }
    }

    fun cachedState(feed: NativeSnsFeed, info: Any, self: String, server: SnsLikeState): SnsLikeState {
        val flag = (R.invokeMethod(info, "getLikeFlag") as? Number)?.toInt()
        val bytes = R.readField(info, "field_attrBuf") as? ByteArray ?: return SnsLikeState.UNKNOWN
        val raw = R.newInstance(objectConstructor) ?: return SnsLikeState.UNKNOWN
        val proto = R.invokeMethod(raw, "parseFrom", bytes) ?: return SnsLikeState.UNKNOWN
        if (R.readField(proto, "Id") != feed.id || R.readField(proto, "Username") != feed.author ||
            (R.readField(proto, "CreateTime") as? Int)?.let(Integer::toUnsignedLong) != feed.time
        ) return SnsLikeState.UNKNOWN
        val stored = snsObjectEvidence(proto).likes?.state(self) ?: SnsLikeState.UNKNOWN
        return verifiedLikeState(server, stored, flag, pending(feed.id))
    }

    private fun pending(id: Long): Boolean? =
        R.invokeStaticMethod(core, "ek")?.let { synchronized(it) { pendingLocked(it, id) } }

    private fun pendingLocked(queue: Any, id: Long): Boolean? {
        val proto = R.readField(queue, "a") ?: return null
        val likes = R.readField(proto, "d") as? List<*> ?: return null
        val unlikes = R.readField(proto, "i") as? List<*> ?: return null
        for (item in likes) {
            val target = R.readField(R.readField(item, "e"), "d") as? Long ?: return null
            if (target == id) return true
        }
        for (item in unlikes) {
            val target = R.readField(item, "d") as? Long ?: return null
            if (target == id) return true
        }
        return false
    }

    fun submit(
        feed: NativeSnsFeed, self: String, info: Any, source: Int, guard: () -> Boolean,
        fresh: () -> Boolean, reserve: () -> Boolean
    ): SnsSceneResult {
        val snsInfo = R.loadClass(SnsInteractionLocator.SNS_INFO_CLASS, loader)
            ?: return SnsSceneResult.NotSubmitted("like_class")
        val actionType = R.loadClass("gd5.tf6", loader) ?: return SnsSceneResult.NotSubmitted("like_payload")
        val payloadType = R.loadClass("ek4.a", loader) ?: return SnsSceneResult.NotSubmitted("like_payload")
        val service = service ?: return SnsSceneResult.NotSubmitted("like_service")
        val entry = R.findMethodRecursive(service, "m", snsInfo, Integer.TYPE, payloadType, Integer.TYPE)
            ?: return SnsSceneResult.NotSubmitted("like_entry")
        val type = R.loadClass(SnsNativeSceneObserver.LIKE, loader)
            ?: return SnsSceneResult.NotSubmitted("like_scene")
        val ctor = R.findConstructor(type, actionType, String::class.java, Integer.TYPE)
            ?: return SnsSceneResult.NotSubmitted("like_constructor")
        if (!install(service, snsInfo, payloadType, actionType)) {
            return SnsSceneResult.NotSubmitted("like_builder_hook")
        }
        return observer.requestBuilt(type, 30_000L, quietRead = true) { bind ->
            if (!guard() || !fresh() || active.get() != null ||
                !registry.isInstallationGenerationActive(installedGeneration) || !reserve() || !guard()
            ) return@requestBuilt false

            val build = Build(feed, self, source)
            active.set(build)
            try {
                R.invokeOrThrow(entry, null, info, 1, null, source)
            } finally {
                active.remove()
            }
            // A missing hook is ambiguous: never try a second send or clear the attempt record.
            check(build.observed) { "SNS like builder did not reach the scoped queue boundary" }
            val action = build.action ?: return@requestBuilt false
            val tag = build.tag ?: return@requestBuilt false
            val scene = R.newInstance(ctor, action, tag, source) ?: return@requestBuilt false
            bind(scene) && guard() && fresh() && pending(feed.id) == false && guard() && enqueue(scene)
        }
    }

    @Synchronized
    private fun install(service: Class<*>, snsInfo: Class<*>, payload: Class<*>, action: Class<*>): Boolean {
        val generation = registry.installationGeneration()
        if (installedGeneration == generation) return true
        val async = R.loadClass("com.tencent.mm.plugin.sns.model.m4", loader) ?: return false
        val node = R.loadClass("gd5.mg6", loader) ?: return false
        val add = R.findMethodRecursive(async, "a", String::class.java, action, Integer.TYPE) ?: return false
        val caller = R.findMethodRecursive(service, "n", snsInfo, Integer.TYPE, payload, node,
            java.lang.Boolean.TYPE, Integer.TYPE, Integer.TYPE) ?: return false
        return runCatching {
            registry.withInstallationGeneration(generation) {
                registry.hook(add, callback = { chain ->
                    val build = active.get() ?: return@hook chain.proceed()
                    if (build.observed) return@hook false
                    build.observed = true
                    val owner = chain.thisObject ?: return@hook false
                    val tag = chain.args[0] as? String ?: return@hook false
                    val interaction = chain.args[1] ?: return@hook false
                    if (tag.isNotBlank() && !tag.startsWith("_AD_TAG_") && chain.args[2] == build.source &&
                        matchesAction(build, interaction) &&
                        synchronized(owner) { pendingLocked(owner, build.feed.id) == false }
                    ) {
                        build.action = interaction
                        build.tag = tag
                    }
                    // Returning false makes h6.n skip both native queue registration and its send.
                    // The module submits the same native o2 instance after binding its callback.
                    false
                })
                // m4.a is a tiny wrapper; protect this safety boundary against ART inlining.
                check(XposedRuntime.get().deoptimize(caller))
            }
            installedGeneration = generation
            true
        }.getOrDefault(false)
    }

    private fun matchesAction(build: Build, action: Any): Boolean {
        val interaction = R.readField(action, "f") ?: return false
        return R.readField(action, "d") == build.feed.id &&
            R.readField(interaction, "h") == 1 &&
            R.readField(interaction, "d") == build.self &&
            R.readField(interaction, "e") == build.feed.author
    }
}
