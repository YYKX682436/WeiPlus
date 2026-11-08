package h.Hchat.hooks.api.contact;

/** Classifies native friend-permission responses without allowing a local bit alone to claim success. */
final class WeChatPermissionResultPolicy {
    private WeChatPermissionResultPolicy() {}

    static WeChatNativeMutationResult.Outcome classify(
            ContactRequestDispatch.Result dispatch,
            boolean stateReadSucceeded,
            boolean targetStateMatches) {
        if (dispatch != null && dispatch.outcome == ContactRequestDispatch.Outcome.SERVER_REJECTED) {
            return WeChatNativeMutationResult.Outcome.SERVER_REJECTED;
        }
        if (!stateReadSucceeded) {
            return dispatch == null || dispatch.wasSubmitted()
                    || dispatch.outcome == ContactRequestDispatch.Outcome.DISPATCH_FAILED
                    ? WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED
                    : WeChatNativeMutationResult.Outcome.READ_FAILED;
        }
        if (dispatch == null) return WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED;
        switch (dispatch.outcome) {
            case SERVER_ACCEPTED:
                return targetStateMatches
                        ? WeChatNativeMutationResult.Outcome.CONFIRMED
                        : WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED;
            case NOT_SUBMITTED:
                return targetStateMatches
                        ? WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED
                        : WeChatNativeMutationResult.Outcome.NOT_SUBMITTED;
            case DISPATCH_FAILED:
            case TIMED_OUT:
            case INTERRUPTED:
                return WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED;
            case SERVER_REJECTED:
                return WeChatNativeMutationResult.Outcome.SERVER_REJECTED;
            default:
                return WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED;
        }
    }
}
