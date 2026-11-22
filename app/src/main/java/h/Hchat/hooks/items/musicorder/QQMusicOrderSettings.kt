package h.Hchat.hooks.items.musicorder

import android.content.Context
import h.Hchat.preferences.HchatStorage
import h.Hchat.hooks.items.talkerlist.TalkerListMode
import h.Hchat.hooks.items.talkerlist.TalkerListPolicy
import org.json.JSONArray

class QQMusicOrderSettings(context: Context?) {
    private val prefs = context?.let { HchatStorage.preferences(it, PREFS_NAME) }
    private val lock = prefs ?: Any()

    init {
        listMode()
    }

    fun isEnabled(): Boolean = boolean(KEY_ENABLE, DEFAULT_ENABLE)

    fun interceptOwnCommand(): Boolean = boolean(KEY_INTERCEPT_OWN_COMMAND, DEFAULT_INTERCEPT_OWN_COMMAND)

    fun sendAsCard(): Boolean = boolean(KEY_SEND_AS_CARD, DEFAULT_SEND_AS_CARD)

    fun sendAsVoice(): Boolean = boolean(KEY_SEND_AS_VOICE, DEFAULT_SEND_AS_VOICE)

    fun customSingerEnabled(): Boolean = boolean(KEY_CUSTOM_SINGER, DEFAULT_CUSTOM_SINGER)

    fun replaceSingerWithNickname(): Boolean = boolean(KEY_REPLACE_SINGER_WITH_NICKNAME, DEFAULT_REPLACE_SINGER_WITH_NICKNAME)

    fun replaceCoverWithAvatar(): Boolean = boolean(KEY_REPLACE_COVER_WITH_AVATAR, DEFAULT_REPLACE_COVER_WITH_AVATAR)

    fun defaultSinger(): String = string(KEY_DEFAULT_SINGER, DEFAULT_SINGER).trim()

    fun appId(): String = string(KEY_APP_ID, DEFAULT_APP_ID).trim().ifBlank { DEFAULT_APP_ID }

