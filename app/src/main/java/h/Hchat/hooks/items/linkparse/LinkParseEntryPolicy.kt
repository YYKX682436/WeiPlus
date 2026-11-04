package h.Hchat.hooks.items.linkparse

/** Outgoing text belongs exclusively to the send-button entry. */
internal object LinkParseEntryPolicy {
    fun acceptDatabase(outgoing: Boolean): Boolean = !outgoing
}
