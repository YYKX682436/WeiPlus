package h.Hchat.hooks.items.swipequote

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeQuoteTouchPolicyTest {
    @Test
    fun interceptCallbackDefersUpAndCancelToTouchCallback() {
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.DEFER,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.UP,
                SwipeQuoteTouchPolicy.CallbackPhase.INTERCEPT,
                armed = true,
                hasTarget = true
            )
        )
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.DEFER,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.CANCEL,
                SwipeQuoteTouchPolicy.CallbackPhase.INTERCEPT,
                armed = true,
                hasTarget = true
            )
        )
    }

    @Test
    fun armedTouchUpTriggersWithoutAnInterceptionFlag() {
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.TRIGGER,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.UP,
                SwipeQuoteTouchPolicy.CallbackPhase.TOUCH,
                armed = true,
                hasTarget = true
            )
        )
    }

    @Test
    fun upAfterStateResetCannotTriggerTheSameGestureAgain() {
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.TRIGGER,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.UP,
                SwipeQuoteTouchPolicy.CallbackPhase.TOUCH,
                armed = true,
                hasTarget = true
            )
        )
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.RESET,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.UP,
                SwipeQuoteTouchPolicy.CallbackPhase.TOUCH,
                armed = false,
                hasTarget = false
            )
        )
    }

    @Test
    fun unarmedOrTargetlessUpResetsWithoutTriggering() {
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.RESET,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.UP,
                SwipeQuoteTouchPolicy.CallbackPhase.TOUCH,
                armed = false,
                hasTarget = true
            )
        )
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.RESET,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.UP,
                SwipeQuoteTouchPolicy.CallbackPhase.TOUCH,
                armed = true,
                hasTarget = false
            )
        )
    }

    @Test
    fun cancelResetsAndCannotTriggerAnArmedGesture() {
        assertEquals(
            SwipeQuoteTouchPolicy.TerminalDecision.RESET,
            SwipeQuoteTouchPolicy.terminalDecision(
                SwipeQuoteTouchPolicy.TerminalAction.CANCEL,
                SwipeQuoteTouchPolicy.CallbackPhase.TOUCH,
                armed = true,
                hasTarget = true
            )
        )
    }
}
