package h.Hchat.hooks.api.message;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class MessageClearCompletionPolicyTest {
    @Test
    public void emptyPartitionAndMainTablesConfirmStageTwoEvenWhenStageOneCallbackWasMissed() {
        assertEquals(MessageClearCompletionPolicy.Decision.COMPLETED,
                MessageClearCompletionPolicy.decide(false, false, false));
    }

    @Test
    public void populatedOrUnreadableMessageStateWaitsUntilBoundedTimeout() {
        assertEquals(MessageClearCompletionPolicy.Decision.WAIT,
                MessageClearCompletionPolicy.decide(true, false, false));
        assertEquals(MessageClearCompletionPolicy.Decision.WAIT,
                MessageClearCompletionPolicy.decide(null, false, false));
        assertEquals(MessageClearCompletionPolicy.Decision.UNCONFIRMED,
                MessageClearCompletionPolicy.decide(true, false, true));
        assertEquals(MessageClearCompletionPolicy.Decision.UNCONFIRMED,
                MessageClearCompletionPolicy.decide(null, false, true));
    }

    @Test
    public void stopAfterSubmissionKeepsAnInFlightClearUnconfirmed() {
        assertEquals(MessageClearCompletionPolicy.Decision.STOPPED,
                MessageClearCompletionPolicy.decide(false, true, false));
    }
}
