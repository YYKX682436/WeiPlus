package h.Hchat.hooks.items.linkparse

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Isolated anonymous requests: no shared cookie jar, automatic redirects, or third-party parser. */
internal class WorkVideoClient {
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun resolve(match: WorkLinkMatcher.Match): String? = runCatching {
        val input = match.url.toHttpUrlOrNull() ?: return null
        val page = getWorkPage(input, match.platform) ?: return null
        when (match.platform) {
            WorkLinkMatcher.Platform.DOUYIN -> {
                val id = page.url.pathSegments.lastOrNull { it.isNotBlank() } ?: return null
                if (!id.matches(Regex("[1-9][0-9]{15,19}"))) return null
                val detail = "https://api.amemv.com/aweme/v1/aweme/detail/".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("aweme_id", id).addQueryParameter("aid", "6383")
                    .addQueryParameter("device_platform", "web").addQueryParameter("version_code", "190500")
                    .addQueryParameter("version_name", "19.5.0").build()
                val body = request(detail).use { response -> readText(response) } ?: return null
                WorkVideoExtractor.douyin(body, id)
            }
            WorkLinkMatcher.Platform.XIAOHONGSHU -> {
                val id = page.url.pathSegments.lastOrNull { it.isNotBlank() } ?: return null
                WorkVideoExtractor.xiaohongshu(page.body, id)
            }
            WorkLinkMatcher.Platform.KUAISHOU -> {
                val id = page.url.queryParameter("shareObjectId") ?: return null
                WorkVideoExtractor.kuaishou(page.body, id)
            }
        }
    }.getOrNull()

    /** Caller owns [target]; a failed or incomplete download never leaves it behind. */
    fun download(mediaUrl: String, platform: WorkLinkMatcher.Platform, target: File): Boolean {
        val part = File(target.absolutePath + ".part")
        if (target.exists() || part.exists()) return false
        var completed = false
        try {
            var url = mediaUrl.toHttpUrlOrNull() ?: return false
            repeat(MAX_REDIRECTS + 1) { hop ->
                if (!WorkVideoExtractor.allowedMedia(url.toString(), platform)) return false
                request(url).use { response ->
                    if (response.isRedirect) {
                        if (hop == MAX_REDIRECTS) return false
                        url = response.header("Location")?.let { url.resolve(it) } ?: return false
                        return@use
                    }
                    if (!response.isSuccessful) return false
                    val body = response.body ?: return false
                    if (body.contentLength() > MAX_VIDEO_BYTES) return false
                    val buffer = ByteArray(16 * 1024)
                    var size = 0L
                    val signature = ByteArrayOutputStream(12)
                    body.byteStream().use { source ->
                        FileOutputStream(part).use { out ->
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw IOException("interrupted")
                                val read = source.read(buffer)
                                if (read < 0) break
                                size += read
                                if (size > MAX_VIDEO_BYTES) throw IOException("video too large")
                                if (signature.size() < 12) signature.write(buffer, 0, minOf(read, 12 - signature.size()))
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    val head = signature.toByteArray()
                    if (size < 12 || head.size < 12 || String(head, 4, 4, Charsets.US_ASCII) != "ftyp") return false
                    if (body.contentLength() >= 0 && size != body.contentLength()) return false
                    if (!part.renameTo(target)) return false
                    completed = true
                    return true
                }
            }
        } catch (_: Exception) {
            return false
        } finally {
            if (part.exists()) part.delete()
            if (!completed && target.exists()) target.delete()
        }
        return false
    }

    private fun getWorkPage(start: HttpUrl, platform: WorkLinkMatcher.Platform): Page? {
        var url = start
        repeat(MAX_REDIRECTS + 1) { hop ->
            val work = WorkLinkMatcher.parse(url.toString()) ?: return null
            if (work.platform != platform) return null
            request(url).use { response ->
                if (response.isRedirect) {
                    if (hop == MAX_REDIRECTS) return null
                    url = response.header("Location")?.let { url.resolve(it) } ?: return null
                    return@use
                }
                if (!response.isSuccessful || work.requiresRedirect) return null
                val body = readText(response) ?: return null
                return Page(url, body)
            }
        }
        return null
    }

    private fun request(url: HttpUrl): Response = client.newCall(
        Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/json,*/*")
            .header("Accept-Encoding", "identity")
            .get().build()
    ).execute()

    private fun readText(response: Response): String? {
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        if (body.contentLength() > MAX_PAGE_BYTES) return null
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        body.byteStream().use { source ->
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_PAGE_BYTES) return null
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class Page(val url: HttpUrl, val body: String)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
        private const val MAX_REDIRECTS = 4
        private const val MAX_PAGE_BYTES = 3 * 1024 * 1024
        private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024
    }
}
