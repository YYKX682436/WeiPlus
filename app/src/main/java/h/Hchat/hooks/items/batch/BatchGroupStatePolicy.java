package h.Hchat.hooks.items.batch;

/** Conservative classifier for the current WeChat contact and chatroom rows. */
public final class BatchGroupStatePolicy {
    public static final int DISBANDED_STATUS_FLAG = 0x2;

    public enum State {
        JOINED,
        NOT_MEMBER,
        DISBANDED,
        CONTACT_MISSING,
        UNKNOWN
    }

    public enum CleanupReason {
        LEFT_OR_REMOVED,
        DISBANDED
    }

    private BatchGroupStatePolicy() {}

    public static State classify(boolean readSucceeded,
                                 boolean contactPresent,
                                 boolean chatroomRowPresent,
                                 String selfId,
                                 String memberList,
                                 int chatroomStatus) {
        return classify(readSucceeded, contactPresent, chatroomRowPresent,
                selfId, memberList, Integer.valueOf(chatroomStatus));
    }

    /** A missing optional status column stays unknown for disband detection, never flag value 0. */
    public static State classify(boolean readSucceeded,
                                 boolean contactPresent,
                                 boolean chatroomRowPresent,
                                 String selfId,
                                 String memberList,
                                 Integer chatroomStatus) {
        if (!readSucceeded) return State.UNKNOWN;
        if (!contactPresent) return State.CONTACT_MISSING;
        if (!chatroomRowPresent) return State.UNKNOWN;
        if (chatroomStatus != null && (chatroomStatus & DISBANDED_STATUS_FLAG) != 0) return State.DISBANDED;
        if (selfId == null || selfId.trim().isEmpty()
                || memberList == null || memberList.trim().isEmpty()) {
            return State.UNKNOWN;
        }
        boolean foundSelf = false;
        for (String member : memberList.split(";", -1)) {
            if (selfId.trim().equals(member.trim())) {
                foundSelf = true;
                break;
            }
        }
        return foundSelf ? State.JOINED : State.NOT_MEMBER;
    }

    /** Adds WeChat's native already-quit predicate as corroborating membership evidence. */
    public static State classify(boolean readSucceeded,
                                 boolean contactPresent,
                                 boolean chatroomRowPresent,
                                 String selfId,
                                 String memberList,
                                 Integer chatroomStatus,
                                 Boolean nativeAlreadyQuit) {
        State rosterState = classify(readSucceeded, contactPresent, chatroomRowPresent,
                selfId, memberList, chatroomStatus);
        if (rosterState == State.JOINED && !Boolean.FALSE.equals(nativeAlreadyQuit)) return State.UNKNOWN;
        if (rosterState == State.NOT_MEMBER && !Boolean.TRUE.equals(nativeAlreadyQuit)) return State.UNKNOWN;
        return rosterState;
    }

    public static boolean isLeaveCandidate(State state, String selfId, String roomOwner) {
        String self = clean(selfId);
        String owner = clean(roomOwner);
        return state == State.JOINED && !self.isEmpty() && !owner.isEmpty() && !owner.equals(self);
    }

    public static CleanupReason cleanupReason(State state) {
        if (state == State.DISBANDED) return CleanupReason.DISBANDED;
        if (state == State.NOT_MEMBER) return CleanupReason.LEFT_OR_REMOVED;
        return null;
    }

    public static boolean stillMatchesCleanupReason(State current, CleanupReason expected) {
        return expected != null && cleanupReason(current) == expected;
    }

    public static boolean shouldBlockRepeat(State state, boolean pendingRequest) {
        return pendingRequest && (state == State.JOINED || state == State.UNKNOWN);
    }

    public static boolean resolvesPendingRequest(State state) {
        return state == State.NOT_MEMBER || state == State.DISBANDED || state == State.CONTACT_MISSING;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
