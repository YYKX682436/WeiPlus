package h.Hchat.hooks.items.linkparse

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WorkImageClientTest {
    @get:Rule val folder = TemporaryFolder()
    private val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)
    private val gif = "GIF89a\u0001\u0000\u0001\u0000rest".toByteArray()

    @Test fun distinguishesJpegGifAndWebpWithoutConversion() {
        assertEquals(
            WorkImageFormat.JPEG,
            WorkImageFormatSniffer.detect(jpeg)
        )
        assertEquals(WorkImageFormat.GIF, WorkImageFormatSniffer.detect(gif))
        assertEquals(WorkImageFormat.GIF, WorkImageFormatSniffer.detect("GIF87a".toByteArray()))
        assertEquals(WorkImageFormat.WEBP, WorkImageFormatSniffer.detect(webp("VP8 ")))
        assertEquals(WorkImageFormat.ANIMATED_WEBP, WorkImageFormatSniffer.detect(webp("ANIM")))
        assertEquals(WorkImageFormat.ANIMATED_WEBP, WorkImageFormatSniffer.detect(webp("ANMF")))
        assertEquals(WorkImageFormat.ANIMATED_WEBP, WorkImageFormatSniffer.detect(webp("VP8X")))
        assertNull(WorkImageFormatSniffer.detect(webp("ANMF").dropLast(1).toByteArray()))
        assertNull(WorkImageFormatSniffer.detect("RIFF1234WAVE".toByteArray(Charsets.US_ASCII)))
        assertNull(WorkImageFormatSniffer.detect(byteArrayOf(0xff.toByte(), 0xd8.toByte())))
    }

    @Test fun doesNotFlattenAnimatedWebpAndKeepsGifCandidateBytes() {
        val client = client(mapOf("/webp" to webp("ANIM"), "/jpg" to jpeg, "/gif" to gif))
        val base = File(folder.root, "image_test")
        val urls = listOf("webp", "jpg", "gif").map { "https://p3.douyinpic.com/$it" }
        val platform = WorkLinkMatcher.Platform.DOUYIN
        val reason = mutableListOf<String>()
        assertNull(client.download(WorkImageSource(urls.take(2)), platform, base, 1024, reason::add))
        assertEquals(listOf("animated_webp_unsupported"), reason)
        assertTrue(folder.root.listFiles()!!.isEmpty())
        val downloaded = client.download(WorkImageSource(urls), platform, base, 1024, reason::add)!!
        assertEquals(WorkImageFormat.GIF, downloaded.format)
        assertArrayEquals(gif, downloaded.file.readBytes())
    }

    @Test fun rejectsOversizeInvalidAndUntrustedCandidatesAndCleansPartialFiles() {
        val client = client(mapOf("/big" to jpeg + ByteArray(128), "/bad" to ByteArray(10), "/ok" to jpeg))
        val urls = listOf("https://p3.douyinpic.com/big", "https://p3.douyinpic.com/bad", "https://douyinpic.com.evil.example/ok")
        val platform = WorkLinkMatcher.Platform.DOUYIN
        val base = File(folder.root, "image_test")
        assertNull(client.download(WorkImageSource(urls), platform, base, 16) {})
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    private fun webp(chunk: String): ByteArray = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray()).putInt(14).put("WEBP".toByteArray()).put(chunk.toByteArray()).putInt(2)
        .put(2).put(0).array()

    private fun client(bodies: Map<String, ByteArray>): WorkImageClient =
        WorkImageClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bodies.getValue(request.url.encodedPath).toResponseBody()).build()
        }.build())
}
