package h.Hchat.hooks.api.sns

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector as R
import java.util.concurrent.Executor

/** Reuse the host's thumbnail memory/disk cache, decoder and CDN pipeline. Main thread only. */
internal class SnsOwnCoverLoader(private val loader: ClassLoader) {
    private val scheduled = LruCache<String, Long>(160)

    fun cached(media: Any): Bitmap? {
        val core = R.loadClass("com.tencent.mm.plugin.sns.model.p4", loader) ?: return null
        val images = R.invokeStaticMethod(core, "Rj") ?: return null
        return (R.invokeMethod(images, "s", media) as? Bitmap)?.takeUnless { it.isRecycled }
    }

    fun bind(media: Any, view: ImageView, video: Boolean): (() -> Unit)? {
        val core = R.loadClass("com.tencent.mm.plugin.sns.model.p4", loader) ?: return null
        val images = R.invokeStaticMethod(core, "Rj") ?: return null
        val sceneType = R.loadClass("com.tencent.mm.storage.s7", loader) ?: return null
        val scene = R.invokeStaticMethod(sceneType, if (video) "c" else "d") ?: return null
        val release = R.findMethodRecursive(images.javaClass, "P", View::class.java) ?: return null
        val kind = if (video) "video" else "image"
        val bound = runCatching {
            val active = R.readField(images, "l") as? Boolean ?: return@runCatching false
            val viewOwner = view.context.hashCode()
            if (active) {
                val args = arrayOf(media, view, NO_RESOURCE_PLACEHOLDER, viewOwner, scene)
                val bind = R.findCompatibleMethod(images.javaClass, "l0", *args) ?: return@runCatching false
                R.invokeOrThrow(bind, images, *args)
            } else {
                // I() would cancel the download while the native timeline is inactive.
                // Register this view and dispatch the exact thumbnail worker used by I(),
                // without changing the global loader's paused/running state.
                val mediaId = (R.readField(media, "d") as? String)?.takeIf(String::isNotBlank) ?: return@runCatching false
                val helpers = R.loadClass("com.tencent.mm.plugin.sns.data.a1", loader) ?: return@runCatching false
                val key = R.invokeStaticMethod(helpers, "x", THUMBNAIL, mediaId) as? String ?: return@runCatching false
                val register = R.findCompatibleMethod(images.javaClass, "s0", view, key, true, NO_RESOURCE_PLACEHOLDER, viewOwner)
                    ?: return@runCatching false
                R.invokeOrThrow(register, images, view, key, true, NO_RESOURCE_PLACEHOLDER, viewOwner)
                val bitmap = R.invokeMethod(images, "k", key, mediaId)
                if (bitmap != null && R.invokeStaticMethod(helpers, "g", bitmap) == true) {
                    R.invokeMethod(images, "R", view, key, bitmap, false)
                } else {
                    val now = SystemClock.elapsedRealtime()
                    if (scheduled.get(key)?.let { now - it < THUMBNAIL_REBIND_WINDOW_MS } != true) {
                        val workerType = R.loadClass("com.tencent.mm.plugin.sns.model.y1", loader)
                        val ctor = R.findConstructor(workerType, images.javaClass, media.javaClass,
                            Integer.TYPE, sceneType, String::class.java)
                        val worker = R.newInstance(ctor, images, media, THUMBNAIL, scene, "") as? Runnable
                            ?: return@runCatching false
                        val executor = R.invokeStaticMethod(core, "zk") as? Executor ?: return@runCatching false
                        executor.execute(worker)
                        scheduled.put(key, now)
                    }
                }
            }
            true
        }.onFailure {
            HLog.i("sns_r1 phase=cover kind=$kind stage=bind_error type=${(it.cause ?: it).javaClass.simpleName}")
        }.getOrDefault(false)
        if (!bound) {
            R.invoke(release, images, view)
            HLog.i("sns_r1 phase=cover kind=$kind stage=placeholder")
            return null
        }
        return { R.invoke(release, images, view); Unit }
    }

    companion object {
        // u0 skips the resource lookup only for -1, not for 0 on Android ImageView.
        private const val NO_RESOURCE_PLACEHOLDER = -1
        private const val THUMBNAIL = 1
        private const val THUMBNAIL_REBIND_WINDOW_MS = 15_000L
    }
}
