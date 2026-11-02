package h.Hchat.hooks.items.customfriendavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import h.Hchat.hooks.items.notification.NotificationAvatarBitmapCache
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object CustomFriendAvatarStore {
    fun interface ChangeListener {
        fun onAvatarChanged(context: Context, wxid: String)
    }

    private data class CacheEntry(
        val modified: Long,
        val size: Long,
        val bytes: Long,
        val bitmap: Bitmap
    )

    private const val MAX_CACHE_ENTRIES = 128
    private const val MAX_CACHE_BYTES = 16L * 1024 * 1024
    private val cache = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)
    private var cacheBytes = 0L
    private val changeListeners = CopyOnWriteArrayList<WeakReference<ChangeListener>>()
    private val revisionGeneration = AtomicLong(0L)

    @JvmStatic
    fun configuredFriends(context: Context): Set<String> {
        val saved = CustomFriendAvatarSettings.preferences(context)
            .getStringSet(CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS, emptySet())
            .orEmpty()
        return saved.filterTo(linkedSetOf()) { avatarFile(context, it).isFile }
    }

    @JvmStatic
    fun hasAvatar(context: Context, wxid: String?): Boolean {
        val id = wxid?.trim().orEmpty()
        return id.isNotEmpty() && avatarFile(context, id).isFile
    }

    @JvmStatic
    fun avatarPath(context: Context, wxid: String?): String {
        val id = wxid?.trim().orEmpty()
        return id.takeIf(String::isNotEmpty)?.let { avatarFile(context, it).absolutePath }.orEmpty()
    }

    @JvmStatic
    fun loadBitmap(context: Context, wxid: String?): Bitmap? {
        val id = wxid?.trim().orEmpty()
        if (id.isEmpty()) return null
        val key = cacheKey(context, id)
        val revision = revisionGeneration.get()
        val file = avatarFile(context, id)
        if (!file.isFile || file.length() <= 0L) {
            removeCached(key)
            return null
        }
        val modified = file.lastModified()
        val size = file.length()
        synchronized(cache) {
            cache[key]?.let {
                if (it.modified == modified &&
                    it.size == size &&
                    !it.bitmap.isRecycled
                ) {
                    return it.bitmap
                }
                cache.remove(key)
                cacheBytes -= it.bytes
            }
        }
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            ?: return null
        if (revisionGeneration.get() != revision || !file.isFile ||
            file.lastModified() != modified || file.length() != size
        ) {
            return null
        }
        putCached(key, modified, size, decoded)
        return decoded
    }

    /** Memory-only lookup for drawable callbacks. It performs no hashing or filesystem checks. */
    @JvmStatic
    fun cachedBitmap(context: Context, wxid: String?): Bitmap? {
        val id = wxid?.trim().orEmpty()
        if (id.isEmpty()) return null
        return synchronized(cache) {
            cache[cacheKey(context, id)]?.bitmap?.takeUnless { it.isRecycled }
        }
    }

    @JvmStatic
    fun addChangeListener(listener: ChangeListener): AutoCloseable {
        val reference = WeakReference(listener)
        changeListeners += reference
        return AutoCloseable { changeListeners.remove(reference) }
    }

    @JvmStatic
    fun loadNotificationBitmap(context: Context, wxid: String?): Bitmap? {
        if (!CustomFriendAvatarSettings.notificationsEnabled(context)) return null
        val id = wxid?.trim().orEmpty()
        if (id.isEmpty()) return null
        val file = avatarFile(context, id).takeIf { it.isFile && it.length() > 0L } ?: return null
        val fingerprint = "custom:${file.lastModified()}:${file.length()}"
        val target = NotificationAvatarBitmapCache.targetEdgePx(context)
        return NotificationAvatarBitmapCache.getOrLoad(
            context,
            "custom_friend_avatar_notification",
            id,
            fingerprint
        ) {
            NotificationAvatarBitmapCache.decode(file.absolutePath, target)
        }
    }

    @JvmStatic
    @Synchronized
    fun saveFromUri(context: Context, wxid: String, uri: Uri): Boolean {
        return saveFromUri(context, wxid, uri, trackConfiguredFriend = true)
    }

    @JvmStatic
    @Synchronized
    fun saveFromUri(
        context: Context,
        wxid: String,
        uri: Uri,
        trackConfiguredFriend: Boolean
    ): Boolean {
        val id = wxid.trim()
        if (id.isEmpty()) return false
        return runCatching {
            val source = decodeBounded(context, uri) ?: error("无法解析图片")
            val edge = minOf(source.width, source.height)
            val bitmap = if (source.width == edge && source.height == edge) {
                source
            } else {
                Bitmap.createBitmap(
                    source,
                    (source.width - edge) / 2,
                    (source.height - edge) / 2,
                    edge,
                    edge
                )
            }
            val target = avatarFile(context, id)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, target.name + ".tmp")
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("头像写入失败")
                }
                output.fd.sync()
            }
            if (!temporary.isFile || temporary.length() <= 0L) error("头像文件为空")
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrElse {
                if (target.exists() && !target.delete()) error("旧头像删除失败")
                if (!temporary.renameTo(target)) error("头像替换失败")
            }
            if (trackConfiguredFriend) {
                val ids = configuredFriends(context).toMutableSet().apply { add(id) }
                CustomFriendAvatarSettings.preferences(context).edit()
                    .putStringSet(CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS, ids)
                    .apply()
            }
            bumpRevision(context, id)
            removeCached(context, id)
            notifyAvatarChanged(context, id)
            true
        }.getOrElse {
            HLog.e("$TAG 保存自定义头像失败: wxid=$id, error=${it.message}", it)
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun remove(context: Context, wxid: String): Boolean {
        val id = wxid.trim()
        if (id.isEmpty()) return false
        val file = avatarFile(context, id)
        val deleted = !file.exists() || file.delete()
        val ids = configuredFriends(context).toMutableSet().apply { remove(id) }
        CustomFriendAvatarSettings.preferences(context).edit()
            .putStringSet(CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS, ids)
            .apply()
        if (deleted) bumpRevision(context, id)
        removeCached(context, id)
        if (deleted) notifyAvatarChanged(context, id)
        return deleted
    }

    @JvmStatic
    fun invalidate(wxid: String?) {
        val id = wxid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        synchronized(cache) {
            val suffix = "\n$id"
            val keys = cache.keys.filter { it.endsWith(suffix) }
            keys.forEach { key -> cache.remove(key)?.let { cacheBytes -= it.bytes } }
        }
    }

    @JvmStatic
    fun invalidate(context: Context, wxid: String?) {
        val id = wxid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        bumpRevision(context, id)
        removeCached(context, id)
        notifyAvatarChanged(context, id)
    }

    @JvmStatic
    fun revision(context: Context, wxid: String?): Long {
        val id = wxid?.trim().orEmpty()
        return if (id.isEmpty()) 0L else revisionGeneration.get()
    }

    private fun putCached(id: String, modified: Long, size: Long, bitmap: Bitmap) {
        val bytes = bitmap.allocationByteCount.toLong().coerceAtLeast(0L)
        if (bytes == 0L || bytes > MAX_CACHE_BYTES) return
        synchronized(cache) {
            cache.remove(id)?.let { cacheBytes -= it.bytes }
            cache[id] = CacheEntry(modified, size, bytes, bitmap)
            cacheBytes += bytes
            val iterator = cache.entries.iterator()
            while ((cache.size > MAX_CACHE_ENTRIES || cacheBytes > MAX_CACHE_BYTES) && iterator.hasNext()) {
                cacheBytes -= iterator.next().value.bytes
                iterator.remove()
            }
        }
    }

    private fun removeCached(context: Context, id: String) = removeCached(cacheKey(context, id))

    private fun removeCached(key: String) {
        synchronized(cache) {
            cache.remove(key)?.let { cacheBytes -= it.bytes }
        }
    }

    private fun cacheKey(context: Context, id: String): String = "${context.packageName}\n$id"

    private fun bumpRevision(context: Context, id: String) {
        revisionGeneration.incrementAndGet()
    }

    private fun notifyAvatarChanged(context: Context, id: String) {
        NotificationAvatarBitmapCache.invalidateTalker(context, id)
        changeListeners.removeIf { reference ->
            val listener = reference.get() ?: return@removeIf true
            runCatching { listener.onAvatarChanged(context, id) }
            false
        }
    }

    private fun avatarFile(context: Context, wxid: String): File {
        val directory = File(HchatStorage.storageDir(context), DIRECTORY_NAME)
        return File(directory, sha256(wxid) + ".png")
    }

    private fun decodeBounded(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val TAG = "[Hchat:CustomFriendAvatar]"
    private const val DIRECTORY_NAME = "custom_friend_avatars"
    private const val MAX_DIMENSION = 1024
}
