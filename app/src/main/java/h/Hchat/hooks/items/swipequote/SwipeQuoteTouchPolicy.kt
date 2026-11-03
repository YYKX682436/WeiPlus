package h.Hchat.hooks.items.swipequote

/** Defers terminal gesture handling to RecyclerView's touch callback. */
internal object SwipeQuoteTouchPolicy {
    enum class CallbackPhase {
        INTERCEPT,
        TOUCH
    }

    enum class TerminalAction {
        UP,
        CANCEL
    }

    enum class TerminalDecision {
        DEFER,
        TRIGGER,
        RESET
    }

    fun terminalDecision(
        action: TerminalAction,
        phase: CallbackPhase,
        armed: Boolean,
        hasTarget: Boolean
    ): TerminalDecision {
        if (phase == CallbackPhase.INTERCEPT) return TerminalDecision.DEFER
        if (action == TerminalAction.CANCEL) return TerminalDecision.RESET
        return if (armed && hasTarget) TerminalDecision.TRIGGER else TerminalDecision.RESET
    }
}
