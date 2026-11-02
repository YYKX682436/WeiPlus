package h.Hchat.hooks.items.automessageforward

object PendingForwardRuleGateRegression {
    private data class Rule(
        val id: String,
        val name: String,
        val enabled: Boolean,
        val targets: Set<String>
    )

    fun run() {
        val target = "room-a"
        val capturedDueAt = mapOf("rule-a" to 5_000L)
        val initial = Rule("rule-a", "Original template", enabled = true, targets = setOf(target))
        fun active(masterEnabled: Boolean, rules: List<Rule>) = PendingForwardRuleGate.activeRules(
            masterEnabled = masterEnabled,
            capturedRuleIds = capturedDueAt.keys,
            target = target,
            currentRules = rules,
            id = Rule::id,
            targets = Rule::targets,
            enabled = Rule::enabled,
            matches = { true }
        )

        val withUnrelatedEdit = listOf(
            initial.copy(name = "Renamed template"),
            Rule("rule-b", "New unrelated rule", enabled = true, targets = setOf("room-b"))
        )
        check(active(masterEnabled = true, rules = withUnrelatedEdit).single().id == "rule-a")
        check(capturedDueAt["rule-a"] == 5_000L) { "Unrelated edits must keep the accepted delay deadline" }

        check(active(masterEnabled = true, rules = listOf(initial.copy(enabled = false))).isEmpty()) {
            "A disabled target rule must fail the send-time check"
        }
        check(active(masterEnabled = true, rules = listOf(initial)).single().id == "rule-a") {
            "Re-enabling the same still-pending rule restores its send-time eligibility"
        }
        check(active(masterEnabled = false, rules = listOf(initial)).isEmpty()) {
            "The master switch must still stop pending work"
        }
        println("PASS: delayed forwarding survives unrelated edits, rejects disabled rules, and revalidates after re-enable")
    }
}
