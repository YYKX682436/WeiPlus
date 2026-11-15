package h.Hchat.hooks.items.aichat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.items.quotetail.ChatQuoteTailXml
import h.Hchat.preferences.HchatStorage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatR2Test {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun blankPromptAndKnownDefaultsMigrateWithoutTouchingCustomInstructions() {
        val prefs = HchatStorage.preferences(context, AiChatSettings.PREFS_NAME)
        prefs.edit().clear().commit()
        assertEquals("", AiChatSettings(context).prefs.getString(AiChatSettings.KEY_PROMPT, ""))
        prefs.edit().clear().putString(AiChatSettings.KEY_PROMPT, "你是一个自然、简洁、有帮助的微信聊天助手。").commit()
        assertEquals("", AiChatSettings(context).prefs.getString(AiChatSettings.KEY_PROMPT, ""))
        prefs.edit().clear().putString(AiChatSettings.KEY_PROMPT, "自定义角色").commit()
        assertEquals("自定义角色", AiChatSettings(context).prefs.getString(AiChatSettings.KEY_PROMPT, ""))
        prefs.edit().putString(AiChatSettings.KEY_PROMPT, "").commit()
        assertEquals("", AiChatSettings(context).prefs.getString(AiChatSettings.KEY_PROMPT, ""))
    }

    @Test fun providerCapabilitiesAndNaturalMediaAnswerAreNotForcedToJson() {
        val flash = AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash")
        assertTrue(flash.supports("image")); assertFalse(flash.copy(model = "deepseek-v4-pro").supports("image"))
        assertFalse(flash.copy(models = listOf(AiModel("deepseek-flash", setOf("text")))).supports("image"))
        val mimo = AiChatConfig(provider = AiProvider.MIMO, model = "mimo-v2.6-flash")
        assertTrue(mimo.supports("image")); assertTrue(mimo.supports("video"))
        assertFalse(mimo.copy(model = "unknown").supports("image"))
        val media = JSONArray().put(JSONObject().put("type", "text").put("text", "what is this"))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,AA==")))
        for (config in listOf(flash, mimo)) {
            val body = AiChatClient.requestBody(config, listOf(AiInput("user", media)), 256)
            assertEquals("data:image/jpeg;base64,AA==", body.getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url"))
            val response = JSONObject("""{"choices":[{"message":{"content":"这是一只猫。"}}]}""")
            assertEquals("这是一只猫。", AiChatClient.parseResponse(config, response).text)
        }
        val saved = mutableMapOf<Long, String>()
        assertEquals("好看。", AiChatRuntime.mediaAnswer("好看。", listOf(42)) { id, text -> saved[id] = text })
        assertTrue("Ordinary replies must not be invented as media descriptions", saved.isEmpty())
        val structured = """{"reply":"是只猫。","descriptions":{"42":"一只橘猫"}}"""
        assertEquals("是只猫。", AiChatRuntime.mediaAnswer(structured, listOf(42)) { id, text -> saved[id] = text })
        assertEquals("一只橘猫", saved[42])
        assertEquals("是只猫。", AiChatRuntime.mediaAnswer(structured, listOf(42)) { _, _ -> throw IllegalStateException("cache unavailable") })
        val video = JSONArray().put(JSONObject().put("type", "video_url").put("video_url", JSONObject().put("url", "data:video/mp4;base64,AA==")).put("fps", 1))
        assertEquals("video_url", AiChatClient.requestBody(mimo, listOf(AiInput("user", video)), 256)
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun imageFileIsDecodedScaledAndEncodedAsActualJpeg() {
        val file = File.createTempFile("ai-r2-image-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val message = WeChatMessage(123, 456, 3, 0, 0, 1000, "friend", "", file.absolutePath, "", "", 0, "", "self")
            val data = AiChatMedia.image(context, message).getJSONObject("image_url").getString("url")
            assertTrue(data.startsWith("data:image/jpeg;base64,"))
            val bytes = android.util.Base64.decode(data.substringAfter(','), android.util.Base64.DEFAULT)
            assertTrue(bytes.size > 2); assertEquals(255, bytes[0].toInt() and 255); assertEquals(216, bytes[1].toInt() and 255)
        } finally { file.delete() }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun missingOrInvalidImageHasExplicitFailure() {
        val missing = WeChatMessage(31, 41, 3, 0, 0, 1000, "friend", "", "/not-a-real-image", "", "", 0, "", "self")
        assertTrue(runCatching { AiChatMedia.image(context, missing) }.exceptionOrNull()!!.message!!.contains("不可用"))
        val file = File.createTempFile("ai-r2-invalid-", ".jpg", context.cacheDir)
        try {
            file.writeText("not an image")
            val invalid = WeChatMessage(32, 42, 3, 0, 0, 1000, "friend", "", file.absolutePath, "", "", 0, "", "self")
            assertTrue(runCatching { AiChatMedia.image(context, invalid) }.exceptionOrNull()!!.message!!.contains("无法解码"))
        } finally { file.delete() }
    }

    @Test fun imageFollowupRequiresSameSenderAndOldImageQuoteKeepsItsReference() {
        val room = "room@chatroom"
        fun message(sender: String, text: String, time: Long, type: Int, outgoing: Boolean = false) =
            WeChatMessage.fromTransient(room, sender, text, time, outgoing, type, time, "", "self")
        val image = message("alice", "<msg><img/></msg>", 1000, 3)
        assertTrue(AiChatRuntime.shouldAttachPreviousImage(message("alice", "这个是什么", 2000, 1), image))
        assertFalse(AiChatRuntime.shouldAttachPreviousImage(message("bob", "这个是什么", 2000, 1), image))
        assertFalse(AiChatRuntime.shouldAttachPreviousImage(message("alice", "明天见", 2000, 1), image))
        assertFalse(AiChatRuntime.shouldAttachPreviousImage(message("alice", "这个是什么", 20_000, 1), image))
        val quote = message("alice", "<msg><appmsg><title>这是什么</title><type>57</type><refermsg><type>3</type><svrid>12345</svrid><fromusr>$room</fromusr><chatusr>bob</chatusr></refermsg></appmsg></msg>", 3000, 49)
        assertEquals(12345L, quote.getQuoteMsg()!!.svrId)
        assertEquals("bob", quote.getQuoteMsg()!!.sendTalker)
        assertTrue(AiChatRuntime.missingQuotedMedia(quote, null))
        assertFalse(AiChatRuntime.missingQuotedMedia(quote, image))
    }

    @Test fun videoFilePathAndPayloadUseTheDocumentedInputFormat() {
        val file = File.createTempFile("ai-r2-video-", ".mp4", context.cacheDir)
        try {
            val fixture = byteArrayOf(0, 0, 0, 12, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 1, 2, 3, 4)
            file.writeBytes(fixture)
            ShadowMediaMetadataRetriever.addMetadata(file.absolutePath, MediaMetadataRetriever.METADATA_KEY_DURATION, "1000")
            ShadowMediaMetadataRetriever.addMetadata(file.absolutePath, MediaMetadataRetriever.METADATA_KEY_MIMETYPE, "video/mp4")
            val message = WeChatMessage(33, 43, 43, 0, 0, 1000, "friend", "", file.absolutePath, "", "", 0, "", "self")
            val payload = AiChatMedia.video(context, message)
            assertEquals("video_url", payload.getString("type")); assertEquals(1, payload.getInt("fps"))
            val encoded = payload.getJSONObject("video_url").getString("url")
            assertTrue(encoded.startsWith("data:video/mp4;base64,"))
            assertArrayEquals(fixture, android.util.Base64.decode(encoded.substringAfter(','), android.util.Base64.DEFAULT))
        } finally { file.delete(); ShadowMediaMetadataRetriever.reset() }
    }

    @Test fun replySplittingPreservesShortAnswersUrlsAndStopsOnFailure() {
        assertEquals(listOf("嗯"), AiChatOutput.messages("嗯"))
        assertEquals(listOf("啊？"), AiChatOutput.messages("啊？"))
        assertTrue(AiChatOutput.finalText("一\n\n二\n\n三\n\n四")!!.contains("四"))
        assertFalse(AiChatOutput.finalText("说明".repeat(149) + "3.14", 300).orEmpty().endsWith("3."))
        val answer = "第一句" + "内容".repeat(28) + "。第二句价格是 3.14，网址 https://example.org/a.b，" + "说明".repeat(24) + "。第三句" + "结论".repeat(22) + "。"
        val parts = AiChatOutput.messages(answer)
        assertTrue(parts.size in 2..3)
        assertTrue(parts.any { it.contains("3.14") && it.contains("https://example.org/a.b") })
        assertTrue(parts.all { it.codePointCount(0, it.length) >= 20 })
        val calls = mutableListOf<String>(); val saved = mutableListOf<String>()
        val submitted = AiChatOutput.sendInOrder(parts, { text, index -> calls += text; index == 0 }, { text, _ -> saved += text })
        assertEquals(1, submitted); assertEquals(2, calls.size); assertEquals(listOf(parts.first()), saved)
        parts.forEach { assertNotNull(ChatQuoteTailXml.build("friend", "self", it, "昵称", "本消息为AI回复")) }
        assertNull(AiChatOutput.finalText("<think>unfinished"))
    }

    @Test fun groupFactsTreatUnreadableCountAsUnknown() {
        val unknown = AiChatLocalContext.render(AiLocalFacts(true, memberCount = 0), false, true, true, false, false, false, false)
        assertTrue(unknown.contains("群成员数：本地不可读")); assertFalse(unknown.contains("群成员数：0"))
        val known = AiChatLocalContext.render(AiLocalFacts(true, roomName = "测试群", memberCount = 42, owner = "群主"), true, true, true, false, false, false, false)
        assertTrue(known.contains("42")); assertTrue(known.contains("测试群")); assertTrue(known.contains("可能不是服务器最新"))
    }

    @Test fun usageOverviewIsCompactAndUnknownsAreNotInvented() {
        val rows = listOf(AiUsageRecord("self", "id", AiProvider.DEEPSEEK, "deepseek-flash", AiPurpose.CHAT, 1,
            AiUsage(1000, 20, 500, 0, 0, 0), "已返回"),
            AiUsageRecord("self", "custom", AiProvider.CUSTOM, "relay", AiPurpose.CHAT, 2, AiUsage(1, 1, null), "已返回"))
        assertTrue(AiChatUsage.costSummary(rows).contains("部分未计入"))
        assertTrue(AiChatUsage.cacheSummary(rows).contains("50.0%"))
        assertTrue(AiChatUsage.cacheSummary(rows).contains("部分未计入"))
        assertEquals("未知", AiChatUsage.cacheSummary(emptyList()))
    }

    @Test fun databaseUpgradePreservesUsageAndAddsSeparateSearchCredits() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE usage(id INTEGER PRIMARY KEY, input INTEGER)")
            db.execSQL("INSERT INTO usage VALUES (1, 12)")
            AiChatMemory.get(context).onUpgrade(db, 2, 3)
            db.rawQuery("SELECT input FROM usage", null).use { assertTrue(it.moveToFirst()); assertEquals(12, it.getInt(0)) }
            db.rawQuery("SELECT COUNT(*) FROM search_usage", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun searchIntentIsSelectiveAndBasicRequestIncludesUsage() {
        assertNull(AiChatSearch.query("今天吃什么")); assertNull(AiChatSearch.query("你好"))
        assertEquals("最新价格", AiChatSearch.query("#搜索 最新价格"))
        assertEquals("最新价格", AiChatSearch.query("@机器人 #搜索 最新价格"))
        assertEquals("最新价格", AiChatSearch.query("@机器人\u2005#搜索 最新价格"))
        assertNotNull(AiChatSearch.query("今天的黄金价格是多少"))
        val request = AiChatSearch.requestBody("latest price")
        assertEquals("basic", request.getString("search_depth")); assertEquals(4, request.getInt("max_results"))
        assertFalse(request.getBoolean("include_answer")); assertFalse(request.getBoolean("include_raw_content")); assertTrue(request.getBoolean("include_usage"))
        assertFalse(request.getBoolean("auto_parameters"))
    }

    @Test fun searchSuccessIsNotInventedWhenUpstreamDidNotRunATool() {
        val config = AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash", webSearch = true)
        val response = JSONObject("""{"choices":[{"message":{"content":"查到了。"}}]}""")
        val withoutResults = AiChatClient.parseResponse(config, response)
        assertFalse(withoutResults.webSucceeded); assertTrue(withoutResults.text.contains("暂时没有查到"))
        val external = AiChatClient.parseResponse(config, response, externalSources = true)
        assertTrue(external.webSucceeded); assertEquals("查到了。", external.text)
    }

    @Test fun tavilyMockChecksQuotaRecordsCreditsAndReusesResults() {
        val captured = mutableListOf<Pair<String, String>>()
        val quota = """{"account":{"plan_usage":5,"plan_limit":1000}}"""
        val result = """{"results":[{"title":"News","url":"https://example.org/item","content":"Useful summary","published_date":"2026-11-16"}],"usage":{"credits":1}}"""
        serve(listOf("200 OK" to quota, "200 OK" to result), captured) { base ->
            val records = mutableListOf<Pair<Long?, String>>()
            val account = UUID.randomUUID().toString()
            val first = AiChatSearch.search(account, "friend", "mock-key", "latest price", base) { credits, status -> records += credits to status }
            val second = AiChatSearch.search(account, "friend", "mock-key", "latest price", base) { credits, status -> records += credits to status }
            assertEquals(first, second); assertEquals("example.org", first.source); assertEquals(listOf(1L to "已返回"), records)
        }
        assertEquals(2, captured.size); assertTrue(captured[0].first.startsWith("GET /usage")); assertTrue(captured[1].first.startsWith("POST /search"))
        assertEquals("basic", JSONObject(captured[1].second).getString("search_depth"))
    }

    @Test fun tavilyQuotaAndFailureNeverFallBackToPaidSearch() {
        val quotaOnly = mutableListOf<Pair<String, String>>()
        serve(listOf("200 OK" to """{"account":{"plan_usage":1000,"plan_limit":1000}}"""), quotaOnly) { base ->
            val records = mutableListOf<Long?>()
            val error = runCatching { AiChatSearch.search(UUID.randomUUID().toString(), "friend", "mock-key", "latest price", base) { c, _ -> records += c } }.exceptionOrNull()
            assertTrue(error!!.message!!.contains("额度")); assertTrue(records.isEmpty())
        }
        assertEquals(1, quotaOnly.size)
        val requests = mutableListOf<Pair<String, String>>()
        serve(listOf("200 OK" to """{"account":{"plan_usage":0,"plan_limit":1000}}""", "432 Error" to "{}"), requests) { base ->
            val records = mutableListOf<Long?>()
            val error = runCatching { AiChatSearch.search(UUID.randomUUID().toString(), "friend", "mock-key", "latest price", base) { c, _ -> records += c } }.exceptionOrNull()
            assertTrue(error!!.message!!.contains("额度")); assertEquals(listOf(null), records)
        }
        assertEquals(2, requests.size)
    }

    private fun serve(responses: List<Pair<String, String>>, captured: MutableList<Pair<String, String>>, block: (String) -> Unit) {
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val worker = thread(isDaemon = true) {
                responses.forEach { (status, json) ->
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                        val line = reader.readLine(); var length = 0
                        while (true) { val header = reader.readLine() ?: break; if (header.isEmpty()) break; if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt() }
                        val body = CharArray(length); var n = 0
                        while (n < length) { val read = reader.read(body, n, length - n); if (read < 0) break; n += read }
                        synchronized(captured) { captured += line to String(body, 0, n) }
                        val bytes = json.toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.getOutputStream().write(bytes)
                    }
                }
            }
            block("http://127.0.0.1:${server.localPort}")
            worker.join(5000)
            assertFalse("mock server did not finish", worker.isAlive)
        }
    }
}
