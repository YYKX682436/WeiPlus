package h.Hchat.hooks.items.autoreply

import android.content.Context
import android.content.SharedPreferences
import h.Hchat.preferences.HchatStorage
import org.json.JSONArray
import org.json.JSONObject

class AutoReplySettings(context: Context?) {
    private val prefs = context?.let { HchatStorage.preferences(it, PREFS_NAME) }

    fun getHostPreferences(): SharedPreferences? = prefs

    fun isEnabled(): Boolean = getBoolean(KEY_ENABLE, DEFAULT_ENABLE)

    fun getBoolean(key: String, def: Boolean): Boolean = runCatching {
        prefs?.getBoolean(key, def) ?: def
    }.getOrDefault(def)

    fun getInt(key: String, def: Int): Int = runCatching {
        prefs?.getInt(key, def) ?: def
    }.getOrDefault(def)

    fun getLong(key: String, def: Long): Long = runCatching {
        prefs?.getLong(key, def) ?: def
    }.getOrDefault(def)

    fun getString(key: String, def: String): String = runCatching {
        prefs?.getString(key, def) ?: def
    }.getOrDefault(def)

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun rules(): List<AutoReplyRule> = parseRules(getString(KEY_RULES, ""))

    fun saveRules(values: List<AutoReplyRule>, explicitlyEmptyRuleId: String? = null) {
        val scopes = explicitEmptyScopes().toMutableSet()
        val emptyRuleScopes = values.filter { it.steps.isEmpty() }.mapTo(HashSet()) { RULE_EMPTY_PREFIX + it.id }
        scopes.removeAll { it.startsWith(RULE_EMPTY_PREFIX) && it !in emptyRuleScopes }
        if (explicitlyEmptyRuleId != null && RULE_EMPTY_PREFIX + explicitlyEmptyRuleId in emptyRuleScopes) {
            scopes.add(RULE_EMPTY_PREFIX + explicitlyEmptyRuleId)
        }
        prefs?.edit()?.putString(KEY_RULES, encodeRules(values))?.also { writeEmptyScopes(it, scopes) }?.apply()
    }

    fun isRuleExplicitlyEmpty(id: String): Boolean = RULE_EMPTY_PREFIX + id in explicitEmptyScopes()

    fun isAutoAcceptExplicitlyEmpty(): Boolean = ACCEPT_EMPTY_SCOPE in explicitEmptyScopes()

    fun isGreetAcceptedExplicitlyEmpty(): Boolean = GREET_EMPTY_SCOPE in explicitEmptyScopes()

