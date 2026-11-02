package h.Hchat.hooks.items.automessageforward

/** Revalidates a delayed target against only the rules that originally selected it. */
internal object PendingForwardRuleGate {
    fun <T> activeRules(
        masterEnabled: Boolean,
        capturedRuleIds: Set<String>,
        target: String,
        currentRules: Iterable<T>,
        id: (T) -> String,
        targets: (T) -> Set<String>,
        enabled: (T) -> Boolean,
        matches: (T) -> Boolean
    ): List<T> {
        if (!masterEnabled || capturedRuleIds.isEmpty()) return emptyList()
        return currentRules.filter { rule ->
            id(rule) in capturedRuleIds &&
                target in targets(rule) &&
                enabled(rule) &&
                matches(rule)
        }
    }
}
