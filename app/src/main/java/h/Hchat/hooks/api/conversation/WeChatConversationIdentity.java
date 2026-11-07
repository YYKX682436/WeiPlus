package h.Hchat.hooks.api.conversation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Shared eligibility rules for user conversations that support native batch actions. */
public final class WeChatConversationIdentity {
    private static final String HCHAT_VIRTUAL_PREFIX = "wxid_hchat_group_";
    private static final Set<String> NON_OPERABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "filehelper", "fmessage", "tmessage", "qqmail", "weixin", "floatbottle",
            "medianote", "medianote@chatroom", "newsapp", "masssend", "feedsapp", "blogapp",
            "message_fold", "conversationboxservice", "@placeholder_foldgroup"
    )));

    private WeChatConversationIdentity() {}

    public static boolean isBatchOperableUsername(String username) {
        if (username == null || username.isEmpty()) return false;
        String value = username.trim();
        return !value.isEmpty()
                && !value.startsWith(HCHAT_VIRTUAL_PREFIX)
                && !NON_OPERABLE.contains(value);
    }
}
