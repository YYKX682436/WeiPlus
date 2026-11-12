package h.Hchat.hooks.items.messageblock;

/** Pure guards for silently dropping a verified one-to-one VoIP invitation. */
public final class MessageBlockVoipCallDecision {
    private MessageBlockVoipCallDecision() {}

    /** WeChat 8.0.80 room type 0 is the video branch and 1 is the voice branch. */
    public static int messageTypeForRoomType(int roomType) {
        if (roomType == 0) return 1000053;
        if (roomType == 1) return 1000052;
        return 0;
    }

    /** Official VoIPMP room enum: voice=1, video=2; MultiTalk and unknown modes fail open. */
    public static int messageTypeForIlinkRoomType(int roomType) {
        if (roomType == 1) return 1000052;
        if (roomType == 2) return 1000053;
        return 0;
    }

    /**
     * WeChat 8.0.80's Ji record callback treats roomType 1 as video and 0/2 as voice;
     * MultiTalk values above 2 and unknown values fail open.
     */
    public static int messageTypeForIlinkRecordType(int roomType) {
        if (roomType == 0 || roomType == 2) return 1000052;
        if (roomType == 1) return 1000053;
        return 0;
    }

    /**
     * Ji inserts every private voice/video call result. Hide any incoming result for a blocked
     * peer; outgoing calls and group/unknown room types remain unaffected.
     */
    public static boolean isIncomingPrivateVoipRecord(boolean localCaller, int roomType) {
        return !localCaller && messageTypeForIlinkRecordType(roomType) != 0;
    }

    /** WeChat's generic notification bridge receives VoIP message types 50/1000052/1000053. */
    public static boolean isIncomingVoipMessageNotification(int notificationType,
                                                            int storedMessageType,
                                                            int isSend) {
        return isVoipMessageType(notificationType) &&
                isVoipMessageType(storedMessageType) &&
                notificationType == storedMessageType &&
                (isSend == 0 || storedMessageType == 50 && isSend == 2);
    }

    private static boolean isVoipMessageType(int type) {
        return type == 50 || type == 1000052 || type == 1000053;
    }

    /**
     * A live VoIP manager carries its own local wxid. Use it in a minimal process only when the
     * account API is unavailable; an explicit API mismatch always fails open.
     */
    public static boolean isReceiverIdentityTrusted(boolean receiverIdPresent,
                                                   boolean accountIdPresent,
                                                   boolean accountMatchesReceiver,
                                                   boolean allowReceiverFallback) {
        return receiverIdPresent && (accountMatchesReceiver ||
                allowReceiverFallback && !accountIdPresent);
    }

    public static boolean shouldIgnore(boolean generationActive,
                                       boolean settingsEnabled,
                                       boolean incomingRequest,
                                       boolean inviteParsed,
                                       boolean privateCaller,
                                       int roomType,
                                       boolean ruleMatched) {
        return shouldIgnoreMessageType(
                generationActive,
                settingsEnabled,
                incomingRequest,
                inviteParsed,
                privateCaller,
                messageTypeForRoomType(roomType),
                ruleMatched);
    }

    /** Applies the same private VoIP rule gate when a protocol path does not expose roomType. */
    public static boolean shouldIgnoreMessageType(boolean generationActive,
                                                  boolean settingsEnabled,
                                                  boolean incomingRequest,
                                                  boolean inviteParsed,
                                                  boolean privateCaller,
                                                  int messageType,
                                                  boolean ruleMatched) {
        return generationActive && settingsEnabled && incomingRequest && inviteParsed && privateCaller &&
                (messageType == 1000052 || messageType == 1000053) && ruleMatched;
    }

    /** A missing ILINK room-type discriminator is safe only when both call variants match. */
    public static boolean shouldIgnoreUnknownVoipVariant(boolean generationActive,
                                                         boolean settingsEnabled,
                                                         boolean incomingRequest,
                                                         boolean inviteParsed,
                                                         boolean privateCaller,
                                                         boolean voiceRuleMatched,
                                                         boolean videoRuleMatched) {
        return shouldIgnoreMessageType(
                generationActive,
                settingsEnabled,
                incomingRequest,
                inviteParsed,
                privateCaller,
                1000052,
                voiceRuleMatched && videoRuleMatched);
    }

    /** The official code-12 bridge emits only this subtype/XML shape for an ILINK invite. */
    public static boolean isIlinkInviteShape(String messageSubtype,
                                             boolean inviteXmlParsed,
                                             boolean incomingNotifySource) {
        return "ilinkvoip".equals(messageSubtype) && inviteXmlParsed && incomingNotifySource;
    }
}
