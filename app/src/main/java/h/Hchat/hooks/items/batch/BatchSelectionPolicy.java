package h.Hchat.hooks.items.batch;

import h.Hchat.hooks.api.conversation.WeChatConversationIdentity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure candidate filtering shared by batch pickers and its regression tests. */
public final class BatchSelectionPolicy {
    private BatchSelectionPolicy() {}

    public static List<String> filterCandidateIds(Iterable<String> candidateIds,
                                                   Set<String> conversationIds,
                                                   Set<String> friendIds,
                                                   Set<String> groupIds,
                                                   boolean requireExistingConversation,
                                                   boolean strictFriendsOnly,
                                                   boolean strictFriendsAndGroupsOnly) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (candidateIds == null) return new ArrayList<>();
        for (String id : candidateIds) {
            if (!WeChatConversationIdentity.isBatchOperableUsername(id)) continue;
            String target = id.trim();
            if (requireExistingConversation && (conversationIds == null || !conversationIds.contains(target))) {
                continue;
            }
            if (strictFriendsOnly && (friendIds == null || !friendIds.contains(target))) continue;
            if (strictFriendsAndGroupsOnly
                    && (friendIds == null || groupIds == null
                    || (!friendIds.contains(target) && !groupIds.contains(target)))) continue;
            result.add(target);
        }
        return new ArrayList<>(result);
    }

    /** Intersects a validated friend query with one label's exact member IDs, preserving friend order. */
    public static List<String> filterFriendLabelMembers(Iterable<String> validFriendIds,
                                                         Set<String> labelMemberIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (validFriendIds == null || labelMemberIds == null) return new ArrayList<>();
        for (String id : validFriendIds) {
            if (!WeChatConversationIdentity.isBatchOperableUsername(id)) continue;
            String target = id.trim();
            if (target.endsWith("@chatroom") || target.endsWith("@im.chatroom")) continue;
            if (labelMemberIds.contains(target)) result.add(target);
        }
        return new ArrayList<>(result);
    }

    /** Intersects the ordered valid-friend list with the current native starred-ID snapshot. */
    public static List<String> filterStarredFriendCandidates(Iterable<String> validFriendIds,
                                                              Set<String> starredFriendIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (validFriendIds == null || starredFriendIds == null) return new ArrayList<>();
        for (String id : validFriendIds) {
            if (!WeChatConversationIdentity.isBatchOperableUsername(id)) continue;
            String target = id.trim();
            if (target.endsWith("@chatroom") || target.endsWith("@im.chatroom")) continue;
            if (starredFriendIds.contains(target)) result.add(target);
        }
        return new ArrayList<>(result);
    }
}
