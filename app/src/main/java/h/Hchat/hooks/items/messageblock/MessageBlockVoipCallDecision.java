package h.Hchat.hooks.items.messageblock;

/** Pure guards for the verified one-to-one VoIP invitation path. */
public final class MessageBlockVoipCallDecision {
    private MessageBlockVoipCallDecision() {}

    /** WeChat 8.0.80 room type 0 is the video branch and 1 is the voice branch. */
    public static int messageTypeForRoomType(int roomType) {
        if (roomType == 0) return 1000053;
        if (roomType == 1) return 1000052;
        return 0;
    }

    public static boolean shouldReject(boolean generationActive,
                                       boolean settingsEnabled,
                                       boolean incomingRequest,
                                       boolean privateCaller,
                                       int roomType,
                                       boolean ruleMatched,
                                       boolean rejectAvailable) {
        return generationActive && settingsEnabled && incomingRequest && privateCaller &&
                messageTypeForRoomType(roomType) != 0 && ruleMatched && rejectAvailable;
    }
}
