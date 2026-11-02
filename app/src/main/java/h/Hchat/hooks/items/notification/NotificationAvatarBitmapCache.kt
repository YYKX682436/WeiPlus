package h.Hchat.hooks.items.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import h.Hchat.hooks.api.core.WeChatApis
import java.io.File
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Shared, byte-bounded notification avatar cache. Entries are never recycled on eviction. */
internal object NotificationAvatarBitmapCache {
    const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    private const val DEFAULT_LARGE_ICON_DP = 64f
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(0)
    }
    private val inFlight = ConcurrentHashMap<String, FutureTask<Bitmap?>>()
    @Volatile private var activeNamespace = ""

    fun namespace(context: Context): String {
        val account = runCatching { WeChatApis.account()?.selfWxId().orEmpty() }.getOrDefault("")
        return "${context.packageName}:$account"
    }

    fun targetEdgePx(context: Context): Int {
        val resources = context.resources
        val identifier = resources.getIdentifier(
            "notification_large_icon_width",
            "dimen",
            "android"
        )
        val frameworkSize = if (identifier != 0) {
            runCatching { resources.getDimensionPixelSize(identifier) }.getOrDefault(0)
        } else 0
        return frameworkSize.takeIf { it > 0 }
            ?: (DEFAULT_LARGE_ICON_DP * resources.displayMetrics.density + 0.5f).toInt()
    }

    fun getOrLoad(
        context: Context,
        owner: String,
        talker: String,
        sourceFingerprint: String,
        stillCurrent: () -> Boolean = { true },
        loader: () -> Bitmap?
    ): Bitmap? {
        if (talker.isBlank() || sourceFingerprint.isBlank() || !stillCurrent()) return null
        val namespace = namespace(context)
        ensureNamespace(namespace)
        val key = "$owner\n$namespace\n$talker\n$sourceFingerprint"
        cache.get(key)?.takeUnless { it.isRecycled }?.let { return it }

        val task = FutureTask(Callable {
            runCatching(loader).getOrNull()?.takeUnless { it.isRecycled || it.width <= 0 || it.height <= 0 }
        })
        val existing = inFlight.putIfAbsent(key, task)
        val selected = existing ?: task
        if (existing == null) task.run()
        val bitmap = runCatching { selected.get(8L, TimeUnit.SECONDS) }.getOrNull()
        if (existing == null) inFlight.remove(key, task)
        if (bitmap == null || !stillCurrent() || namespace != namespace(context)) return null
        synchronized(cache) {
            if (namespace == activeNamespace && stillCurrent()) cache.put(key, bitmap)
        }
        return bitmap.takeIf { stillCurrent() && namespace == namespace(context) }
    }

    fun invalidateTalker(context: Context, talker: String?) {
        val id = talker?.trim().orEmpty()
        if (id.isEmpty()) return
        val namespace = namespace(context)
        val infix = "\n$namespace\n$id\n"
        synchronized(cache) {
            cache.snapshot().keys.filter { it.contains(infix) }.forEach(cache::remove)
        }
        inFlight.keys.filter { it.contains(infix) }.forEach { key ->
            inFlight.remove(key)?.cancel(true)
        }
    }

    fun clearOwner(owner: String) {
        val prefix = "$owner\n"
        synchronized(cache) {
            cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach(cache::remove)
        }
        inFlight.keys.filter { it.startsWith(prefix) }.forEach { key ->
            inFlight.remove(key)?.cancel(true)
        }
    }

    fun clearAll() {
        synchronized(cache) { cache.evictAll() }
        inFlight.values.forEach { it.cancel(true) }
        inFlight.clear()
    }

    fun decode(source: String, targetEdgePx: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            openUrl(source)?.use { BitmapFactory.decodeStream(it, null, options) }
        } else {
            BitmapFactory.decodeFile(source, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        val edge = targetEdgePx.coerceAtLeast(1)
        while (maxOf(options.outWidth / (sample * 2), options.outHeight / (sample * 2)) >= edge) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = if (source.startsWith("http://") || source.startsWith("https://")) {
            openUrl(source)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } else {
            File(source).takeIf { it.isFile && it.length() > 0L }
                ?.let { BitmapFactory.decodeFile(it.absolutePath, decodeOptions) }
        }
        decoded ?: return null
        val longestEdge = maxOf(decoded.width, decoded.height)
        if (longestEdge <= edge) return decoded
        val scale = edge.toFloat() / longestEdge.toFloat()
        val width = (decoded.width * scale + 0.5f).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale + 0.5f).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(decoded, width, height, true) }
            .getOrDefault(decoded)
    }

    private fun openUrl(source: String) = runCatching {
        URL(source).openConnection().apply {
            connectTimeout = 3000
            readTimeout = 3000
        }.getInputStream()
    }.getOrNull()

    private fun ensureNamespace(namespace: String) {
        if (activeNamespace == namespace) return
        synchronized(cache) {
            if (activeNamespace != namespace) {
                cache.evictAll()
                activeNamespace = namespace
            }
        }
        inFlight.entries.removeIf { entry ->
            if (!entry.key.contains("\n$namespace\n")) {
                entry.value.cancel(true)
                true
            } else false
        }
    }
}
