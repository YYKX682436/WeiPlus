package h.Hchat.hooks.api.sns

import h.Hchat.utils.KavaReflector as R

/** Exact 8.0.80/3220 network scenes; keep the native queue, parser and storage callbacks. */
internal class SnsOwnTimelineNative(private val loader: ClassLoader) {
    private val observer = SnsNativeSceneObserver(loader)
    private val ints = Integer.TYPE

    fun page(owner: String, cursor: Long, guard: () -> Boolean): SnsSceneResult {
        val type = R.loadClass(SnsNativeSceneObserver.PAGE, loader) ?: return unavailable("page_class")
        val constructor = R.findConstructor(type, String::class.java, Long::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!, ints, ints, ints, ints, ints, ints)
        // c6.run/h6.f normal user page. D=11 uses r3's native continuation ceiling:
        // process this response normally, then let our session consume raw ContinueID itself.
        val scene = R.newInstance(constructor, owner, cursor, true, 0, -1, 0, 4, 2, 11)
            ?: return unavailable("page_constructor")
        val request = R.readField(R.readField(R.readField(scene, "d"), "a"), "a")
        // An explicit fresh read: don't let cached FirstPageMd5/MinId/LastRequestTime
        // turn a response into a cache delta. Do not modify SnsExtStorage.
        if (!R.writeField(request, "d", "") || !R.writeField(scene, "p", "") ||
            !R.writeField(request, "h", 0L) || !R.writeField(request, "i", 0) ||
            (R.readField(request, "f") as? Number)?.toLong() != cursor) return unavailable("page_request")
        var reserved = false
        val result = observer.request(scene, READ_TIMEOUT_MS) {
            if (!guard()) return@request false
            reserved = R.invokeStaticMethod(type, "H", owner) == true
            reserved && guard() && sendOnce(scene)
        }
        if (reserved && result is SnsSceneResult.NotSubmitted) R.invokeStaticMethod(type, "L", owner)
        // A timed-out scene still owns its native request guard until the host completes it.
        return result
    }

    fun detail(id: Long, guard: () -> Boolean): SnsSceneResult {
        val type = R.loadClass(SnsNativeSceneObserver.DETAIL, loader) ?: return unavailable("detail_class")
        val outstanding = R.readField(field = R.findField(type, "m"), receiver = null) as? Collection<*>
            ?: return unavailable("detail_guard")
        if (id in outstanding) return unavailable("detail_busy")
        val ctor = R.findConstructor(type, Long::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
        // Both view-count flags are off. The host can still refresh its local cache.
        val scene = R.newInstance(ctor, id, false, false) ?: return unavailable("detail_constructor")
        return observer.request(scene, READ_TIMEOUT_MS) { guard() && sendOnce(scene) }
    }

    fun delete(id: Long, guard: () -> Boolean, reserve: () -> Boolean): SnsSceneResult {
        val type = R.loadClass(SnsNativeSceneObserver.DELETE, loader) ?: return unavailable("delete_class")
        val scene = R.newInstance(R.findConstructor(type, Long::class.javaPrimitiveType!!, ints), id, 1)
            ?: return unavailable("delete_constructor")
        return observer.request(scene, DELETE_TIMEOUT_MS) {
            guard() && hasNativePendingDelete(id) == false && reserve() && guard() && sendOnce(scene)
        }
    }

    fun hasNativePendingDelete(id: Long): Boolean? {
        val core = R.loadClass("com.tencent.mm.plugin.sns.model.p4", loader) ?: return null
        val queue = R.invokeStaticMethod(core, "ek") ?: return null
        return synchronized(queue) {
            val items = R.readField(R.readField(queue, "a"), "h") as? Iterable<*> ?: return@synchronized null
            for (entry in items) {
                val pendingId = (R.readField(entry, "d") as? Number)?.toLong() ?: return@synchronized null
                if (pendingId == id) return@synchronized true
            }
            false
        }
    }

    private fun sendOnce(scene: Any): Boolean {
        val kernel = R.loadClass("zp0.j1", loader) ?: return false
        R.invokeStaticMethod(kernel, "j") ?: return false
        val queue = R.readField(R.invokeStaticMethod(kernel, "q"), "b") ?: return false
        val method = R.findCompatibleMethod(queue.javaClass, "g", scene) ?: return false
        // Do not use the generic dispatcher fallback: an exception after enqueue is ambiguous.
        return R.invokeOrThrow(method, queue, scene) == true
    }

    private fun unavailable(stage: String) = SnsSceneResult.NotSubmitted(stage)

    companion object {
        private const val READ_TIMEOUT_MS = 18_000L
        private const val DELETE_TIMEOUT_MS = 45_000L
    }
}
