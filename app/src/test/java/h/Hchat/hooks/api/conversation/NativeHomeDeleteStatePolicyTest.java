package h.Hchat.hooks.api.conversation;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class NativeHomeDeleteStatePolicyTest {
    @Test
    public void confirmsOnlyWhenRowAndNativeHomeQueryBothOmitConversation() {
        assertEquals(WeChatConversationApi.NativeHomeDeleteState.CONFIRMED_REMOVED,
                NativeHomeDeleteStatePolicy.classify(false, false, 1, 0));
        assertEquals(WeChatConversationApi.NativeHomeDeleteState.HOME_STILL_CONTAINS,
                NativeHomeDeleteStatePolicy.classify(false, true, 1, 0));
        assertEquals(WeChatConversationApi.NativeHomeDeleteState.HOME_REMOVED_ROW_RETAINED,
                NativeHomeDeleteStatePolicy.classify(true, false, 0, 0));
    }

    @Test
    public void distinguishesRecreatedRowFromRemainingRowAndUnavailableState() {
        assertEquals(WeChatConversationApi.NativeHomeDeleteState.ROW_RECREATED,
                NativeHomeDeleteStatePolicy.classify(true, true, 1, 1));
        assertEquals(WeChatConversationApi.NativeHomeDeleteState.UNCONFIRMED,
                NativeHomeDeleteStatePolicy.classify(null, false, 0, 0));
    }
}
