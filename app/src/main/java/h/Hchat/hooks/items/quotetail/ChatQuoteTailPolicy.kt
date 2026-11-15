package h.Hchat.hooks.items.quotetail

internal object ChatQuoteTailPolicy {
    fun resolveNickname(
        accountName: String?,
        contactName: String?,
        selfWxId: String
    ): String {
        val accountNickname = accountName.orEmpty()
        if (accountNickname.isNotBlank() && accountNickname != selfWxId) return accountNickname
        val contactNickname = contactName.orEmpty()
        return contactNickname.takeIf { it.isNotBlank() && it != selfWxId }.orEmpty()
    }

    fun shouldIntercept(
        enabled: Boolean,
        text: String,
        talker: String,
        nickname: String,
        quoteContent: String,
        lastQuoteMessageId: Long?
    ): Boolean {
        if (!enabled || text.isBlank() || talker.isBlank()) return false
        if (nickname.isBlank() || quoteContent.isBlank()) return false
        if (lastQuoteMessageId != 0L) return false
        return !(isGroup(talker) && '@' in text)
    }

    private fun isGroup(talker: String): Boolean =
        talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom")
}
