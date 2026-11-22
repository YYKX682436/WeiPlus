package h.Hchat.hooks.items.talkerlist

internal enum class TalkerListMode(val preferenceValue: String, val title: String) {
    BLACKLIST("blacklist", "黑名单"),
    WHITELIST("whitelist", "白名单");

    companion object {
        fun fromPreferenceValue(value: String?): TalkerListMode? =
            entries.firstOrNull { it.preferenceValue == value }
    }
}

internal object TalkerListPolicy {
    fun isValidChatId(talker: String): Boolean =
        talker.isNotEmpty() && talker == talker.trim() &&
            talker.none { it.isWhitespace() || Character.isISOControl(it) }

    fun allows(
        mode: TalkerListMode,
        talker: String,
        outgoing: Boolean,
        listedTalkers: Set<String>,
        listIsValid: Boolean = true
    ): Boolean {
        if (!isValidChatId(talker)) return false
        if (outgoing) return true
        if (!listIsValid) return false
        return when (mode) {
            TalkerListMode.BLACKLIST -> talker !in listedTalkers
            TalkerListMode.WHITELIST -> talker in listedTalkers
        }
    }

    fun update(
        mode: TalkerListMode,
        current: Set<String>,
        talker: String,
        allowed: Boolean
    ): Set<String> {
        if (!isValidChatId(talker)) return current
        val shouldBeListed = if (mode == TalkerListMode.WHITELIST) allowed else !allowed
        return current.toMutableSet().apply {
            if (shouldBeListed) add(talker) else remove(talker)
        }
    }
}
