package h.Hchat.hooks.items.messageblock;

/** Pure fail-open decision used by the API 102 pre-process chain callback. */
public final class MessageBlockDecision {
    private MessageBlockDecision() {}

    public static boolean shouldIntercept(boolean generationActive,
                                          boolean settingEnabled,
                                          boolean messageParsed,
                                          boolean ruleMatched,
                                          boolean notificationAllows) {
        return generationActive && settingEnabled && messageParsed && ruleMatched && !notificationAllows;
    }
}