    fun triggers(): List<String> {
        val value = string(KEY_TRIGGERS, DEFAULT_TRIGGERS)
        return value.split(',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf(DEFAULT_TRIGGERS) }
    }

    internal fun listMode(): TalkerListMode = synchronized(lock) { readListModeLocked() }

    internal fun saveListMode(mode: TalkerListMode) {
        synchronized(lock) {
            prefs?.edit()?.putString(KEY_LIST_MODE, mode.preferenceValue)?.commit()
        }
    }

    internal fun talkersFor(mode: TalkerListMode): Set<String> = synchronized(lock) {
        readTalkersLocked(mode).orEmpty()
    }

    internal fun isTalkerListValid(mode: TalkerListMode): Boolean = synchronized(lock) {
        readTalkersLocked(mode) != null
    }

    internal fun saveTalkers(mode: TalkerListMode, values: Set<String>) {
        val key = keyFor(mode)
        synchronized(lock) {
            prefs?.edit()?.putString(key, encodeStringSet(values))?.commit()
        }
    }

    internal fun setTalkerAllowed(talker: String, allowed: Boolean): Boolean = synchronized(lock) {
        if (!TalkerListPolicy.isValidChatId(talker)) return@synchronized false
        val mode = readListModeLocked()
        val current = readTalkersLocked(mode) ?: return@synchronized false
        val next = TalkerListPolicy.update(mode, current, talker, allowed)
        prefs?.edit()?.putString(keyFor(mode), encodeStringSet(next))?.commit() ?: false
    }

    internal fun allows(talker: String, outgoing: Boolean): Boolean = synchronized(lock) {
        if (!isEnabled() || !TalkerListPolicy.isValidChatId(talker)) return@synchronized false
        val mode = readListModeLocked()
        if (outgoing) return@synchronized true
        val talkers = readTalkersLocked(mode) ?: return@synchronized false
        TalkerListPolicy.allows(mode, talker, outgoing = false, listedTalkers = talkers)
    }

    internal fun allowedTalkers(): Set<String> = talkersFor(TalkerListMode.WHITELIST)

    internal fun saveAllowedTalkers(values: Set<String>) {
        saveTalkers(TalkerListMode.WHITELIST, values)
    }

    private fun readListModeLocked(): TalkerListMode {
        val stored = try {
            prefs?.getString(KEY_LIST_MODE, null)
        } catch (_: Throwable) {
            return TalkerListMode.WHITELIST
        }
        if (stored != null) {
            return TalkerListMode.fromPreferenceValue(stored) ?: TalkerListMode.WHITELIST
        }
        val inferred = if (hasLegacyConfigurationLocked()) TalkerListMode.WHITELIST else TalkerListMode.BLACKLIST
        runCatching { prefs?.edit()?.putString(KEY_LIST_MODE, inferred.preferenceValue)?.commit() }
        return inferred
    }

    private fun hasLegacyConfigurationLocked(): Boolean = runCatching {
        prefs?.all?.keys?.any { it in LEGACY_CONFIGURATION_KEYS } == true
    }.getOrElse { true }

    private fun readTalkersLocked(mode: TalkerListMode): Set<String>? {
        val raw = try {
            prefs?.getString(keyFor(mode), if (mode == TalkerListMode.WHITELIST) "" else "[]").orEmpty()
        } catch (_: Throwable) {
            return null
        }
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf(TalkerListPolicy::isValidChatId)?.let(::add)
                }
            }
        }.getOrNull()
    }

    private fun keyFor(mode: TalkerListMode): String = when (mode) {
        TalkerListMode.BLACKLIST -> KEY_BLOCKED_TALKERS
        TalkerListMode.WHITELIST -> KEY_ALLOWED_TALKERS
    }

    private fun boolean(key: String, defaultValue: Boolean): Boolean = runCatching {
        prefs?.getBoolean(key, defaultValue) ?: defaultValue
    }.getOrDefault(defaultValue)

    private fun string(key: String, defaultValue: String): String = runCatching {
        prefs?.getString(key, defaultValue) ?: defaultValue
    }.getOrDefault(defaultValue)

    companion object {
        const val PREFS_NAME = "Hchat_qq_music_order_config"

        const val KEY_ENABLE = "qq_music_order_enable"
        const val KEY_INTERCEPT_OWN_COMMAND = "qq_music_order_intercept_own_command"
        const val KEY_SEND_AS_CARD = "qq_music_order_send_as_card"
        const val KEY_SEND_AS_VOICE = "qq_music_order_send_as_voice"
        const val KEY_CUSTOM_SINGER = "qq_music_order_custom_singer"
        const val KEY_DEFAULT_SINGER = "qq_music_order_default_singer"
        const val KEY_APP_ID = "qq_music_order_app_id"
        const val KEY_TRIGGERS = "qq_music_order_triggers"
        const val KEY_REPLACE_COVER_WITH_AVATAR = "qq_music_order_replace_cover_with_avatar"
        const val KEY_REPLACE_SINGER_WITH_NICKNAME = "qq_music_order_replace_singer_with_nickname"
        // Legacy whitelist storage; keep reading and writing it as the whitelist source.
        const val KEY_ALLOWED_TALKERS = "qq_music_order_allowed_talkers"
        const val KEY_BLOCKED_TALKERS = "qq_music_order_blocked_talkers"
        const val KEY_LIST_MODE = "qq_music_order_list_mode"

        const val DEFAULT_ENABLE = false
        const val DEFAULT_INTERCEPT_OWN_COMMAND = false
        const val DEFAULT_SEND_AS_CARD = true
        const val DEFAULT_SEND_AS_VOICE = false
        const val DEFAULT_CUSTOM_SINGER = false
        const val DEFAULT_SINGER = ""
        const val DEFAULT_APP_ID = "wx485a97c844086dc9"
        const val DEFAULT_TRIGGERS = "点歌"
        const val DEFAULT_REPLACE_COVER_WITH_AVATAR = false
        const val DEFAULT_REPLACE_SINGER_WITH_NICKNAME = false

        private val LEGACY_CONFIGURATION_KEYS = setOf(
            KEY_ENABLE,
            KEY_INTERCEPT_OWN_COMMAND,
            KEY_SEND_AS_CARD,
            KEY_SEND_AS_VOICE,
            KEY_CUSTOM_SINGER,
            KEY_DEFAULT_SINGER,
            KEY_APP_ID,
            KEY_TRIGGERS,
            KEY_REPLACE_COVER_WITH_AVATAR,
            KEY_REPLACE_SINGER_WITH_NICKNAME,
            KEY_ALLOWED_TALKERS
        )

        @JvmStatic
        fun parseStringSet(value: String?): Set<String> {
            if (value.isNullOrBlank()) return emptySet()
            return runCatching {
                val array = JSONArray(value)
                buildSet {
                    for (index in 0 until array.length()) {
                        array.optString(index).trim().takeIf(TalkerListPolicy::isValidChatId)?.let(::add)
                    }
                }
            }.getOrDefault(emptySet())
        }

        @JvmStatic
        fun encodeStringSet(values: Set<String>): String {
            return JSONArray().apply {
                values.map { it.trim() }
                    .filter(TalkerListPolicy::isValidChatId)
                    .distinct()
                    .forEach(::put)
            }.toString()
        }
    }
}
