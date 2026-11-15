package h.Hchat.hooks.items.aichat

import android.content.Context
import android.content.SharedPreferences
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.preferences.HchatStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class AiProvider { DEEPSEEK, MIMO, CUSTOM }

internal data class AiModel(
    val id: String,
    val modalities: Set<String> = emptySet(),
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0
)

internal data class AiChatConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val apiKey: String = "",
    val baseUrl: String = "",
    val chatPath: String = "/chat/completions",
    val model: String = "",
    val image: Boolean = false,
    val audio: Boolean = false,
    val video: Boolean = false,
    val models: List<AiModel> = emptyList()
) {
    fun endpoint(): String = when (provider) {
        AiProvider.DEEPSEEK -> "https://api.deepseek.com"
        AiProvider.MIMO -> "https://api.xiaomimimo.com/v1"
        AiProvider.CUSTOM -> baseUrl.trim().trimEnd('/')
    }

    fun supports(modality: String): Boolean = when (provider) {
        AiProvider.CUSTOM -> when (modality) { "image" -> image; "audio" -> audio; "video" -> video; else -> false }
        AiProvider.DEEPSEEK -> models.firstOrNull { it.id == model }?.modalities?.contains(modality) == true
        // MiMo's model list currently omits modalities. Only documented V2.6 chat IDs are known.
        AiProvider.MIMO -> model.lowercase() in setOf("mimo-v2.6-flash", "mimo-v2.6-pro", "mimo-v2.6-pro-ultraspeed") &&
            modality in setOf("image", "audio", "video")
    }
}

internal data class AiChatTarget(
    val talker: String,
    val enabled: Boolean = true,
    val prompt: String = "",
    val atMe: Boolean = true,
    val quoteMe: Boolean = true,
    val paused: Boolean = false
)

internal class AiChatSettings(context: Context) {
    val prefs: SharedPreferences = HchatStorage.preferences(context, PREFS_NAME)
    private val legacy = AutoReplySettings(context)

    init { migrateLegacy() }

