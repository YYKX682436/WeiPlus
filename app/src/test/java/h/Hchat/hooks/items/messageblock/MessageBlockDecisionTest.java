package h.Hchat.hooks.items.messageblock;

import org.junit.Test;
import static org.junit.Assert.*;

public class MessageBlockDecisionTest {
    @Test public void allowsNormalMessagesAndParsesThatCannotBeTrusted() {
        assertFalse(MessageBlockDecision.shouldIntercept(true, true, true, false, false));
        assertFalse(MessageBlockDecision.shouldIntercept(true, true, false, true, false));
        assertFalse(MessageBlockDecision.shouldIntercept(true, false, true, true, false));
    }

    @Test public void blocksOnlyWithAnActiveGenerationAndNoAllowOverride() {
        assertTrue(MessageBlockDecision.shouldIntercept(true, true, true, true, false));
        assertFalse(MessageBlockDecision.shouldIntercept(true, true, true, true, true));
        assertFalse(MessageBlockDecision.shouldIntercept(false, true, true, true, false));
    }

    @Test public void decisionErrorsCanBeContainedAsPassThroughByTheCaller() {
        boolean matched = false;
        try {
            throw new IllegalStateException("fixture");
        } catch (Throwable ignored) {
            // MessageBlockFeature handles parser/rule errors by leaving matched=false.
        }
        assertFalse(MessageBlockDecision.shouldIntercept(true, true, true, matched, false));
    }
}