    private fun explicitEmptyScopes(): Set<String> = runCatching {
        prefs?.getStringSet(KEY_REMOVED_REPLY_EMPTY_SCOPES, emptySet())?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    private fun writeEmptyScopes(editor: SharedPreferences.Editor, scopes: Set<String>) {
        if (scopes.isEmpty()) editor.remove(KEY_REMOVED_REPLY_EMPTY_SCOPES)
        else editor.putStringSet(KEY_REMOVED_REPLY_EMPTY_SCOPES, scopes)
    }

    private fun saveFriendSteps(key: String, scope: String, values: List<AutoReplyStep>, explicitEmpty: Boolean) {
        val scopes = explicitEmptyScopes().toMutableSet()
        if (explicitEmpty && values.isEmpty()) scopes.add(scope) else scopes.remove(scope)
        prefs?.edit()?.putString(key, encodeSteps(values))?.also { writeEmptyScopes(it, scopes) }?.apply()
    }

    fun shouldExcludeTalker(talker: String?): Boolean {
        if (!getBoolean(KEY_EXCLUDED_TALKERS_ENABLE, false)) return false
        val normalized = talker?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return excludedTalkerIds().any { it.equals(normalized, ignoreCase = true) }
    }

    fun excludedTalkerIds(): Set<String> =
        getString(KEY_EXCLUDED_TALKERS, "")
            .split(',', '，', ';', '；', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun autoAcceptSteps(): List<AutoReplyStep> {
        val steps = parseSteps(getString(KEY_AUTO_ACCEPT_STEPS, ""))
        return if (steps.isNotEmpty() || isAutoAcceptExplicitlyEmpty()) steps
        else listOf(AutoReplyStep(mode = REPLY_TEXT, content = "你好，%friendName%"))
    }

    fun saveAutoAcceptSteps(values: List<AutoReplyStep>, explicitEmpty: Boolean = false) {
        saveFriendSteps(KEY_AUTO_ACCEPT_STEPS, ACCEPT_EMPTY_SCOPE, values, explicitEmpty)
    }

    fun greetAcceptedSteps(): List<AutoReplyStep> {
        val steps = parseSteps(getString(KEY_GREET_ACCEPTED_STEPS, ""))
        return if (steps.isNotEmpty() || isGreetAcceptedExplicitlyEmpty()) steps
        else listOf(AutoReplyStep(mode = REPLY_TEXT, content = "哈喽，%friendName%！感谢通过好友请求，以后请多指教啦！"))
    }

    fun saveGreetAcceptedSteps(values: List<AutoReplyStep>, explicitEmpty: Boolean = false) {
        saveFriendSteps(KEY_GREET_ACCEPTED_STEPS, GREET_EMPTY_SCOPE, values, explicitEmpty)
    }

    fun aiConfig(): AutoReplyAiConfig {
        return activeZhiliaConfig().toAiConfig(
            stream = getBoolean(KEY_AI_STREAM, DEFAULT_AI_STREAM)
        )
    }

    fun activeZhiliaName(): String {
        ensureZhiliaConfigMigrated()
        return getString(KEY_ZHILIA_ACTIVE_CONFIG, DEFAULT_ZHILIA_CONFIG_NAME).ifBlank { DEFAULT_ZHILIA_CONFIG_NAME }
    }

    fun activeZhiliaConfig(): AutoReplyZhiliaConfig {
        ensureZhiliaConfigMigrated()
        val configs = zhiliaConfigs()
        val activeName = activeZhiliaName()
        return configs.firstOrNull { it.name == activeName }
            ?: configs.firstOrNull()
            ?: legacyZhiliaConfig(DEFAULT_ZHILIA_CONFIG_NAME)
    }

    fun zhiliaConfigs(): List<AutoReplyZhiliaConfig> {
        val raw = getString(KEY_ZHILIA_CONFIGS, "")
        val parsed = runCatching {
            if (raw.isBlank()) return@runCatching emptyList()
            val obj = JSONObject(raw)
            buildList {
                obj.keys().forEach { name ->
                    val item = obj.optJSONObject(name) ?: return@forEach
                    add(
                        AutoReplyZhiliaConfig(
                            name = name,
                            apiKey = item.optString("apiKey", ""),
                            apiBaseUrl = item.optString("apiUrl", DEFAULT_AI_API_BASE).ifBlank { DEFAULT_AI_API_BASE },
                            apiPath = item.optString("apiPath", DEFAULT_AI_API_PATH).ifBlank { DEFAULT_AI_API_PATH },
                            model = item.optString("modelName", DEFAULT_AI_MODEL).ifBlank { DEFAULT_AI_MODEL },
                            systemPrompt = item.optString("systemPrompt", DEFAULT_AI_SYSTEM_PROMPT),
                            contextLimit = item.optInt("contextLimit", DEFAULT_AI_CONTEXT_LIMIT).coerceIn(0, 50)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { listOf(legacyZhiliaConfig(DEFAULT_ZHILIA_CONFIG_NAME)) }
    }

    fun saveZhiliaConfigs(values: List<AutoReplyZhiliaConfig>, activeName: String) {
        val clean = values
            .map { it.normalized() }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name }
            .ifEmpty { listOf(legacyZhiliaConfig(DEFAULT_ZHILIA_CONFIG_NAME)) }
        val json = JSONObject()
        clean.forEach { config ->
            json.put(config.name, JSONObject().apply {
                put("apiKey", config.apiKey)
                put("apiUrl", config.apiBaseUrl)
                put("apiPath", config.apiPath)
                put("modelName", config.model)
                put("systemPrompt", config.systemPrompt)
                put("contextLimit", config.contextLimit.coerceIn(0, 50))
            })
        }
        val active = clean.firstOrNull { it.name == activeName }?.name ?: clean.first().name
        prefs?.edit()
            ?.putString(KEY_ZHILIA_CONFIGS, json.toString())
            ?.putString(KEY_ZHILIA_ACTIVE_CONFIG, active)
            ?.apply()
        clean.firstOrNull { it.name == active }?.let { syncLegacyZhiliaConfig(it) }
    }

    fun saveActiveZhiliaConfig(value: AutoReplyZhiliaConfig) {
        val next = zhiliaConfigs().toMutableList()
        val clean = value.normalized()
        val index = next.indexOfFirst { it.name == clean.name }
        if (index >= 0) next[index] = clean else next += clean
        saveZhiliaConfigs(next, clean.name)
    }

    fun favoriteModels(apiBaseUrl: String): Set<String> {
        val key = favoriteModelKey(apiBaseUrl)
        val obj = runCatching { JSONObject(getString(KEY_ZHILIA_MODEL_FAVORITES, "")) }.getOrNull()
            ?: return emptySet()
        val raw = obj.optJSONArray(key)
        if (raw == null) {
            return obj.optString(key, "")
                .split(";;;")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        return buildSet {
            for (i in 0 until raw.length()) {
                raw.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
    }

    fun saveFavoriteModels(apiBaseUrl: String, values: Set<String>) {
        val key = favoriteModelKey(apiBaseUrl)
        val json = runCatching { JSONObject(getString(KEY_ZHILIA_MODEL_FAVORITES, "")) }.getOrDefault(JSONObject())
        json.put(key, JSONArray().apply {
            values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { put(it) }
        })
        putString(KEY_ZHILIA_MODEL_FAVORITES, json.toString())
    }

    private fun ensureZhiliaConfigMigrated() {
        if (getString(KEY_ZHILIA_CONFIGS, "").isNotBlank()) return
        val legacy = legacyZhiliaConfig(DEFAULT_ZHILIA_CONFIG_NAME)
        saveZhiliaConfigs(listOf(legacy), getString(KEY_ZHILIA_ACTIVE_CONFIG, DEFAULT_ZHILIA_CONFIG_NAME))
    }

    private fun legacyZhiliaConfig(name: String): AutoReplyZhiliaConfig {
        return AutoReplyZhiliaConfig(
            name = name.ifBlank { DEFAULT_ZHILIA_CONFIG_NAME },
            apiKey = getString(KEY_AI_API_KEY, ""),
            apiBaseUrl = getString(KEY_AI_API_BASE, DEFAULT_AI_API_BASE),
            apiPath = getString(KEY_AI_API_PATH, DEFAULT_AI_API_PATH),
            model = getString(KEY_AI_MODEL, DEFAULT_AI_MODEL),
            systemPrompt = getString(KEY_AI_SYSTEM_PROMPT, DEFAULT_AI_SYSTEM_PROMPT),
            contextLimit = getInt(KEY_AI_CONTEXT_LIMIT, DEFAULT_AI_CONTEXT_LIMIT).coerceIn(0, 50)
        )
    }

    private fun syncLegacyZhiliaConfig(value: AutoReplyZhiliaConfig) {
        prefs?.edit()
            ?.putString(KEY_AI_API_KEY, value.apiKey)
            ?.putString(KEY_AI_API_BASE, value.apiBaseUrl)
            ?.putString(KEY_AI_API_PATH, value.apiPath)
            ?.putString(KEY_AI_MODEL, value.model)
            ?.putString(KEY_AI_SYSTEM_PROMPT, value.systemPrompt)
            ?.putInt(KEY_AI_CONTEXT_LIMIT, value.contextLimit.coerceIn(0, 50))
            ?.apply()
    }

    private fun favoriteModelKey(apiBaseUrl: String): String =
        apiBaseUrl.trim().lowercase()

    companion object {
        const val PREFS_NAME = "Hchat_auto_reply_config"

        const val KEY_ENABLE = "auto_reply_enable"
        const val KEY_RULES = "auto_reply_rules_v1"
        // Records an intentional empty reply after a removed legacy step was deleted.
        const val KEY_REMOVED_REPLY_EMPTY_SCOPES = "removed_reply_empty_scopes_v1"
        private const val RULE_EMPTY_PREFIX = "rule:"
        private const val ACCEPT_EMPTY_SCOPE = "friend:accept"
        private const val GREET_EMPTY_SCOPE = "friend:greet"
        const val KEY_EXCLUDED_TALKERS_ENABLE = "excluded_talkers_enable"
        const val KEY_EXCLUDED_TALKERS = "excluded_talkers"
        const val KEY_AUTO_ACCEPT_ENABLE = "auto_accept_enable"
        const val KEY_AUTO_ACCEPT_DELAY_MS = "auto_accept_delay_ms"
        const val KEY_AUTO_ACCEPT_STEPS = "auto_accept_steps_v1"
        const val KEY_AUTO_ACCEPT_TAG_ENABLE = "auto_accept_tag_enable"
        const val KEY_AUTO_ACCEPT_TAG_NAME = "auto_accept_tag_name"
        const val KEY_AUTO_ACCEPT_LABEL_NEW_FRIEND_ENABLE = "auto_accept_label_new_friend_enable"
        const val KEY_AUTO_ACCEPT_LABEL_DATE_ENABLE = "auto_accept_label_date_enable"
        const val KEY_AUTO_ACCEPT_LABEL_DATE_FORMAT = "auto_accept_label_date_format"
        const val KEY_AUTO_ACCEPT_LABEL_EXISTING_ENABLE = "auto_accept_label_existing_enable"
        const val KEY_AUTO_ACCEPT_LABEL_SELECTED_NAMES = "auto_accept_label_selected_names"
        const val KEY_AUTO_ACCEPT_REMARK_NEW_FRIEND_ENABLE = "auto_accept_remark_new_friend_enable"
        const val KEY_AUTO_ACCEPT_REMARK_NICKNAME_SUFFIX_ENABLE = "auto_accept_remark_nickname_suffix_enable"
        const val KEY_AUTO_ACCEPT_REMARK_DATE_ENABLE = "auto_accept_remark_date_enable"
        const val KEY_AUTO_ACCEPT_REMARK_DATE_FORMAT = "auto_accept_remark_date_format"
        const val KEY_AUTO_ACCEPT_REMARK_CUSTOM_ENABLE = "auto_accept_remark_custom_enable"
        const val KEY_AUTO_ACCEPT_REMARK_CUSTOM_TEXT = "auto_accept_remark_custom_text"
        const val KEY_GREET_ACCEPTED_ENABLE = "greet_accepted_enable"
        const val KEY_GREET_ACCEPTED_DELAY_MS = "greet_accepted_delay_ms"
        const val KEY_GREET_ACCEPTED_STEPS = "greet_accepted_steps_v1"
        const val KEY_GREET_ACCEPTED_TAG_ENABLE = "greet_accepted_tag_enable"
        const val KEY_GREET_ACCEPTED_TAG_NAME = "greet_accepted_tag_name"
        const val KEY_GREET_ACCEPTED_LABEL_NEW_FRIEND_ENABLE = "greet_accepted_label_new_friend_enable"
        const val KEY_GREET_ACCEPTED_LABEL_DATE_ENABLE = "greet_accepted_label_date_enable"
        const val KEY_GREET_ACCEPTED_LABEL_DATE_FORMAT = "greet_accepted_label_date_format"
        const val KEY_GREET_ACCEPTED_LABEL_EXISTING_ENABLE = "greet_accepted_label_existing_enable"
        const val KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES = "greet_accepted_label_selected_names"
        const val KEY_GREET_ACCEPTED_REMARK_NEW_FRIEND_ENABLE = "greet_accepted_remark_new_friend_enable"
        const val KEY_GREET_ACCEPTED_REMARK_NICKNAME_SUFFIX_ENABLE = "greet_accepted_remark_nickname_suffix_enable"
        const val KEY_GREET_ACCEPTED_REMARK_DATE_ENABLE = "greet_accepted_remark_date_enable"
        const val KEY_GREET_ACCEPTED_REMARK_DATE_FORMAT = "greet_accepted_remark_date_format"
        const val KEY_GREET_ACCEPTED_REMARK_CUSTOM_ENABLE = "greet_accepted_remark_custom_enable"
        const val KEY_GREET_ACCEPTED_REMARK_CUSTOM_TEXT = "greet_accepted_remark_custom_text"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_API_BASE = "ai_api_base"
        const val KEY_AI_API_PATH = "ai_api_path"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_SYSTEM_PROMPT = "ai_system_prompt"
        const val KEY_AI_CONTEXT_LIMIT = "ai_context_limit"
        const val KEY_AI_STREAM = "ai_stream"
        const val KEY_AI_CLEAR_CONTEXT_ON_SAVE = "ai_clear_context_on_save"
        const val KEY_ZHILIA_CONFIGS = "zhilia_multi_configs_v1"
        const val KEY_ZHILIA_ACTIVE_CONFIG = "zhilia_active_config_name_v1"
        const val KEY_ZHILIA_MODEL_FAVORITES = "zhilia_model_favorites_v1"

        const val DEFAULT_ENABLE = false
        const val DEFAULT_AI_API_BASE = "https://api.siliconflow.cn/v1"
        const val DEFAULT_AI_API_PATH = "/chat/completions"
        const val DEFAULT_AI_MODEL = "deepseek-ai/DeepSeek-V3"
        const val DEFAULT_AI_SYSTEM_PROMPT = "你是一个简洁、有帮助的聊天助手"
        const val DEFAULT_AI_CONTEXT_LIMIT = 10
        const val DEFAULT_AI_STREAM = false
        const val DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE = true
        const val DEFAULT_ZHILIA_CONFIG_NAME = "默认配置"

        const val MATCH_FUZZY = 0
        const val MATCH_EXACT = 1
        const val MATCH_REGEX = 2
        const val MATCH_ANY = 3

        const val TARGET_ALL = 0
        const val TARGET_PRIVATE = 1
        const val TARGET_GROUP = 2
        const val TARGET_SPECIFIC = 3
        const val TARGET_OFFICIAL = 4

        const val AT_NONE = 0
        const val AT_ME = 1
        const val AT_ALL = 2

        const val PAT_NONE = 0
        const val PAT_ME = 1

        const val REPLY_TEXT = 0
        const val REPLY_IMAGE = 1
        const val REPLY_VOICE = 2
        const val REPLY_VOICE_RANDOM_FOLDER = 3
        const val REPLY_EMOJI = 4
        const val REPLY_VIDEO = 5
        const val REPLY_CARD = 6
        const val REPLY_FILE = 7
        const val REPLY_INVITE_GROUP = 8
        const val REPLY_XML = 9
        const val REPLY_AI = 10
        const val REPLY_ZHILIA_AI = REPLY_AI
        // Historical reply values remain reserved so old rules round-trip unchanged.
        const val REPLY_REMOVED_AI = 11
        const val REPLY_REMOVED_VOICE = 12
        const val REPLY_FAVORITE = 13

        const val FRIEND_ACCEPTED_KEYWORD = "我通过了你的朋友验证请求，现在我们可以开始聊天了"

        @JvmStatic
        fun isAiReplyMode(mode: Int): Boolean = mode == REPLY_ZHILIA_AI

        @JvmStatic
        fun isRemovedReplyMode(mode: Int): Boolean =
            mode == REPLY_REMOVED_AI || mode == REPLY_REMOVED_VOICE

        @JvmStatic
        fun parseRules(value: String?): List<AutoReplyRule> {
            if (value.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(value)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        add(
                            AutoReplyRule(
                                id = obj.optString("id").ifBlank { System.currentTimeMillis().toString() + "_$i" },
                                name = obj.optString("name", "规则 ${i + 1}"),
                                enabled = obj.optBoolean("enabled", true),
                                keyword = obj.optString("keyword", ""),
                                excludedKeywords = obj.optString("excludedKeywords", ""),
                                matchType = obj.optInt("matchType", MATCH_FUZZY),
                                targetMode = obj.optInt("targetMode", TARGET_ALL),
                                targetIds = parseStringSet(obj.optJSONArray("targetIds")),
                                excludedIds = parseStringSet(obj.optJSONArray("excludedIds")),
                                includedGroupMembers = parseStringSet(obj.optJSONArray("includedGroupMembers")),
                                excludedGroupMembers = parseStringSet(obj.optJSONArray("excludedGroupMembers")),
                                atTrigger = obj.optInt("atTrigger", AT_NONE),
                                patTrigger = obj.optInt("patTrigger", PAT_NONE),
                                startTime = obj.optString("startTime", ""),
                                endTime = obj.optString("endTime", ""),
                                maxReplyCount = obj.optInt("maxReplyCount", 0).coerceAtLeast(0),
                                cooldownSeconds = obj.optLong("cooldownSeconds", 0L).coerceAtLeast(0L),
                                replyAsQuote = obj.optBoolean("replyAsQuote", false),
                                steps = parseSteps(obj.optString("steps", ""))
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        @JvmStatic
        fun encodeRules(values: List<AutoReplyRule>): String {
            val array = JSONArray()
            values.forEach { rule ->
                array.put(JSONObject().apply {
                    put("id", rule.id)
                    put("name", rule.name)
                    put("enabled", rule.enabled)
                    put("keyword", rule.keyword)
                    put("excludedKeywords", rule.excludedKeywords)
                    put("matchType", rule.matchType)
                    put("targetMode", rule.targetMode)
                    put("targetIds", encodeStringSet(rule.targetIds))
                    put("excludedIds", encodeStringSet(rule.excludedIds))
                    put("includedGroupMembers", encodeStringSet(rule.includedGroupMembers))
                    put("excludedGroupMembers", encodeStringSet(rule.excludedGroupMembers))
                    put("atTrigger", rule.atTrigger)
                    put("patTrigger", rule.patTrigger)
                    put("startTime", rule.startTime)
                    put("endTime", rule.endTime)
                    put("maxReplyCount", rule.maxReplyCount)
                    put("cooldownSeconds", rule.cooldownSeconds.coerceAtLeast(0L))
                    put("replyAsQuote", rule.replyAsQuote)
                    put("steps", encodeSteps(rule.steps))
                })
            }
            return array.toString()
        }

        @JvmStatic
        fun parseSteps(value: String?): List<AutoReplyStep> {
            if (value.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(value)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val mode = obj.optInt("mode", REPLY_TEXT)
                        val content = obj.optString("content", "")
                        if (content.isBlank() && !isAiReplyMode(mode) && !isRemovedReplyMode(mode)) continue
                        add(
                            AutoReplyStep(
                                id = obj.optString("id").ifBlank { System.currentTimeMillis().toString() + "_$i" },
                                mode = mode,
                                content = content,
                                delayMs = obj.optLong("delayMs", 0L).coerceIn(0L, 600000L),
                                randomDelay = obj.optBoolean("randomDelay", false)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        @JvmStatic
        fun encodeSteps(values: List<AutoReplyStep>): String {
            val array = JSONArray()
            values.forEach { step ->
                array.put(JSONObject().apply {
                    put("id", step.id)
                    put("mode", step.mode)
                    put("content", step.content)
                    put("delayMs", step.delayMs.coerceIn(0L, 600000L))
                    put("randomDelay", step.randomDelay)
                })
            }
            return array.toString()
        }

        @JvmStatic
        fun parseStringSet(value: String?): Set<String> {
            if (value.isNullOrBlank()) return emptySet()
            return runCatching { parseStringSet(JSONArray(value)) }.getOrDefault(emptySet())
        }

        private fun parseStringSet(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()
            return buildSet {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }

        @JvmStatic
        fun encodeStringSet(values: Set<String>): JSONArray {
            val array = JSONArray()
            values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { array.put(it) }
            return array
        }
    }
}

data class AutoReplyRule(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "新规则",
    val enabled: Boolean = true,
    val keyword: String = "",
    val excludedKeywords: String = "",
    val matchType: Int = AutoReplySettings.MATCH_FUZZY,
    val targetMode: Int = AutoReplySettings.TARGET_ALL,
    val targetIds: Set<String> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
    val includedGroupMembers: Set<String> = emptySet(),
    val excludedGroupMembers: Set<String> = emptySet(),
    val atTrigger: Int = AutoReplySettings.AT_NONE,
    val patTrigger: Int = AutoReplySettings.PAT_NONE,
    val startTime: String = "",
    val endTime: String = "",
    val maxReplyCount: Int = 0,
    val cooldownSeconds: Long = 0L,
    val replyAsQuote: Boolean = false,
    val steps: List<AutoReplyStep> = listOf(AutoReplyStep())
)

data class AutoReplyStep(
    val id: String = System.currentTimeMillis().toString(),
    val mode: Int = AutoReplySettings.REPLY_TEXT,
    val content: String = "你好",
    val delayMs: Long = 0L,
    val randomDelay: Boolean = false
)

data class AutoReplyAiConfig(
    val apiKey: String,
    val apiBaseUrl: String,
    val apiPath: String,
    val model: String,
    val systemPrompt: String,
    val contextLimit: Int,
    val stream: Boolean
)

data class AutoReplyZhiliaConfig(
    val name: String,
    val apiKey: String,
    val apiBaseUrl: String,
    val apiPath: String,
    val model: String,
    val systemPrompt: String,
    val contextLimit: Int
) {
    fun normalized(): AutoReplyZhiliaConfig = copy(
        name = name.trim().ifBlank { AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME },
        apiBaseUrl = apiBaseUrl.trim().ifBlank { AutoReplySettings.DEFAULT_AI_API_BASE },
        apiPath = apiPath.trim().ifBlank { AutoReplySettings.DEFAULT_AI_API_PATH },
        model = model.trim().ifBlank { AutoReplySettings.DEFAULT_AI_MODEL },
        contextLimit = contextLimit.coerceIn(0, 50)
    )

    fun toAiConfig(stream: Boolean): AutoReplyAiConfig = normalized().let {
        AutoReplyAiConfig(
            apiKey = it.apiKey,
            apiBaseUrl = it.apiBaseUrl,
            apiPath = it.apiPath,
            model = it.model,
            systemPrompt = it.systemPrompt,
            contextLimit = it.contextLimit,
            stream = stream
        )
    }
}
