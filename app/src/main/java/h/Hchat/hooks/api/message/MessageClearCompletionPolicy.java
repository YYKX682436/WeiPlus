package h.Hchat.hooks.api.message;

/** Post-submission gate for advancing from native message cleanup to conversation deletion. */
public final class MessageClearCompletionPolicy {
    public enum Decision { COMPLETED, WAIT, UNCONFIRMED, STOPPED }

    private MessageClearCompletionPolicy() {}

    /**
     * Stage 1's callback is diagnostic only: Stage 2 is confirmed by empty partition and main
     * message tables. A stop after submission leaves the already-started native operation unknown.
     */
    public static Decision decide(Boolean messagesRemain, boolean stopped, boolean timedOut) {
        if (stopped) return Decision.STOPPED;
        if (Boolean.FALSE.equals(messagesRemain)) return Decision.COMPLETED;
        if (timedOut) return Decision.UNCONFIRMED;
        return Decision.WAIT;
    }
}
