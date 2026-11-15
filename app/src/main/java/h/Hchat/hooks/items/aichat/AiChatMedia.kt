package h.Hchat.hooks.items.aichat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Base64
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatRetransmitPayloadFactory
import h.Hchat.hooks.api.model.WeChatMessage
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import me.yun.silk.SilkCodec
import h.Hchat.utils.HLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest

internal object AiChatMedia {
    fun cachedVoice(message: WeChatMessage): String {
        message.translatedContent.takeIf { it.isNotBlank() && !it.trim().startsWith("<") }?.let { return it }
        val db = WeChatApis.database() ?: return ""
        fun lookup(column: String, value: String): String {
            val cursor = runCatching { db.rawQuery("SELECT content FROM VoiceTransText WHERE $column=? LIMIT 1", arrayOf(value)) }.getOrNull() ?: return ""
            cursor.use { return if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
        }
        if (message.msgId > 0) lookup("msgId", message.msgId.toString()).takeIf { it.isNotBlank() }?.let { return it }
        return if (message.imagePath.isNotBlank()) lookup("cmsgId", message.imagePath) else ""
    }

    /** WeChat 8.0.80/3220: opcode 0 asks the native listener, which refuses when busy. */
    @Synchronized fun transcribeVoice(context: Context, message: WeChatMessage): String {
        cachedVoice(message).takeIf { it.isNotBlank() }?.let { return it }
        val info = runCatching { context.packageManager.getPackageInfo("com.tencent.mm", 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val version = info?.versionCode ?: 0
        if (info?.versionName != "8.0.80" || version != 3220) throw AiRequestException("当前微信版本尚未适配原生语音转写")
        if (message.msgId <= 0 || message.imagePath.isBlank()) throw AiRequestException("微信语音消息缺少本地标识")
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        val failure = AtomicReference<String>()
        val responseCode = java.util.concurrent.atomic.AtomicInteger(0)
        val errorCode = java.util.concurrent.atomic.AtomicInteger(0)
        val errorType = java.util.concurrent.atomic.AtomicInteger(0)
        val scene = if (message.isGroupChat()) 2 else 1
        Handler(Looper.getMainLooper()).post {
            try {
                val cls = context.classLoader.loadClass("com.tencent.mm.autogen.events.ExtTranslateVoiceEvent")
                val event = cls.getDeclaredConstructor().newInstance()
                val request = cls.getField("g").get(event)
                val response = cls.getField("h").get(event)
                request.javaClass.getField("a").set(request, message.imagePath)
                request.javaClass.getField("b").set(request, message.msgId.toString())
                request.javaClass.getField("g").set(request, message.talker)
                request.javaClass.getField("c").setInt(request, 0)
                request.javaClass.getField("d").setInt(request, 1)
                request.javaClass.getField("f").setInt(request, scene)
                request.javaClass.getField("e").set(request, Runnable {
                    runCatching {
                        if (response.javaClass.getField("b").getBoolean(response)) {
                            result.set(response.javaClass.getField("a").get(response) as? String ?: "")
                            responseCode.set(response.javaClass.getField("c").getInt(response))
                            val detail = response.javaClass.getField("d").get(response)
                            if (detail != null) {
                                errorCode.set(runCatching { detail.javaClass.getField("d").getInt(detail) }.getOrDefault(0))
                                errorType.set(runCatching { detail.javaClass.getField("e").getInt(detail) }.getOrDefault(0))
                            }
                            done.countDown()
                        }
                    }.onFailure { failure.set("微信转写回调不可用"); done.countDown() }
                })
                // IEvent.e() reports whether listeners exist, not the listener's acceptance result.
                val listenerPresent = cls.getMethod("e").invoke(event) == true
                HLog.i("[Hchat:AI-Voice] scene=$scene listenerPresent=$listenerPresent idPresent=${message.msgId > 0} fileIdPresent=${message.imagePath.isNotBlank()}")
                if (!listenerPresent) { failure.set("微信转写服务未注册"); done.countDown() }
            } catch (_: Throwable) { failure.set("微信原生转写接口不可用"); done.countDown() }
        }
        if (!done.await(45, TimeUnit.SECONDS)) {
            HLog.i("[Hchat:AI-Voice] scene=$scene timedOut=true")
            throw AiRequestException("微信转写未完成：服务可能忙碌，受理状态未知；未取消其他转写")
        }
        failure.get()?.let { throw AiRequestException(it) }
        val text = result.get()?.takeIf { it.isNotBlank() } ?: cachedVoice(message)
        HLog.i("[Hchat:AI-Voice] scene=$scene completed=true state=${responseCode.get()} errorType=${errorType.get()} errorCode=${errorCode.get()} textLength=${text?.length ?: 0}")
        return text?.takeIf { it.isNotBlank() } ?: throw AiRequestException(
            when (responseCode.get()) { 1 -> "微信语音转写被取消"; 2 -> "微信原生语音转写失败"; else -> "微信未能转写该语音" }
        )
    }

    fun image(context: Context, message: WeChatMessage): JSONObject {
        val images = WeChatApis.media()?.images()
        var sawFile = false
        diagnostic("type=image ref=${messageRef(message)} idPresent=${message.msgId > 0} serverIdPresent=${message.msgSvrId > 0}")
        // The database notification can precede the end of WeChat's download.
        repeat(4) { attempt ->
            val current = WeChatApis.messageStore()?.getMessageById(message.msgId)?.takeIf { it.talker == message.talker } ?: message
            val native = WeChatApis.database()?.nativeMessageById(current.msgId)
            val paths = listOf(
                WeChatRetransmitPayloadFactory.build(current, native)?.fileName,
                images?.resolveBestAvailablePath(native), images?.resolvePathToken(current.imagePath), current.imagePath
            ).filterNotNull().filter { it.isNotBlank() }.distinct()
            for (path in paths) {
                val file = File(path)
                if (!file.isFile || file.length() <= 0) continue
                sawFile = true
                if (isStable(file)) encodeImage(file)?.let { return it }
            }
            if (attempt < 3) Thread.sleep(500)
        }
        val info = message.getImageMsg()
        val url = info?.bigImgUrl?.ifBlank { info.midImgUrl }.orEmpty()
        if (images != null && info != null && url.isNotBlank() && info.key.isNotBlank() && images.canDownloadCdn()) {
            val file = File.createTempFile("hchat-ai-image-", ".tmp", context.cacheDir)
            file.delete()
            val done = CountDownLatch(1)
            val active = AtomicBoolean(true)
            val downloaded = AtomicBoolean(false)
            val original = info.bigImgUrl.isNotBlank()
            val size = if (original) info.bigLength else info.midLength
            try {
                val submitted = images.downloadCdn(info.md5, url, info.key, file.absolutePath,
                    if (original) 1 else 2, size, object : h.Hchat.hooks.api.media.WeChatImageApi.DownloadCallback {
                        override fun onSuccess(result: File) {
                            if (active.get()) downloaded.set(true) else result.delete()
                            done.countDown()
                        }
                        override fun onError(message: String) { if (!active.get()) file.delete(); done.countDown() }
                    })
                diagnostic("type=image cdnSubmitted=$submitted")
                if (submitted && done.await(15, TimeUnit.SECONDS) && downloaded.get() && (size <= 0 || file.length() >= size)) {
                    sawFile = true
                    encodeImage(file)?.let { return it }
                }
            } finally { active.set(false); file.delete() }
        }
        diagnostic("type=image decoded=false filePresent=$sawFile")
        throw AiRequestException(if (sawFile) "微信图片文件无法解码或尚未下载完整" else "微信图片尚未下载或本地文件不可用")
    }

    private fun encodeImage(file: File): JSONObject? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val output = ByteArrayOutputStream()
        try { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) return null }
        finally { bitmap.recycle() }
        diagnostic("type=image decoded=true encodedBytes=${output.size()}")
        val data = "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", data))
    }

    fun audio(context: Context, message: WeChatMessage, provider: AiProvider): JSONObject {
        val api = WeChatApis.media()?.voices() ?: throw AiRequestException("微信语音文件接口不可用")
        val native = WeChatApis.database()?.nativeMessageById(message.msgId)
        val file = File(api.resolvePath(native, message.imagePath).orEmpty())
        if (!file.isFile || file.length() <= 0 || file.length() > 12L * 1024 * 1024) throw AiRequestException("语音文件不可用或超过 12 MB")
        val codec = try { SilkCodec() } catch (_: Throwable) { throw AiRequestException("音频解码器不可用") }
        val duration = runCatching { codec.getDuration(file.absolutePath) }.getOrDefault(0L)
        if (duration <= 0 || duration > 60_000) throw AiRequestException("语音时长不可读或超过 60 秒")
        var temporary: File? = null
        try {
            val type = codec.getFileType(file.absolutePath)
            val source = if (type == 1) {
                File.createTempFile("hchat-ai-audio-", ".mp3", context.cacheDir).also {
                    temporary = it
                    if (codec.silkToMp3(file.absolutePath, it.absolutePath, 24000) != 0 || it.length() <= 0) throw AiRequestException("SILK 语音转换失败")
                }
            } else file
            val format = when (if (type == 1) 2 else type) { 2 -> "mp3"; 3 -> "wav"; else -> throw AiRequestException("语音格式不受支持") }
            if (source.length() > 12L * 1024 * 1024) throw AiRequestException("转换后音频超过 12 MB")
            return audioPayload(source.readBytes(), format, provider)
        } finally { temporary?.delete() }
    }

    fun isAudioFile(message: WeChatMessage): Boolean = message.isFile() &&
        message.getFileMsg()?.ext?.lowercase() in setOf("mp3", "wav", "flac", "m4a", "ogg")

    /** A received audio attachment is different from a WeChat voice message. */
    fun audioFile(message: WeChatMessage, provider: AiProvider): JSONObject {
        val format = message.getFileMsg()?.ext?.lowercase().orEmpty()
        if (!isAudioFile(message) || (provider != AiProvider.MIMO && format !in setOf("mp3", "wav"))) {
            throw AiRequestException("当前音频文件格式不受支持")
        }
        val path = WeChatRetransmitPayloadFactory.build(message, null)?.fileName.orEmpty()
        val file = File(path)
        if (!file.isFile || file.length() <= 0 || file.length() > 12L * 1024 * 1024) {
            throw AiRequestException("微信音频文件尚不可用或超过 12 MB")
        }
        val retriever = MediaMetadataRetriever()
        val valid = try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            duration != null && duration in 1..60_000 && mime.startsWith("audio/")
        } catch (_: Exception) { false } finally { retriever.release() }
        if (!valid) throw AiRequestException("音频文件不可解码或时长超过 60 秒")
        val bytes = file.readBytes()
        if (!audioHeaderMatches(bytes, format)) throw AiRequestException("音频内容与文件格式不匹配")
        return audioPayload(bytes, format, provider)
    }

    internal fun audioHeaderMatches(bytes: ByteArray, format: String): Boolean {
        fun matches(offset: Int, value: String) = bytes.size >= offset + value.length &&
            value.indices.all { bytes[offset + it].toInt() == value[it].code }
        return when (format) {
            "mp3" -> matches(0, "ID3") || (bytes.size >= 2 && (bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 224) == 224)
            "wav" -> matches(0, "RIFF") && matches(8, "WAVE")
            "flac" -> matches(0, "fLaC")
            "m4a" -> matches(4, "ftyp")
            "ogg" -> matches(0, "OggS")
            else -> false
        }
    }

    internal fun audioPayload(bytes: ByteArray, format: String, provider: AiProvider): JSONObject {
        val mime = when (format) {
            "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"; else -> throw AiRequestException("音频格式不受支持")
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val payload = if (provider == AiProvider.MIMO) JSONObject().put("data", "data:$mime;base64,$encoded")
            else JSONObject().put("data", encoded).put("format", format)
        return JSONObject().put("type", "input_audio").put("input_audio", payload)
    }

    fun video(context: Context, message: WeChatMessage): JSONObject {
        diagnostic("type=video ref=${messageRef(message)} idPresent=${message.msgId > 0} serverIdPresent=${message.msgSvrId > 0}")
        var sawFile = false
        repeat(4) { attempt ->
            val current = WeChatApis.messageStore()?.getMessageById(message.msgId)?.takeIf { it.talker == message.talker } ?: message
            val path = WeChatRetransmitPayloadFactory.build(current, null)?.fileName.orEmpty()
            val file = File(path)
            if (file.isFile && file.length() > 0) {
                sawFile = true
                val expected = current.getVideoMsg()?.length ?: 0L
                if ((expected <= 0 || file.length() >= expected) && isStable(file)) encodeVideo(file)?.let { return it }
            }
            if (attempt < 3) Thread.sleep(500)
        }
        val videos = WeChatApis.media()?.videos()
        val native = videos?.resolveDownloadInfo(message.imagePath)
        val info = message.getVideoMsg()
        val url = native?.cdnUrl?.takeIf { it.isNotBlank() } ?: info?.cdnVideoUrl.orEmpty()
        val key = native?.aesKey?.takeIf { it.isNotBlank() } ?: info?.aesKey.orEmpty()
        val length = native?.totalLength?.takeIf { it > 0 } ?: info?.length ?: 0L
        if (length > 12L * 1024 * 1024) throw AiRequestException("视频超过 12 MB 限制")
        if (videos != null && url.isNotBlank() && key.isNotBlank()) {
            val file = File.createTempFile("hchat-ai-video-", ".mp4", context.cacheDir)
            file.delete()
            val done = CountDownLatch(1)
            val active = AtomicBoolean(true)
            val downloaded = AtomicBoolean(false)
            try {
                val submitted = videos.downloadCdn(native?.md5?.takeIf { it.isNotBlank() } ?: info?.md5.orEmpty(),
                    url, key, file.absolutePath, object : h.Hchat.hooks.api.media.WeChatVideoApi.DownloadCallback {
                        override fun onSuccess(result: File) {
                            if (active.get()) downloaded.set(true) else result.delete()
                            done.countDown()
                        }
                        override fun onError(message: String) { if (!active.get()) file.delete(); done.countDown() }
                    })
                diagnostic("type=video cdnSubmitted=$submitted")
                if (submitted && done.await(15, TimeUnit.SECONDS) && downloaded.get() && (length <= 0 || file.length() >= length)) {
                    sawFile = true
                    encodeVideo(file)?.let { return it }
                }
            } finally { active.set(false); file.delete() }
        }
        diagnostic("type=video decoded=false filePresent=$sawFile")
        throw AiRequestException(if (sawFile) "视频尚未下载完整或格式无法解码" else "微信视频文件尚未下载或本地不可用")
    }
    private fun encodeVideo(file: File): JSONObject? {
        if (file.length() > 12L * 1024 * 1024) throw AiRequestException("视频超过 12 MB 限制")
        val retriever = MediaMetadataRetriever()
        val metadata = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() to
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
        } catch (_: Exception) { null } finally { retriever.release() }
        val duration = metadata?.first
        val mime = metadata?.second.orEmpty()
        if (duration != null && duration > 60_000) throw AiRequestException("视频超过 60 秒限制")
        if (duration == null || duration <= 0 || mime !in setOf("video/mp4", "video/quicktime")) return null
        val data = "data:$mime;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        diagnostic("type=video available=true bytes=${file.length()} durationMs=$duration")
        return JSONObject().put("type", "video_url").put("video_url", JSONObject().put("url", data)).put("fps", 1)
    }
    private fun messageRef(message: WeChatMessage): String = MessageDigest.getInstance("SHA-256")
        .digest("${message.msgId}/${message.msgSvrId}".toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    private fun isStable(file: File): Boolean {
        val size = file.length()
        if (size <= 0) return false
        Thread.sleep(200)
        return file.isFile && file.length() == size
    }
    private fun diagnostic(value: String) { runCatching { HLog.i("[Hchat:AI-Media] $value") } }
}
