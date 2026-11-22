package h.Hchat.hooks.items.linkparse

import android.content.Context
import androidx.core.content.edit
import h.Hchat.hooks.items.talkerlist.TalkerListMode
import h.Hchat.hooks.items.talkerlist.TalkerListPolicy
import h.Hchat.preferences.HchatStorage
import org.json.JSONArray

internal class LinkParseSettings(context: Context) {
    private val prefs = HchatStorage.preferences(context, PREFS_NAME)
    private val lock = prefs

    init {
        listMode()
    }

    fun enabled() = boolean(KEY_ENABLE)

    fun enabled(platform: WorkLinkMatcher.Platform) = boolean(
        when (platform) {
            WorkLinkMatcher.Platform.DOUYIN -> KEY_DOUYIN
            WorkLinkMatcher.Platform.XIAOHONGSHU -> KEY_XHS
        }
    )

    private fun boolean(key: String): Boolean = runCatching { prefs.all[key] as? Boolean ?: false }.getOrDefault(false)

    fun listMode(): TalkerListMode = synchronized(lock) { readListModeLocked() }

    fun saveListMode(mode: TalkerListMode) {
        synchronized(lock) {
            prefs.edit(commit = true) { putString(KEY_LIST_MODE, mode.preferenceValue) }
        }
    }

    fun talkersFor(mode: TalkerListMode): Set<String> = synchronized(lock) {
        readTalkersLocked(mode).orEmpty()
    }

    fun isTalkerListValid(mode: TalkerListMode): Boolean = synchronized(lock) {
        readTalkersLocked(mode) != null
    }

    fun saveTalkers(mode: TalkerListMode, values: Set<String>) {
        val array = JSONArray()
        values.map(String::trim).filter(TalkerListPolicy::isValidChatId).distinct().forEach { array.put(it) }
        synchronized(lock) {
            prefs.edit(commit = true) { putString(keyFor(mode), array.toString()) }
        }
    }

    fun allows(platform: WorkLinkMatcher.Platform, talker: String, outgoing: Boolean): Boolean {
        if (!enabled() || !enabled(platform) || !TalkerListPolicy.isValidChatId(talker)) return false
        if (outgoing) return true
        return synchronized(lock) {
            val mode = readListModeLocked()
            val talkers = readTalkersLocked(mode) ?: return@synchronized false
            TalkerListPolicy.allows(mode, talker, outgoing = false, listedTalkers = talkers)
        }
    }

    fun allowedTalkers(): Set<String> = talkersFor(TalkerListMode.WHITELIST)

    fun saveAllowedTalkers(values: Set<String>) {
        saveTalkers(TalkerListMode.WHITELIST, values)
    }

    private fun readListModeLocked(): TalkerListMode {
        val stored = try {
            prefs.getString(KEY_LIST_MODE, null)
        } catch (_: Throwable) {
            return TalkerListMode.WHITELIST
        }
        if (stored != null) {
            return TalkerListMode.fromPreferenceValue(stored) ?: TalkerListMode.WHITELIST
        }
        val inferred = if (hasLegacyConfigurationLocked()) TalkerListMode.WHITELIST else TalkerListMode.BLACKLIST
        runCatching { prefs.edit(commit = true) { putString(KEY_LIST_MODE, inferred.preferenceValue) } }
        return inferred
    }

    private fun hasLegacyConfigurationLocked(): Boolean = runCatching {
        prefs.all.keys.any { it in LEGACY_CONFIGURATION_KEYS }
    }.getOrElse { true }

    private fun readTalkersLocked(mode: TalkerListMode): Set<String>? {
        val key = keyFor(mode)
        val raw = try {
            val storedValue = prefs.all[key]
            if (prefs.contains(key)) {
                storedValue as? String ?: return null
            } else {
                "[]"
            }
        } catch (_: Throwable) {
            return null
        }
        if (mode == TalkerListMode.WHITELIST && raw.isEmpty()) return emptySet()
        if (raw.isBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    val talker = array.get(index) as? String ?: error("名单项必须是聊天 ID 字符串")
                    check(TalkerListPolicy.isValidChatId(talker)) { "名单项不是有效聊天 ID" }
                    add(talker)
                }
            }
        }.getOrNull()
    }

    private fun keyFor(mode: TalkerListMode): String = when (mode) {
        TalkerListMode.BLACKLIST -> KEY_BLOCKED_TALKERS
        TalkerListMode.WHITELIST -> KEY_ALLOWED_TALKERS
    }

    companion object {
        const val PREFS_NAME = "Hchat_link_parse_config"
        const val KEY_ENABLE = "enable"
        const val KEY_DOUYIN = "douyin"
        const val KEY_XHS = "xiaohongshu"
        // Legacy whitelist storage; keep reading and writing it as the whitelist source.
        const val KEY_ALLOWED_TALKERS = "allowed_talkers"
        const val KEY_BLOCKED_TALKERS = "blocked_talkers"
        const val KEY_LIST_MODE = "list_mode"

        private val LEGACY_CONFIGURATION_KEYS = setOf(KEY_ENABLE, KEY_DOUYIN, KEY_XHS, KEY_ALLOWED_TALKERS)
    }
}
