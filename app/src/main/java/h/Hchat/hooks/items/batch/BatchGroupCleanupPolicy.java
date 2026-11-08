package h.Hchat.hooks.items.batch;

/** Fail-closed routing for native cleanup when a residual group has no conversation row. */
public final class BatchGroupCleanupPolicy {
    public enum ConversationState { EXISTS, ABSENT, READ_FAILED }
    public enum Action { DELETE_CONVERSATION_THEN_GROUP, DELETE_GROUP_ONLY, ABORT }

    private BatchGroupCleanupPolicy() {}

    public static Action decide(boolean groupStillConfirmedInvalid, ConversationState conversationState) {
        if (!groupStillConfirmedInvalid || conversationState == null
                || conversationState == ConversationState.READ_FAILED) return Action.ABORT;
        return conversationState == ConversationState.EXISTS
                ? Action.DELETE_CONVERSATION_THEN_GROUP
                : Action.DELETE_GROUP_ONLY;
    }
}
