package h.Hchat.hooks.items.aichat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.quotetail.ChatQuoteTailXml
import h.Hchat.preferences.HchatStorage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatR1Test {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPreferences() {
        HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME).edit().clear().commit()
        HchatStorage.preferences(context, AiChatSettings.PREFS_NAME).edit().clear().commit()
    }

    @Test fun fiveTurnsAndReopenKeepAssistantTextFreeOfOldMarkersAndTail() {
        val self = UUID.randomUUID().toString(); val talker = "room@chatroom"
        val db = AiChatMemory.get(context)
        val messages = (1..5).map { n ->
            val time = 1_000_000L + n * 30_000L
            val old = "[AI回复] [AI回复] 第${n}次回答"
            val xml = ChatQuoteTailXml.build(talker, self, old, "昵称", "本消息为AI回复")!!
            db.rememberSent(self, talker, old, time)
            WeChatMessage(n.toLong(), (n + 100).toLong(), 49, 0, 1, time, talker, xml, "", "", "", 0, "", self)
        }
        fun assertClean() {
            val history = AiChatHistory.recent(messages, self, db, 7000)
            assertEquals(5, history.size)
            history.forEachIndexed { i, message ->
                assertEquals("assistant", message.role)
                assertEquals("第${i + 1}次回答", message.content)
                assertFalse(message.content.toString().contains("本消息为AI回复"))
            }
        }
        assertClean()
        db.close()
        assertClean()
        val last = messages.last()
        val manual = WeChatMessage(999, 999, 1, 0, 1, last.createTime + 1000, talker,
            "[AI回复] [AI回复] 第5次回答", "", "", "", 0, "", self)
        val history = AiChatHistory.recent(listOf(manual), self, db, 1000).single()
        assertEquals("user", history.role)
        assertEquals("[AI回复] [AI回复] 第5次回答", history.content)
    }

    @Test fun groupMembersUseSeparateNamesWithoutPollutingAssistantContent() {
        val self = UUID.randomUUID().toString(); val db = AiChatMemory.get(context)
        val a = WeChatMessage.fromTransient("room@chatroom", "wxid_a", "你好", 1000, false, 1, 10, "", self)
        val b = WeChatMessage.fromTransient("room@chatroom", "wxid_b", "你好", 2000, false, 1, 11, "", self)
        val history = AiChatHistory.recent(listOf(a, b), self, db, 1000)
        assertEquals("你好", history[0].content); assertEquals("你好", history[1].content)
        assertNotEquals(history[0].name, history[1].name)
        val request = AiChatClient.requestBody(AiChatConfig(model = "deepseek-flash"), history, 256)
        assertEquals(history[0].name, request.getJSONArray("messages").getJSONObject(0).getString("name"))
    }

    @Test fun outputGuardKeepsLanguageAndExcludesThoughtsAndOverflow() {
        assertEquals("你好呀！", AiChatOutput.finalText("[AI回复][AI回复] <think>secret</think>你好呀！"))
        assertEquals("Hello there!", AiChatOutput.finalText("Hello there!"))
        assertNull(AiChatOutput.finalText("<think>not finished"))
        assertNull(AiChatOutput.finalText("<think>only thought</think>"))
        val limited = AiChatOutput.finalText("好".repeat(199) + "👨‍👩‍👧‍👦" + "多".repeat(300))!!
        assertTrue(limited.codePointCount(0, limited.length) <= 200)
        assertFalse(limited.endsWith("‍"))
        assertEquals("一 二\n\n三 四", AiChatOutput.finalText("一\n二\n\n三\n四\n\n五"))
        val db = AiChatMemory.get(context); val account = UUID.randomUUID().toString()
        db.saveMedia(account, "friend", 1, "[AI回复][AI回复] 一张图片")
        assertEquals("一张图片", db.media(account, "friend", 1))
    }

    @Test fun sustainedInputCannotDelayAMergedBatchPastSixSeconds() {
        assertEquals(1500, AiChatRuntime.debounceWait(0, 0, 0))
        assertEquals(1000, AiChatRuntime.debounceWait(0, 5000, 5000))
        assertEquals(0, AiChatRuntime.debounceWait(0, 6000, 6000))
        assertEquals(0, AiChatRuntime.debounceWait(0, 3000, 5000))
    }

    @Test fun audioAttachmentsUseProviderFormatAndRejectRawSilk() {
        val xml = "<msg><appmsg><title>sound.mp3</title><type>6</type><appattach><fileext>mp3</fileext></appattach></appmsg></msg>"
        val message = WeChatMessage.fromTransient("friend", "friend", xml, 1_000, false, 49, 1, "", "self")
        assertTrue(AiChatMedia.isAudioFile(message))
        assertFalse(AiChatMedia.isAudioFile(WeChatMessage.fromTransient("friend", "friend", "hello", 1_000, false, 1, 2, "", "self")))
        val mp3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0)
        assertTrue(AiChatMedia.audioHeaderMatches(mp3, "mp3"))
        assertFalse(AiChatMedia.audioHeaderMatches("#!SILK_V3".toByteArray(), "mp3"))
        assertFalse(AiChatMedia.audioHeaderMatches("RIFFxxxxNOPE".toByteArray(), "wav"))
        val mimo = AiChatMedia.audioPayload(mp3, "mp3", AiProvider.MIMO).getJSONObject("input_audio")
        assertTrue(mimo.getString("data").startsWith("data:audio/mpeg;base64,")); assertFalse(mimo.has("format"))
        val relay = AiChatMedia.audioPayload(mp3, "mp3", AiProvider.CUSTOM).getJSONObject("input_audio")
        assertEquals("mp3", relay.getString("format")); assertFalse(relay.getString("data").contains("base64,"))
    }

    @Test fun officialSearchToolsDifferAndInternalRequestsNeverEnableThem() {
        val inputs = listOf(AiInput("user", "看看 https://example.com"))
        val deep = AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash", webSearch = true)
        val mimo = AiChatConfig(provider = AiProvider.MIMO, model = "mimo-v2.6-flash", webSearch = true)
        val d = AiChatClient.requestBody(deep, inputs, 256)
        assertEquals("deepseek_web_search", d.getJSONArray("tools").getJSONObject(0).getString("type"))
        assertEquals("deepseek_read_url", d.getJSONArray("tools").getJSONObject(1).getString("type"))
        val m = AiChatClient.requestBody(mimo, inputs, 256).getJSONArray("tools").getJSONObject(0)
        assertEquals("web_search", m.getString("type")); assertEquals(1, m.getInt("max_keyword")); assertFalse(m.getBoolean("force_search"))
        assertFalse(AiChatClient.requestBody(deep, inputs, 1200, AiPurpose.MEMORY).has("tools"))
        assertFalse(AiChatClient.requestBody(mimo, inputs, 16, AiPurpose.TEST).has("tools"))
        assertFalse(AiChatClient.supportsWebSearch(deep.copy(model = "deepseek-v4-pro")))
        assertFalse(AiChatClient.supportsWebSearch(deep.copy(provider = AiProvider.CUSTOM)))
    }

    @Test fun webResponseSeparatesAnswerSourcesThoughtsAndErrors() {
        val config = AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash", webSearch = true)
        val response = JSONObject("""{"choices":[{"finish_reason":"length","message":{"content":"<think>private</think>找到了。","reasoning_content":"hidden", "annotations":[{"url_citation":{"url":"https://example.org/a"}}],"server_tool_calls":[{"calls":[{"type":"deepseek_web_search","status":"completed"}]}]}}],"usage":{"prompt_tokens":100,"completion_tokens":20,"prompt_cache_hit_tokens":40,"server_tool_use":{"tool_search":1}}}""")
        val result = AiChatClient.parseResponse(config, response)
        assertEquals("找到了。", result.text); assertEquals("length", result.finishReason)
        assertEquals("example.org", result.source); assertEquals(1L, result.usage.search)
        assertTrue(result.serverToolCalls.contains("deepseek_web_search"))
        val failed = JSONObject(response.toString())
        failed.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("server_tool_calls").getJSONObject(0).getJSONArray("calls").getJSONObject(0).put("status", "failed")
        assertTrue(runCatching { AiChatClient.parseResponse(config, failed) }.isFailure)
        val mimo = config.copy(provider = AiProvider.MIMO, model = "mimo-v2.6-flash")
        val error = JSONObject("""{"error":{"message":"plugin not enabled"},"usage":{"prompt_tokens":3,"completion_tokens":0}}""")
        assertTrue(runCatching { AiChatClient.parseResponse(mimo, error) }.exceptionOrNull()!!.message!!.contains("控制台"))
    }

    @Test fun coldStartResponseDoesNotExposeReasoningFieldsOrBlocks() {
        val cfg = AiChatConfig(provider = AiProvider.MIMO, model = "mimo-v2.6-pro")
        val response = JSONObject("""{"choices":[{"finish_reason":"stop","message":{"reasoning_content":"hidden thought","content":[{"type":"thinking","text":"private"},{"type":"text","text":"你说得对。"}]}}],"usage":{"prompt_tokens":10,"completion_tokens":4,"completion_tokens_details":{"reasoning_tokens":2}}}""")
        assertEquals("你说得对。", AiChatClient.parseResponse(cfg, response).text)
        response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("content", "<think>unfinished")
        assertTrue(runCatching { AiChatClient.parseResponse(cfg, response) }.isFailure)
    }

    @Test fun usageCoverageAndPricesDoNotAveragePercentagesOrMixCurrencies() {
        val now = System.currentTimeMillis()
        fun row(provider: AiProvider, model: String, purpose: AiPurpose, usage: AiUsage) =
            AiUsageRecord("self", UUID.randomUUID().toString(), provider, model, purpose, now, usage, "已返回")
        val rows = listOf(
            row(AiProvider.DEEPSEEK, "deepseek-flash", AiPurpose.CHAT, AiUsage(100, 10, 100, 1, 2, 3)),
            row(AiProvider.DEEPSEEK, "deepseek-flash", AiPurpose.MEMORY, AiUsage(900, 20, 0, 0, 0, 0)),
            row(AiProvider.MIMO, "mimo-v2.6-pro", AiPurpose.TEST, AiUsage(50, 5, null, 0))
        )
        val totals = AiChatUsage.totals(rows)
        assertEquals(1000, totals.cacheInput); assertEquals(100, totals.cacheHit); assertEquals(1, totals.unknown)
        assertEquals(2, totals.fetch); assertEquals(3, totals.imageSearch); assertEquals(0, totals.deepseekToolUnknown)
        assertTrue(AiChatUsage.description(rows).contains("读网页 2 次、图片搜索 3 次"))
        val costs = AiChatUsage.estimate(rows).associateBy { it.currency }
        assertTrue(costs.containsKey("USD")); assertTrue(costs.containsKey("CNY")); assertTrue(costs["USD"]!!.maximum >= costs["USD"]!!.minimum)
        assertTrue(costs["CNY"]!!.incomplete)
    }

    @Test fun placeholderMigrationSkipsOnlyKnownBlankDefault() {
        val old = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        old.edit().putString(AutoReplySettings.KEY_AI_API_KEY, "").putString(AutoReplySettings.KEY_AI_MODEL, AutoReplySettings.DEFAULT_AI_MODEL).commit()
        assertNull(AiChatSettings(context).activeConfig())
        HchatStorage.preferences(context, AiChatSettings.PREFS_NAME).edit().clear().commit()
        val saved = JSONObject().put("个人配置", JSONObject().put("apiKey", "").put("apiUrl", "https://relay.example/v1").put("modelName", "custom-model"))
        old.edit().putString(AutoReplySettings.KEY_ZHILIA_CONFIGS, saved.toString()).putString(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, "个人配置").commit()
        assertEquals("custom-model", AiChatSettings(context).activeConfig()?.model)
        val settings = AiChatSettings(context)
        val placeholder = AiChatConfig(name = AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME, provider = AiProvider.CUSTOM,
            baseUrl = AutoReplySettings.DEFAULT_AI_API_BASE, model = AutoReplySettings.DEFAULT_AI_MODEL)
        val actual = AiChatConfig(name = "MiMo", provider = AiProvider.MIMO, model = "mimo-v2.6-flash")
        old.edit().putString(AutoReplySettings.KEY_ZHILIA_CONFIGS, JSONObject().put(AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME,
            JSONObject().put("apiKey", "").put("apiUrl", AutoReplySettings.DEFAULT_AI_API_BASE).put("modelName", AutoReplySettings.DEFAULT_AI_MODEL)).toString()).commit()
        settings.saveConfigs(listOf(placeholder, actual), placeholder.id)
        assertEquals(listOf(actual.id), settings.configs().map { it.id })
        assertNull("Removing a known placeholder must not silently select another configuration", settings.activeConfig())

        HchatStorage.preferences(context, AiChatSettings.PREFS_NAME).edit().clear().commit()
        val customized = JSONObject().put(AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME, JSONObject()
            .put("apiKey", "").put("apiUrl", AutoReplySettings.DEFAULT_AI_API_BASE)
            .put("modelName", AutoReplySettings.DEFAULT_AI_MODEL).put("systemPrompt", "我的自定义人格"))
        old.edit().putString(AutoReplySettings.KEY_ZHILIA_CONFIGS, customized.toString())
            .putString(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME).commit()
        val migrated = AiChatSettings(context)
        assertNotNull(migrated.activeConfig())
        assertEquals("我的自定义人格", migrated.prefs.getString(AiChatSettings.KEY_PROMPT, ""))
    }

    @Test fun defaultPromptUpgradesWithoutOverwritingCustomInstruction() {
        val prefs = HchatStorage.preferences(context, AiChatSettings.PREFS_NAME)
        prefs.edit().putString(AiChatSettings.KEY_PROMPT, "你是一个自然、简洁、有帮助的微信聊天助手。").commit()
        assertTrue(AiChatSettings(context).prefs.getString(AiChatSettings.KEY_PROMPT, "")!!.contains("玩笑"))
        prefs.edit().clear().putString(AiChatSettings.KEY_PROMPT, "我的自定义人设").commit()
        assertEquals("我的自定义人设", AiChatSettings(context).prefs.getString(AiChatSettings.KEY_PROMPT, ""))
        assertTrue(AiChatSettings.DEFAULT_PROMPT.contains("对方要求英文"))
    }

    @Test fun usageRemainsBoundToOriginalConfigurationAndSurvivesMemoryClear() {
        val db = AiChatMemory.get(context); val account = UUID.randomUUID().toString()
        val first = AiChatConfig(id = "first", provider = AiProvider.DEEPSEEK, model = "deepseek-flash")
        val second = AiChatConfig(id = "second", provider = AiProvider.MIMO, model = "mimo-v2.6-pro")
        db.recordUsage(account, first, AiPurpose.CHAT, 1, AiUsage(100, 20, 50, 0, 0, 0))
        db.recordUsage(account, second, AiPurpose.MEMORY, 2, AiUsage(200, 30, null, 0))
        db.recordUsage(account, first, AiPurpose.TEST, 3, null)
        db.clear(account, "friend", 100, 1)
        val rows = db.usage(account)
        assertEquals(3, rows.size)
        assertEquals("first", rows.first().configId); assertEquals("用量未知", rows.first().status)
        assertEquals(AiPurpose.MEMORY, rows[1].purpose); assertEquals("mimo-v2.6-pro", rows[1].model)
        assertEquals(50L, rows.last().usage?.cached)
    }

    @Test fun databaseUpgradeKeepsExistingSentRecordsAndAddsUsageStorage() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE sent(account TEXT NOT NULL,talker TEXT NOT NULL,fingerprint TEXT NOT NULL,submitted_at INTEGER NOT NULL,source TEXT NOT NULL)")
            db.execSQL("INSERT INTO sent VALUES ('self','friend','hash',1,'AI回复')")
            AiChatMemory.get(context).onUpgrade(db, 1, 2)
            db.rawQuery("SELECT source,local_id,server_id FROM sent", null).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("AI回复", cursor.getString(0))
                assertEquals(0, cursor.getLong(1)); assertEquals(0, cursor.getLong(2))
            }
            db.rawQuery("SELECT COUNT(*) FROM usage", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun chargedErrorAndMissingFinalAnswerAreRecordedOnce() {
        fun call(status: String, json: String): Pair<Int, AiUsage?> {
            ServerSocket(0).use { server ->
                val worker = thread(isDaemon = true) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                        var length = 0
                        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt() }
                        var remaining = length
                        while (remaining > 0) { if (reader.read() < 0) break; remaining-- }
                        val bytes = json.toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.getOutputStream().write(bytes)
                    }
                }
                var count = 0; var recorded: AiUsage? = null
                val config = AiChatConfig(provider = AiProvider.CUSTOM, baseUrl = "http://127.0.0.1:${server.localPort}", apiKey = "mock", model = "relay")
                runCatching { AiChatClient.complete(config, listOf(AiInput("user", "hello"))) { usage -> count++; recorded = usage } }
                worker.join(3000)
                return count to recorded
            }
        }
        val error = call("400 Bad Request", """{"error":{"message":"bad"},"usage":{"prompt_tokens":12,"completion_tokens":4}}""")
        assertEquals(1, error.first); assertEquals(12L, error.second?.input)
        val noAnswer = call("200 OK", """{"choices":[{"message":{"content":"<think>private</think>"}}],"usage":{"prompt_tokens":8,"completion_tokens":2}}""")
        assertEquals(1, noAnswer.first); assertEquals(8L, noAnswer.second?.input)
    }
}
