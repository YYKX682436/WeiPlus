package h.Hchat.hooks.items.linkparse

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal enum class WorkImageFormat(val extension: String) {
    JPEG("jpg"),
    WEBP("webp"),
    GIF("gif"),
    ANIMATED_WEBP("webp")
}

internal data class DownloadedWorkImage(
    val file: File,
    val bytes: Long,
    val format: WorkImageFormat
)

internal object WorkImageFormatSniffer {
    fun detect(bytes: ByteArray): WorkImageFormat? = detect(ByteArrayInputStream(bytes), bytes.size.toLong())

    fun detect(stream: InputStream, size: Long): WorkImageFormat? = runCatching {
        val input = DataInputStream(stream)
        val head = ByteArray(minOf(12L, size).toInt()).also { input.readFully(it) }
        if (head.size >= 3 && head[0] == 0xff.toByte() && head[1] == 0xd8.toByte() && head[2] == 0xff.toByte()) {
            return@runCatching WorkImageFormat.JPEG
        }
        if (head.size >= 6 && String(head, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) {
            return@runCatching WorkImageFormat.GIF
        }
        if (head.size != 12 || String(head, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(head, 8, 4, Charsets.US_ASCII) != "WEBP") return@runCatching null
        val end = littleEndianInt(head, 4) + 8
        if (end != size) return@runCatching null
        var position = 12L
        var static = false
        var animated = false
        val chunk = ByteArray(8)
        while (position < end) {
            if (end - position < 8) return@runCatching null
            input.readFully(chunk)
            val length = littleEndianInt(chunk, 4)
            val padded = length + (length and 1L)
            if (padded > end - position - 8) return@runCatching null
            val type = String(chunk, 0, 4, Charsets.US_ASCII)
            var skip = padded.toInt()
            when (type) {
                "ANIM", "ANMF" -> animated = true
                "VP8 ", "VP8L" -> static = length > 0
                "VP8X" -> if (length >= 1) {
                    val flags = input.readUnsignedByte()
                    animated = animated || (flags and 2) != 0
                    skip--
                }
            }
            while (skip > 0) {
                val skipped = input.skipBytes(skip)
                if (skipped == 0) {
                    input.readByte()
                    skip--
                } else skip -= skipped
            }
            position += 8 + padded
        }
        when {
            animated -> WorkImageFormat.ANIMATED_WEBP
            static -> WorkImageFormat.WEBP
            else -> null
        }
    }.getOrNull()

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, i ->
        value or ((bytes[offset + i].toLong() and 255) shl (i * 8))
    }
}

/** Anonymous, bounded image downloads. Original JPEG/WebP bytes are kept unchanged. */
internal class WorkImageClient(private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()) {

    fun download(
        source: WorkImageSource,
        platform: WorkLinkMatcher.Platform,
        targetBase: File,
        limit: Long,
        onFailure: (String) -> Unit
    ): DownloadedWorkImage? {
        var sawAnimatedWebp = false
        for (candidate in source.candidates) {
            val image = downloadCandidate(candidate, platform, targetBase, minOf(limit, MAX_IMAGE_BYTES)) ?: continue
            if (image.format == WorkImageFormat.ANIMATED_WEBP) sawAnimatedWebp = true
            val isGif = image.format == WorkImageFormat.GIF
            if (image.format != WorkImageFormat.ANIMATED_WEBP &&
                (source.live != null && !isGif || source.live == null &&
                    (isGif || !source.expectsAnimation && !sawAnimatedWebp))) return image
            image.file.delete()
        }
        onFailure(if (sawAnimatedWebp) "animated_webp_unsupported" else "image_unavailable")
        return null
    }

    private fun downloadCandidate(
        candidate: String,
        platform: WorkLinkMatcher.Platform,
        targetBase: File,
        limit: Long
    ): DownloadedWorkImage? {
        if (limit < 6) return null
        val part = File(targetBase.absolutePath + ".part")
        if (part.exists() || finalFiles(targetBase).any(File::exists)) return null
        var url = candidate.toHttpUrlOrNull() ?: return null
        var result: DownloadedWorkImage? = null
        try {
            repeat(MAX_REDIRECTS + 1) { hop ->
                if (!allowed(url, platform)) return null
                request(url).use { response ->
                    if (response.isRedirect) {
                        if (hop == MAX_REDIRECTS) return null
                        url = response.header("Location")?.let { url.resolve(it) } ?: return null
                        return@use
                    }
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    if (body.contentLength() > limit) return null
                    val buffer = ByteArray(16 * 1024)
                    var size = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(part).use { output ->
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw IOException("interrupted")
                                val read = input.read(buffer)
                                if (read < 0) break
                                size += read
                                if (size > limit) throw IOException("image too large")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (body.contentLength() >= 0 && size != body.contentLength()) return null
                    val format = part.inputStream().buffered().use { WorkImageFormatSniffer.detect(it, size) } ?: return null
                    val target = File(targetBase.absolutePath + "." + format.extension)
                    if (!part.renameTo(target)) return null
                    result = DownloadedWorkImage(target, size, format)
                    return@use
                }
                if (result != null) return result
            }
        } catch (_: Exception) {
            return null
        } finally {
            if (part.exists()) part.delete()
        }
        return result
    }

    private fun allowed(url: HttpUrl, platform: WorkLinkMatcher.Platform): Boolean =
        when (platform) {
            WorkLinkMatcher.Platform.DOUYIN -> WorkImageExtractor.allowedDouyinImage(url.toString())
            WorkLinkMatcher.Platform.XIAOHONGSHU -> WorkImageExtractor.allowedXhsImage(url.toString())
        }

    private fun request(url: HttpUrl): Response = client.newCall(
        Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "image/gif,image/webp,image/jpeg,*/*")
            .header("Accept-Encoding", "identity")
            .get().build()
    ).execute()

    private fun finalFiles(base: File): List<File> = WorkImageFormat.entries.map {
        File(base.absolutePath + "." + it.extension)
    }

    companion object {
        const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
        private const val MAX_REDIRECTS = 4
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
    }
}
