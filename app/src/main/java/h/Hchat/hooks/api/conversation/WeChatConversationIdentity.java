package h.Hchat.hooks.api.conversation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Rejects Hchat virtual talks and structural folder rows while preserving real special talks
 * returned by WeChat's native home query (for example, filehelper).
 */
public final class WeChatConversationIdentity {
    private static final String HCHAT_VIRTUAL_PREFIX = "wxid_hchat_group_";
    private static final Set<String> STRUCTURAL_ROWS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "message_fold", "conversationboxservice", "@placeholder_foldgroup"
    )));

    private WeChatConversationIdentity() {}

    public static boolean isBatchOperableUsername(String username) {
        if (username == null || username.isEmpty()) return false;
        String value = username.trim();
        return !value.isEmpty()
                && !value.startsWith(HCHAT_VIRTUAL_PREFIX)
                && !STRUCTURAL_ROWS.contains(value);
    }
}
