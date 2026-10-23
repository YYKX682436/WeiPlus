package h.Hchat.hooks.items.antirecall

internal object RecallCachePolicy {
    fun needed(antiRecall: Boolean, forwarding: Boolean, followRecallRule: Boolean): Boolean =
        antiRecall || (forwarding && followRecallRule)

    fun needsDatabase(needed: Boolean, pbOperational: Boolean, direction: Int?, update: Boolean, delete: Boolean): Boolean =
        needed && !delete && (direction == null || direction != 0 || update || !pbOperational)
}
