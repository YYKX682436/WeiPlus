package h.Hchat.hooks.api.contact;

/** Result of a native WeChat mutation, with queue submission kept separate from confirmation. */
public final class WeChatNativeMutationResult {
    public enum Outcome {
        CONFIRMED,
        SERVER_ACCEPTED,
        SUBMITTED_UNCONFIRMED,
        SERVER_REJECTED,
        NOT_SUBMITTED,
        ALREADY_SATISFIED,
        INVALID_TARGET,
        READ_FAILED
    }

    public final Outcome outcome;
    public final String message;

    public WeChatNativeMutationResult(Outcome outcome, String message) {
        this.outcome = outcome == null ? Outcome.NOT_SUBMITTED : outcome;
        this.message = message == null ? "" : message;
    }

    public static WeChatNativeMutationResult of(Outcome outcome, String message) {
        return new WeChatNativeMutationResult(outcome, message);
    }
}
