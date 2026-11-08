package h.Hchat.hooks.api.conversation;

/** Classifies the native home-list and rconversation state after WeChat's own delete helper. */
public final class NativeHomeDeleteStatePolicy {
    private NativeHomeDeleteStatePolicy() {}

    public static WeChatConversationApi.NativeHomeDeleteState classify(
            Boolean rowPresent, Boolean nativeHomeContains, int deleteEvents, int insertEvents) {
        if (rowPresent == null || nativeHomeContains == null) {
            return WeChatConversationApi.NativeHomeDeleteState.UNCONFIRMED;
        }
        if (!rowPresent && !nativeHomeContains) {
            return WeChatConversationApi.NativeHomeDeleteState.CONFIRMED_REMOVED;
        }
        if (rowPresent && deleteEvents > 0 && insertEvents > 0) {
            return WeChatConversationApi.NativeHomeDeleteState.ROW_RECREATED;
        }
        if (nativeHomeContains) {
            return WeChatConversationApi.NativeHomeDeleteState.HOME_STILL_CONTAINS;
        }
        if (rowPresent) return WeChatConversationApi.NativeHomeDeleteState.ROW_REMAINS;
        return WeChatConversationApi.NativeHomeDeleteState.HOME_STILL_CONTAINS;
    }
}