    private fun migrateLegacy() {
        synchronized(migrationLock) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val old = legacy.getHostPreferences()
        val hadOld = old != null && listOf(
            AutoReplySettings.KEY_ZHILIA_CONFIGS, AutoReplySettings.KEY_AI_API_KEY,
            AutoReplySettings.KEY_AI_API_BASE, AutoReplySettings.KEY_AI_MODEL,
            AutoReplySettings.KEY_AI_SYSTEM_PROMPT, AutoReplySettings.KEY_ZHILIA_MODEL_FAVORITES
        ).any { old.contains(it) }
        val editor = prefs.edit()
        if (hadOld) {
            // Read without invoking the legacy migration: its source keys must remain untouched.
            val raw = legacy.getString(AutoReplySettings.KEY_ZHILIA_CONFIGS, "")
            val oldConfigs = runCatching { JSONObject(raw) }.getOrNull()
            val configs = mutableListOf<AiChatConfig>()
            var activePrompt = legacy.getString(AutoReplySettings.KEY_AI_SYSTEM_PROMPT, AutoReplySettings.DEFAULT_AI_SYSTEM_PROMPT)
            var activeLimit = legacy.getInt(AutoReplySettings.KEY_AI_CONTEXT_LIMIT, AutoReplySettings.DEFAULT_AI_CONTEXT_LIMIT)
            val oldActive = legacy.getString(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME)
            oldConfigs?.keys()?.forEach { name ->
                val value = oldConfigs.optJSONObject(name) ?: return@forEach
                val base = value.optString("apiUrl", AutoReplySettings.DEFAULT_AI_API_BASE)
                val path = value.optString("apiPath", AutoReplySettings.DEFAULT_AI_API_PATH)
                configs += AiChatConfig(
                    name = name, provider = providerForUrl(base, path), apiKey = value.optString("apiKey", ""),
                    baseUrl = base, chatPath = path,
                    model = value.optString("modelName", AutoReplySettings.DEFAULT_AI_MODEL)
                )
                if (name == oldActive) {
                    activePrompt = value.optString("systemPrompt", activePrompt)
                    activeLimit = value.optInt("contextLimit", activeLimit)
                }
            }
            if (configs.isEmpty()) {
                val base = legacy.getString(AutoReplySettings.KEY_AI_API_BASE, AutoReplySettings.DEFAULT_AI_API_BASE)
                val path = legacy.getString(AutoReplySettings.KEY_AI_API_PATH, AutoReplySettings.DEFAULT_AI_API_PATH)
                configs += AiChatConfig(
                    name = oldActive, provider = providerForUrl(base, path),
                    apiKey = legacy.getString(AutoReplySettings.KEY_AI_API_KEY, ""), baseUrl = base,
                    chatPath = path,
                    model = legacy.getString(AutoReplySettings.KEY_AI_MODEL, AutoReplySettings.DEFAULT_AI_MODEL)
                )
            }
            editor.putString(KEY_CONFIGS, encodeConfigs(configs))
            editor.putString(KEY_ACTIVE, (configs.firstOrNull { it.name == oldActive } ?: configs.first()).id)
            editor.putString(KEY_PROMPT, activePrompt)
            editor.putInt(KEY_CONTEXT, if (activeLimit <= 0) 0 else (activeLimit.coerceAtMost(50) * 700).coerceAtMost(16000))
            val rawFavorites = legacy.getString(AutoReplySettings.KEY_ZHILIA_MODEL_FAVORITES, "")
            val favorites = runCatching { JSONObject(rawFavorites) }.getOrDefault(JSONObject())
            configs.forEach { config ->
                val source = config.baseUrl.trim().lowercase()
                val destination = config.endpoint().trim().lowercase()
                if (source != destination && favorites.has(source)) {
                    val existing = favorites.optJSONArray(destination)
                    val values = legacy.favoriteModels(config.baseUrl) + legacy.favoriteModels(config.endpoint()) +
                        (0 until (existing?.length() ?: 0)).map { existing?.optString(it).orEmpty() }.filter { it.isNotBlank() }
                    favorites.put(destination, JSONArray(values.sorted()))
                }
            }
            editor.putString(KEY_FAVORITES, favorites.toString())
        }
        editor.putBoolean(KEY_MIGRATED, true).commit()
        }
    }

    fun configs(): List<AiChatConfig> = decodeConfigs(prefs.getString(KEY_CONFIGS, "").orEmpty())
    fun activeConfig(): AiChatConfig? = configs().let { values ->
        values.firstOrNull { it.id == prefs.getString(KEY_ACTIVE, "") } ?: values.firstOrNull()
    }
    fun saveConfigs(values: List<AiChatConfig>, active: String) {
        prefs.edit().putString(KEY_CONFIGS, encodeConfigs(values)).putString(KEY_ACTIVE, active).apply()
    }
    fun targets(account: String): List<AiChatTarget> {
        if (account.isBlank()) return emptyList()
        val array = runCatching { JSONArray(prefs.getString(targetKey(account), "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o ->
            val id = o.optString("talker")
            if (id.isBlank()) null else AiChatTarget(id, o.optBoolean("enabled", true), o.optString("prompt"),
                o.optBoolean("atMe", true), o.optBoolean("quoteMe", true), o.optBoolean("paused", false))
        } }
    }
    fun saveTargets(account: String, targets: List<AiChatTarget>) {
        if (account.isBlank()) return
        val array = JSONArray()
        targets.distinctBy { it.talker }.forEach { target -> array.put(JSONObject().apply {
            put("talker", target.talker); put("enabled", target.enabled); put("prompt", target.prompt)
            put("atMe", target.atMe); put("quoteMe", target.quoteMe); put("paused", target.paused)
        }) }
        prefs.edit().putString(targetKey(account), array.toString()).apply()
    }
    fun favorites(base: String): Set<String> {
        val obj = runCatching { JSONObject(prefs.getString(KEY_FAVORITES, "")) }.getOrNull() ?: return emptySet()
        val key = base.trim().lowercase()
        val array = obj.optJSONArray(key)
        return if (array != null) (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.toSet()
        else obj.optString(key, "").split(";;;").filter { it.isNotBlank() }.toSet()
    }
    fun saveFavorites(base: String, values: Set<String>) {
        val obj = runCatching { JSONObject(prefs.getString(KEY_FAVORITES, "")) }.getOrDefault(JSONObject())
        obj.put(base.trim().lowercase(), JSONArray(values.sorted()))
        prefs.edit().putString(KEY_FAVORITES, obj.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = "Hchat_ai_chat_config"
        const val KEY_ENABLE = "enable"
        const val KEY_PROMPT = "global_prompt"
        const val KEY_TAIL = "quote_tail"
        const val KEY_CONTEXT = "context_budget"
        const val KEY_IMAGE = "image_enabled"
        const val KEY_VOICE = "voice_enabled"
        const val KEY_VIDEO = "video_enabled"
        const val KEY_NATIVE_AUDIO = "native_audio"
        const val KEY_STATUS = "status"
        const val KEY_CONFIGS = "configs"
        const val KEY_ACTIVE = "active"
        const val KEY_FAVORITES = "favorites"
        private const val KEY_MIGRATED = "legacy_migrated_v1"
        private val migrationLock = Any()
        const val DEFAULT_TAIL = "本消息为AI回复"
        const val DEFAULT_PROMPT = "你是一个自然、简洁、有帮助的微信聊天助手。"
        private fun targetKey(account: String) = "targets:$account"
        private fun providerForUrl(base: String, path: String): AiProvider {
            if (path.trim().trimEnd('/') !in setOf("/chat/completions", "chat/completions", "")) return AiProvider.CUSTOM
            return when (base.trim().trimEnd('/').lowercase()) {
            "https://api.deepseek.com", "https://api.deepseek.com/v1" -> AiProvider.DEEPSEEK
            "https://api.xiaomimimo.com/v1" -> AiProvider.MIMO
            else -> AiProvider.CUSTOM
            }
        }
        private fun encodeConfigs(values: List<AiChatConfig>): String = JSONArray().apply {
            values.forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("provider", c.provider.name); put("key", c.apiKey)
                put("base", c.baseUrl); put("path", c.chatPath); put("model", c.model)
                put("image", c.image); put("audio", c.audio); put("video", c.video)
                put("models", JSONArray().apply { c.models.forEach { m -> put(JSONObject().apply {
                    put("id", m.id); put("modalities", JSONArray(m.modalities.toList())); put("context", m.contextWindow)
                    put("output", m.maxOutputTokens)
                }) } })
            }) }
        }.toString()
        private fun decodeConfigs(raw: String): List<AiChatConfig> {
            val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
            return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o ->
                val models = o.optJSONArray("models") ?: JSONArray()
                AiChatConfig(id = o.optString("id"), name = o.optString("name"),
                    provider = runCatching { AiProvider.valueOf(o.optString("provider")) }.getOrDefault(AiProvider.CUSTOM),
                    apiKey = o.optString("key"), baseUrl = o.optString("base"), chatPath = o.optString("path", "/chat/completions"),
                    model = o.optString("model"), image = o.optBoolean("image"), audio = o.optBoolean("audio"), video = o.optBoolean("video"),
                    models = (0 until models.length()).mapNotNull { n -> models.optJSONObject(n)?.let { m ->
                        val modalities = m.optJSONArray("modalities") ?: JSONArray()
                        AiModel(m.optString("id"), (0 until modalities.length()).map { modalities.optString(it) }.toSet(),
                            m.optInt("context"), m.optInt("output"))
                    } })
            } }.filter { it.id.isNotBlank() }
        }
    }
}
