package h.Hchat.hooks.items.aichat

import android.content.Context
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.items.autoreply.AutoReplySettings
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
import java.util.UUID
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPreferences() {
        HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME).edit().clear().commit()
        HchatStorage.preferences(context, AiChatSettings.PREFS_NAME).edit().clear().commit()
    }

    @Test fun providerRequestsUseCorrectOutputFieldsAndDisableThinking() {
        val messages = listOf(AiInput("system", "角色"), AiInput("user", "问题"))
        val deepseek = AiChatClient.requestBody(AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash"), messages, 512)
        val mimo = AiChatClient.requestBody(AiChatConfig(provider = AiProvider.MIMO, model = "mimo-v2.6-pro"), messages, 512)
        val custom = AiChatClient.requestBody(AiChatConfig(provider = AiProvider.CUSTOM, model = "relay"), messages, 512)
        assertEquals("disabled", deepseek.getJSONObject("thinking").getString("type"))
        assertEquals("disabled", mimo.getJSONObject("thinking").getString("type"))
        assertEquals(512, deepseek.getInt("max_tokens")); assertFalse(deepseek.has("max_completion_tokens"))
        assertEquals(512, mimo.getInt("max_completion_tokens")); assertFalse(mimo.has("max_tokens"))
        assertFalse(custom.has("thinking")); assertFalse(custom.getBoolean("stream"))
        assertEquals("问题", custom.getJSONArray("messages").getJSONObject(1).getString("content"))
    }

    @Test fun customRelayMakesOneNonStreamingRequest() {
        ServerSocket(0).use { server ->
            val body = AtomicReference<String>()
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                    var length = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    val chars = CharArray(length)
                    var read = 0
                    while (read < chars.size) { val n = input.read(chars, read, chars.size - read); if (n < 0) break; read += n }
                    body.set(String(chars, 0, read))
                    val payload = """{"choices":[{"message":{"content":"OK"}}],"usage":{"prompt_tokens":2,"completion_tokens":1}}"""
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    socket.getOutputStream().write(bytes)
                }
            }
            val config = AiChatConfig(provider = AiProvider.CUSTOM, baseUrl = "http://127.0.0.1:${server.localPort}", apiKey = "mock", model = "relay")
            val result = AiChatClient.complete(config, listOf(AiInput("user", "hello")))
            worker.join(3000)
            assertEquals("OK", result.text)
            assertNull(result.usage.cached)
            assertFalse(JSONObject(body.get()).getBoolean("stream"))
        }
    }

    @Test fun lostResponseDoesNotAutomaticallyGenerateAgain() {
        ServerSocket(0).use { server ->
            server.soTimeout = 1500
            val count = java.util.concurrent.atomic.AtomicInteger()
            val worker = thread(isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        count.incrementAndGet()
                        val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        }
                        val chars = CharArray(length)
                        var read = 0
                        while (read < chars.size) { val n = reader.read(chars, read, chars.size - read); if (n < 0) break; read += n }
                    }
                    try { server.accept().use { count.incrementAndGet() } } catch (_: SocketTimeoutException) {}
                } catch (_: Exception) {}
            }
            val config = AiChatConfig(provider = AiProvider.CUSTOM, baseUrl = "http://127.0.0.1:${server.localPort}", apiKey = "mock", model = "relay")
            assertTrue(runCatching { AiChatClient.complete(config, listOf(AiInput("user", "hello"))) }.isFailure)
            worker.join(3000)
            assertEquals(1, count.get())
        }
    }

    @Test fun modelsAndUsageRespectProviderMetadata() {
        val body = """{"data":[{"id":"deepseek-flash","input_modalities":["text","image"],"context_window":128000},{"id":"deepseek-v4-pro","input_modalities":["text"]}]}"""
        val models = AiChatClient.parseModels(body, AiProvider.DEEPSEEK)
        val cfg = AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash", models = models)
        assertTrue(cfg.supports("image")); assertFalse(cfg.copy(model = "deepseek-v4-pro").supports("image"))
        assertFalse(cfg.copy(model = "unknown").supports("image"))
        val mimo = AiChatClient.parseModels("""{"data":[{"id":"mimo-v2.5-asr"},{"id":"mimo-v2.5-tts"},{"id":"mimo-v2.6-flash"}]}""", AiProvider.MIMO)
        assertEquals(listOf("mimo-v2.6-flash"), mimo.map { it.id })
        assertEquals(AiUsage(12, 3, 7), AiChatClient.parseUsage(JSONObject("""{"prompt_tokens":12,"completion_tokens":3,"prompt_cache_hit_tokens":7}""")))
        assertEquals(AiUsage(12, 3, 5), AiChatClient.parseUsage(JSONObject("""{"prompt_tokens":12,"completion_tokens":3,"prompt_tokens_details":{"cached_tokens":5}}""")))
        assertNull(AiChatClient.parseUsage(JSONObject("{}" )).cached)
    }

    @Test fun migrationKeepsLegacyRulesAndDoesNotEnableNewFeature() {
        val old = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        val rules = "[{\"id\":\"legacy-rule\",\"sentinel\":\"unchanged\"}]"
        val configs = JSONObject().put("旧服务", JSONObject().put("apiKey", "legacy-key").put("apiUrl", "https://relay.example/v1")
            .put("apiPath", "/chat/completions").put("modelName", "special-model").put("systemPrompt", "旧指令"))
        val favorites = JSONObject().put("https://relay.example/v1", JSONArray().put("special-model")).toString()
        old.edit().putString(AutoReplySettings.KEY_RULES, rules).putString(AutoReplySettings.KEY_ZHILIA_CONFIGS, configs.toString())
            .putString(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, "旧服务").putString(AutoReplySettings.KEY_ZHILIA_MODEL_FAVORITES, favorites).commit()
        val settings = AiChatSettings(context)
        assertFalse(settings.prefs.getBoolean(AiChatSettings.KEY_ENABLE, false))
        val config = settings.activeConfig()!!
        assertEquals("legacy-key", config.apiKey); assertEquals("special-model", config.model); assertEquals("旧服务", config.name)
        assertEquals("旧指令", settings.prefs.getString(AiChatSettings.KEY_PROMPT, ""))
        assertTrue("special-model" in settings.favorites("https://relay.example/v1"))
        assertEquals(rules, old.getString(AutoReplySettings.KEY_RULES, "")); assertEquals(configs.toString(), old.getString(AutoReplySettings.KEY_ZHILIA_CONFIGS, ""))
        assertTrue(settings.targets("self").isEmpty())
        old.edit().putString(AutoReplySettings.KEY_AI_API_KEY, "later").commit()
        assertEquals("legacy-key", AiChatSettings(context).activeConfig()?.apiKey)
    }

    @Test fun migratedOfficialFavoritesRemainVisibleAtTheManagedEndpoint() {
        val old = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        val config = JSONObject().put("deep", JSONObject().put("apiKey", "key").put("apiUrl", "https://api.deepseek.com/v1")
            .put("apiPath", "/chat/completions").put("modelName", "deepseek-flash").put("contextLimit", 0))
        val favorites = JSONObject().put("https://api.deepseek.com/v1", JSONArray().put("deepseek-flash"))
        old.edit().putString(AutoReplySettings.KEY_ZHILIA_CONFIGS, config.toString())
            .putString(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, "deep").putString(AutoReplySettings.KEY_ZHILIA_MODEL_FAVORITES, favorites.toString()).commit()
        val settings = AiChatSettings(context)
        assertEquals(AiProvider.DEEPSEEK, settings.activeConfig()?.provider)
        assertTrue("deepseek-flash" in settings.favorites("https://api.deepseek.com"))
        assertEquals(0, settings.prefs.getInt(AiChatSettings.KEY_CONTEXT, -1))
        assertEquals(favorites.toString(), old.getString(AutoReplySettings.KEY_ZHILIA_MODEL_FAVORITES, ""))
    }

    @Test fun targetsAndMemoryAreScopedAndClearRejectsStaleBatch() {
        val settings = AiChatSettings(context)
        settings.saveTargets("self1", listOf(AiChatTarget("room@chatroom", prompt = "私有指令")))
        assertTrue(settings.targets("self2").isEmpty())
        assertTrue(settings.targets("self1").single().quoteMe)
        val db = AiChatMemory.get(context)
        val account = UUID.randomUUID().toString(); val talker = "friend"
        val old = db.state(account, talker)
        assertTrue(db.commitBatch(account, talker, old, 100, 1, 1, "old summary", "old fact", AiUsage(10, 2, 3)))
        val inFlight = db.state(account, talker)
        db.clear(account, talker, 200, 2)
        assertFalse(db.commitBatch(account, talker, inFlight, 300, 3, 1, "stale", "stale", null))
        assertEquals("", db.state(account, talker).facts)
        assertEquals(200, db.state(account, talker).time)
        assertTrue(db.state(account, talker).initialDone)
        assertTrue(db.summaries(account, talker).isEmpty())
    }

    @Test fun interruptedMemoryRequestRequiresExplicitRetry() {
        val db = AiChatMemory.get(context)
        val account = UUID.randomUUID().toString(); val talker = "friend"
        val started = db.beginBatch(account, talker, db.state(account, talker))!!
        assertTrue(db.state(account, talker).pending)
        assertNull(db.beginBatch(account, talker, db.state(account, talker)))
        db.failBatch(account, talker, started, "timeout")
        assertEquals("timeout", db.state(account, talker).error)
        assertNull(db.beginBatch(account, talker, db.state(account, talker)))
        db.retryBatch(account, talker)
        val retried = db.beginBatch(account, talker, db.state(account, talker))!!
        assertTrue(db.commitBatch(account, talker, retried, 100, 7, 1, "摘要", "记忆", AiUsage(5, 2, null)))
        assertFalse(db.state(account, talker).pending)
        assertEquals("记忆", db.state(account, talker).facts)
    }

    @Test fun editingMemoryWhileARequestIsRunningPreservesTheEditAndUnblocksProgress() {
        val db = AiChatMemory.get(context)
        val account = UUID.randomUUID().toString(); val talker = "friend"
        val started = db.beginBatch(account, talker, db.state(account, talker))!!
        db.editFacts(account, talker, "手动编辑的记忆")
        assertFalse(db.commitBatch(account, talker, started, 100, 7, 1, "旧摘要", "旧记忆", AiUsage(5, 2, null)))
        val state = db.state(account, talker)
        assertEquals("手动编辑的记忆", state.facts)
        assertFalse(state.pending)
        assertTrue(state.error.contains("手动继续"))
        assertEquals(5, state.input)
        assertEquals(2, state.output)
        assertTrue(db.summaries(account, talker).isEmpty())

        db.retryBatch(account, talker)
        val failed = db.beginBatch(account, talker, db.state(account, talker))!!
        db.editFacts(account, talker, "再次编辑")
        db.failBatch(account, talker, failed, "网络错误")
        assertFalse(db.state(account, talker).pending)
        assertEquals("再次编辑", db.state(account, talker).facts)
        assertTrue(db.state(account, talker).error.contains("手动继续"))
    }

    @Test fun incrementalMemoryWaitsForAUsefulBatch() {
        assertFalse(AiChatHistory.shouldSummarizeIncrement(1, 10))
        assertFalse(AiChatHistory.shouldSummarizeIncrement(4, 3000))
        assertTrue(AiChatHistory.shouldSummarizeIncrement(5, 1500))
        assertTrue(AiChatHistory.shouldSummarizeIncrement(30, 200))
        val db = AiChatMemory.get(context)
        val account = UUID.randomUUID().toString()
        db.finishInitialScan(account, "friend", db.state(account, "friend"))
        assertTrue(db.state(account, "friend").initialDone)
    }

    @Test fun zeroBudgetDoesNotIncludePriorHistory() {
        val memory = AiChatMemory.get(context)
        val old = WeChatMessage.fromTransient("friend", "friend", "旧问题", 1000, false, 1, 1, "", "self")
        assertTrue(AiChatHistory.recent(listOf(old), "self", memory, 0).isEmpty())
    }

    @Test fun relatedMemoryFindsRelevantOlderFacts() {
        val facts = "[旧] 小李喜欢清蒸鲈鱼和海鲜\n[新] 小王最近准备考试"
        assertTrue(AiChatHistory.relatedMemory(facts, "小李喜欢什么海鲜", "").contains("清蒸鲈鱼"))
        assertEquals("", AiChatHistory.relatedMemory(facts, "今天天气怎么样", ""))
        assertTrue(AiChatHistory.historicalTerms("上次吃海鲜是什么时候").contains("海鲜"))
        assertTrue(AiChatHistory.historicalTerms("今天吃海鲜").isEmpty())
    }

    @Test fun groupMentionAndQuoteRequireTheActualSelf() {
        val self = "wxid_self"; val room = "room@chatroom"
        fun msg(source: String, xml: String = "hello") = WeChatMessage.fromTransient(room, "wxid_other", xml, 1000, false, if (xml.startsWith("<")) 49 else 1, 0, source, self)
        assertTrue(msg("<msgsource><atuserlist>wxid_self</atuserlist></msgsource>").isAtMe(self))
        assertFalse(msg("<msgsource><atuserlist>notify@all</atuserlist></msgsource>").isAtMe(self))
        assertFalse(msg("<msgsource><atuserlist>wxid_other</atuserlist></msgsource>").isAtMe(self))
        fun quote(author: String) = "<msg><appmsg><title>继续</title><type>57</type><refermsg><type>49</type><svrid>0</svrid><fromusr>$room</fromusr><chatusr>$author</chatusr><content>本消息为AI回复</content></refermsg></appmsg></msg>"
        assertTrue(AiChatRuntime.quotesSelf(msg("", quote(self)), self))
        assertFalse(AiChatRuntime.quotesSelf(msg("", quote("wxid_other")), self))
        val target = AiChatTarget(room)
        assertTrue(AiChatRuntime.triggers(msg("<msgsource><atuserlist>wxid_self</atuserlist></msgsource>"), self, target))
        assertTrue(AiChatRuntime.triggers(msg("", quote(self)), self, target))
        assertFalse(AiChatRuntime.triggers(msg("", quote("wxid_other")), self, target))
        assertFalse(AiChatRuntime.triggers(msg("<msgsource><atuserlist>notify@all</atuserlist></msgsource>"), self, target))
        assertFalse(AiChatRuntime.triggers(msg(""), self, target))
        assertFalse(AiChatRuntime.triggers(msg("", quote(self)), self, target.copy(quoteMe = false)))
        val outgoing = WeChatMessage.fromTransient(room, self, "本人手动消息", 1000, true, 1, 1, "", self)
        assertFalse(AiChatRuntime.triggers(outgoing, self, target))
    }
}
