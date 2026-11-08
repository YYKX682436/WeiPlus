import h.Hchat.hooks.api.contact.ContactLabelIds;
import h.Hchat.hooks.api.contact.WeChatContactTypeState;
import h.Hchat.hooks.items.batch.BatchCandidateLoadGate;
import h.Hchat.hooks.items.batch.BatchGroupCleanupPolicy;
import h.Hchat.hooks.items.batch.BatchGroupSchemaPolicy;
import h.Hchat.hooks.items.batch.BatchGroupStatePolicy;
import h.Hchat.hooks.items.batch.BatchSelectionPolicy;
import h.Hchat.hooks.items.batch.BatchSelectionState;
import h.Hchat.hooks.items.batch.BatchStatePolicy;
import h.Hchat.hooks.items.batch.BatchTaskRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class BatchOperationRegression {
    public static void main(String[] args) {
        selectionFiltering();
        starredFriendCandidatesAreFilteredAgainstNativeSnapshot();
        labelMemberCandidatesAreExactAndOrdered();
        multiScopeSelectionState();
        searchSeparatesSelectedItemsFromMatches();
        staleCandidateLoadsCannotReplaceNewerScopes();
        labelChangesResetOldSelections();
        labelMembershipEdits();
        stateTransitions();
        groupStatusEvidenceFailsClosed();
        groupSchemaFailuresAreDistinctFromOptionalColumns();
        groupCleanupConversationStatesFailClosed();
        friendPermissionTypeBitsAreIndependent();
        serialResultsAndPartialFailure();
        stopPreventsLaterSubmissions();
        accountSwitchPreventsLaterSubmissions();
        progressCountsOnlyCompletedItems();
        System.out.println("Batch operation regression assertions passed");
    }

    private static void selectionFiltering() {
        List<String> candidates = Arrays.asList(
                "friend1", "group1@chatroom", "nonfriend@chat", "not-home",
                "wxid_hchat_group_abc", "filehelper", "message_fold", "", "friend1");
        Set<String> sessions = new HashSet<>(Arrays.asList(
                "friend1", "group1@chatroom", "nonfriend@chat", "filehelper", "message_fold"));
        Set<String> friends = new HashSet<>(Arrays.asList("friend1"));
        Set<String> groups = new HashSet<>(Arrays.asList("group1@chatroom"));
        List<String> chats = BatchSelectionPolicy.filterCandidateIds(
                candidates, sessions, friends, groups, true, false, true);
        assertEquals(Arrays.asList("friend1", "group1@chatroom"), chats,
                "chat selection must keep only existing operable friends/groups");
        List<String> strictFriends = BatchSelectionPolicy.filterCandidateIds(
                candidates, sessions, friends, groups, false, true, false);
        assertEquals(Arrays.asList("friend1"), strictFriends,
                "friend label selection must keep only valid friends");
        List<String> allContacts = BatchSelectionPolicy.filterCandidateIds(
                candidates, new HashSet<String>(), friends, groups, false, false, true);
        assertEquals(Arrays.asList("friend1", "group1@chatroom"), allContacts,
                "all-friends-and-groups actions must include non-chat friends while excluding virtual rows");
        List<String> allGroups = BatchSelectionPolicy.filterCandidateIds(
                Arrays.asList("group1@chatroom", "stale@chatroom", "wxid_hchat_group_abc"),
                new HashSet<String>(), new HashSet<String>(), groups, false, false, true);
        assertEquals(Arrays.asList("group1@chatroom"), allGroups,
                "group-wide operations must keep real group contacts and exclude virtual or stale rows");
        List<String> nativeHome = BatchSelectionPolicy.filterCandidateIds(
                candidates, sessions, friends, groups, true, false, false);
        assertEquals(Arrays.asList("friend1", "group1@chatroom", "nonfriend@chat", "filehelper"), nativeHome,
                "native home rows must keep non-friend chats and operable system chats while filtering virtual/pseudo rows");
    }

    private static void starredFriendCandidatesAreFilteredAgainstNativeSnapshot() {
        List<String> candidates = BatchSelectionPolicy.filterStarredFriendCandidates(
                Arrays.asList("friend-b", "friend-a", "group@chatroom", "wxid_hchat_group_fake", "friend-b"),
                new HashSet<>(Arrays.asList("friend-a", "friend-b", "group@chatroom", "stale-friend")));
        assertEquals(Arrays.asList("friend-b", "friend-a"), candidates,
                "unstar must show only ordered valid starred friend candidates");
        assertEquals(new java.util.ArrayList<String>(),
                BatchSelectionPolicy.filterStarredFriendCandidates(
                        Arrays.asList("friend-a"), new HashSet<String>()),
                "a successful empty starred state must produce an empty list");
    }

    private static void multiScopeSelectionState() {
        List<String> selected = BatchSelectionState.toggleVisible(
                new java.util.ArrayList<>(), Arrays.asList("chat-a", "chat-b"));
        assertEquals(Arrays.asList("chat-a", "chat-b"), selected,
                "select-all must preserve the visible candidate order");

        List<String> allScopeOrder = BatchSelectionState.selectedFirst(
                Arrays.asList("friend-c", "chat-b", "friend-d"), selected);
        assertEquals(Arrays.asList("chat-b", "friend-c", "friend-d"), allScopeOrder,
                "selected candidates must be moved to the front without reordering other rows");
        List<String> displayedWithRetained = new java.util.ArrayList<>(selected);
        for (String id : allScopeOrder) if (!displayedWithRetained.contains(id)) displayedWithRetained.add(id);
        assertEquals(Arrays.asList("chat-a", "chat-b", "friend-c", "friend-d"), displayedWithRetained,
                "selected rows outside the current scope must remain visible at the top");

        Set<String> afterToggle = new java.util.LinkedHashSet<>(selected);
        afterToggle.addAll(allScopeOrder);
        List<String> retained = BatchSelectionState.updateOrder(selected, afterToggle,
                Arrays.asList("friend-c", "chat-b", "friend-d"));
        assertEquals(Arrays.asList("chat-a", "chat-b", "friend-c", "friend-d"), retained,
                "switching scopes must retain hidden selections and append newly selected IDs");

        List<String> cancelVisible = BatchSelectionState.toggleVisible(retained,
                Arrays.asList("chat-b", "friend-c", "friend-d"));
        assertEquals(Arrays.asList("chat-a"), cancelVisible,
                "cancel-all must clear only the current display results");
        List<String> currentDisplay = new java.util.ArrayList<>(cancelVisible);
        for (String id : BatchSelectionState.selectedFirst(
                Arrays.asList("friend-c", "friend-d"), cancelVisible)) {
            if (!currentDisplay.contains(id)) currentDisplay.add(id);
        }
        assertEquals(Arrays.asList("chat-a", "friend-c", "friend-d"), currentDisplay,
                "a retained out-of-scope selection remains selected across range changes");
    }

    private static void labelMemberCandidatesAreExactAndOrdered() {
        List<String> members = BatchSelectionPolicy.filterFriendLabelMembers(
                Arrays.asList("friend-b", "friend-a", "group@chatroom", "wxid_hchat_group_fake", "friend-c"),
                new HashSet<>(Arrays.asList("friend-a", "group@chatroom", "stale-friend")));
        assertEquals(Arrays.asList("friend-a"), members,
                "remove-tag candidates must be the exact valid friend members in friend order");
        assertEquals(new java.util.ArrayList<String>(),
                BatchSelectionPolicy.filterFriendLabelMembers(
                        Arrays.asList("friend-a"), new HashSet<String>()),
                "an empty member relation is a valid empty candidate list");
    }

    private static void searchSeparatesSelectedItemsFromMatches() {
        List<String> selectionOrder = Arrays.asList("outside-scope", "matched-selected");
        List<String> searchMatches = Arrays.asList("matched-first", "matched-selected", "matched-last");
        assertEquals(Arrays.asList("outside-scope"),
                BatchSelectionState.selectedOutsideMatches(selectionOrder, searchMatches),
                "selected entries that do not match must stay in their separate selected section");
        assertEquals(Arrays.asList("matched-selected", "matched-first", "matched-last"),
                BatchSelectionState.selectedFirst(searchMatches, selectionOrder),
                "search results must contain matching entries only and keep selected matches first");
    }

    private static void staleCandidateLoadsCannotReplaceNewerScopes() {
        BatchCandidateLoadGate gate = new BatchCandidateLoadGate();
        long oldScopeLoad = gate.beginLoad();
        long newestTagLoad = gate.beginLoad();
        assertTrue(!gate.isCurrent(oldScopeLoad),
                "a late result from a prior scope/tag request must be discarded");
        assertTrue(gate.isCurrent(newestTagLoad),
                "only the newest candidate request may update the visible picker");
        gate.invalidate();
        assertTrue(!gate.isCurrent(newestTagLoad),
                "disposing the picker must invalidate outstanding candidate callbacks");
    }

    private static void labelChangesResetOldSelections() {
        assertTrue(BatchSelectionState.shouldResetForKeyChange("true:label-a", "true:label-b"),
                "changing the target label must clear selected members");
        assertTrue(BatchSelectionState.shouldResetForKeyChange("true:label-a", "false:label-a"),
                "switching between append and removal must clear stale selections");
        assertTrue(!BatchSelectionState.shouldResetForKeyChange("false:label-a", "false:label-a"),
                "reloading the same tag/action must preserve the current selection");
        assertTrue(BatchSelectionState.shouldResetForKeyChange("starred", "all-friends"),
                "switching star/unstar candidate modes must clear the prior selection");
    }

    private static void labelMembershipEdits() {
        assertEquals(Boolean.FALSE, ContactLabelIds.membershipState("", "first"),
                "a successfully-read empty label list is a valid nonmembership state");
        assertEquals(null, ContactLabelIds.membershipState(null, "first"),
                "a failed label-row read remains unknown rather than becoming an empty list");
        String original = "keep1,removeMe,unknown99\u0000";
        assertEquals("keep1,removeMe,unknown99,addMe\u0000",
                ContactLabelIds.setMembership(original, "addMe", true),
                "append must preserve all existing IDs");
        assertEquals("keep1,unknown99\u0000",
                ContactLabelIds.setMembership(original, "removeMe", false),
                "remove must preserve unrelated and unknown IDs");
        assertEquals("keep1, unknown-historical ,new\u0000",
                ContactLabelIds.setMembership("keep1, unknown-historical \u0000", "new", true),
                "append must preserve unknown historical IDs without normalizing their stored value");
        assertEquals("keep1,unknown99\u0000",
                ContactLabelIds.setMembership("keep1, removeMe ,unknown99\u0000", "removeMe", false),
                "remove must match the selected ID while preserving other raw ID tokens");
        assertEquals("", ContactLabelIds.setMembership("only\u0000", "only", false),
                "removing the last label must produce an empty list");
        assertEquals(original, ContactLabelIds.setMembership(original, "removeMe", true),
                "a no-op must keep the exact stored value");
        assertEquals("new\u0000", ContactLabelIds.setMembership(null, "new", true),
                "append to an empty list must use WeChat's native terminator");
        assertTrue(ContactLabelIds.setMembership(original, "", true) == null,
                "invalid label IDs must be rejected");
    }

    private static void stateTransitions() {
        assertTrue(BatchStatePolicy.alreadySatisfied(false, false),
                "disable should skip when state is already off");
        assertTrue(!BatchStatePolicy.alreadySatisfied(false, true),
                "enable should submit when state is off");
        assertTrue(BatchStatePolicy.verified(true, true),
                "enable should verify after state turns on");
        assertTrue(!BatchStatePolicy.verified(false, true),
                "a stale off state must not be reported as success");
        assertTrue(!BatchStatePolicy.alreadySatisfied(null, false)
                        && !BatchStatePolicy.verified(null, false),
                "unknown state must not count as satisfied or verified");
    }

    private static void groupStatusEvidenceFailsClosed() {
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN,
                BatchGroupStatePolicy.classify(false, true, true, "self", "other", 0),
                "a failed database read must stay unknown");
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN,
                BatchGroupStatePolicy.classify(true, true, false, "self", "other", 0),
                "a missing chatroom row must not prove an invalid group");
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN,
                BatchGroupStatePolicy.classify(true, true, true, "self", "", 0),
                "an empty member list must not prove an invalid group");
        assertEquals(BatchGroupStatePolicy.State.CONTACT_MISSING,
                BatchGroupStatePolicy.classify(true, false, true, "self", "other", 0),
                "a missing contact row is not a residual group cleanup candidate");
        assertEquals(BatchGroupStatePolicy.State.JOINED,
                BatchGroupStatePolicy.classify(true, true, true, "self", "member;self", 0),
                "the current member list must confirm the current account is still joined");
        assertEquals(BatchGroupStatePolicy.State.NOT_MEMBER,
                BatchGroupStatePolicy.classify(true, true, true, "self", "member-a;member-b", 0),
                "a nonempty member list without self confirms left or removed state");
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN,
                BatchGroupStatePolicy.classify(true, true, true, "self", "member-a;member-b", 0, null),
                "missing native already-quit evidence must not confirm a stale roster");
        assertEquals(BatchGroupStatePolicy.State.UNKNOWN,
                BatchGroupStatePolicy.classify(true, true, true, "self", "member-a;member-b", 0, false),
                "a disagreement with WeChat's native already-quit predicate must stay unknown");
        assertEquals(BatchGroupStatePolicy.State.NOT_MEMBER,
                BatchGroupStatePolicy.classify(true, true, true, "self", "member-a;member-b", 0, true),
                "cleanup eligibility requires WeChat's native already-quit confirmation");
        assertEquals(BatchGroupStatePolicy.State.JOINED,
                BatchGroupStatePolicy.classify(true, true, true, "self", "member-a;self", 0, false),
                "the roster and native current-membership check must agree before leave");
        assertEquals(BatchGroupStatePolicy.State.DISBANDED,
                BatchGroupStatePolicy.classify(true, true, true, "self", "", 0x2),
                "the native disband status flag is independent evidence from an empty roster");
        assertTrue(!BatchGroupStatePolicy.isLeaveCandidate(
                        BatchGroupStatePolicy.State.JOINED, "self", "self"),
                "the room owner must not enter the automatic leave request");
        assertTrue(!BatchGroupStatePolicy.stillMatchesCleanupReason(
                        BatchGroupStatePolicy.State.JOINED,
                        BatchGroupStatePolicy.CleanupReason.LEFT_OR_REMOVED),
                "a group that becomes usable before cleanup must not be deleted");
        assertTrue(BatchGroupStatePolicy.shouldBlockRepeat(
                        BatchGroupStatePolicy.State.JOINED, true),
                "a timed-out leave cannot be submitted again while native membership still says joined");
        assertTrue(BatchGroupStatePolicy.shouldBlockRepeat(
                        BatchGroupStatePolicy.State.UNKNOWN, true),
                "an unknown group state cannot be resubmitted while a prior request is unresolved");
        assertTrue(BatchGroupStatePolicy.resolvesPendingRequest(
                        BatchGroupStatePolicy.State.NOT_MEMBER),
                "a native membership change resolves the pending leave guard");
    }

    private static void groupSchemaFailuresAreDistinctFromOptionalColumns() {
        BatchGroupSchemaPolicy.Result noStatus = BatchGroupSchemaPolicy.evaluate(
                true, new HashSet<>(Arrays.asList("username")),
                true, new HashSet<>(Arrays.asList("chatroomname", "memberlist", "roomowner")));
        assertTrue(noStatus.success && noStatus.hasRoomOwner && !noStatus.hasChatroomStatus,
                "a missing optional disband bit must not invalidate normal membership reads");

        BatchGroupSchemaPolicy.Result failed = BatchGroupSchemaPolicy.evaluate(
                false, new HashSet<>(), true, new HashSet<>(Arrays.asList("chatroomname", "memberlist")));
        assertTrue(!failed.success && "rcontact 表结构读取失败".equals(failed.error),
                "a failed table metadata read must remain different from a successful empty group result");

        BatchGroupSchemaPolicy.Result missingMembers = BatchGroupSchemaPolicy.evaluate(
                true, new HashSet<>(Arrays.asList("username")),
                true, new HashSet<>(Arrays.asList("chatroomname")));
        assertTrue(!missingMembers.success && missingMembers.error.contains("memberlist"),
                "missing required member data must report its required column");
    }

    private static void groupCleanupConversationStatesFailClosed() {
        assertEquals(BatchGroupCleanupPolicy.Action.DELETE_CONVERSATION_THEN_GROUP,
                BatchGroupCleanupPolicy.decide(true, BatchGroupCleanupPolicy.ConversationState.EXISTS),
                "an existing session must use the native conversation removal path first");
        assertEquals(BatchGroupCleanupPolicy.Action.DELETE_GROUP_ONLY,
                BatchGroupCleanupPolicy.decide(true, BatchGroupCleanupPolicy.ConversationState.ABSENT),
                "an absent session must not block native residual group cleanup");
        assertEquals(BatchGroupCleanupPolicy.Action.ABORT,
                BatchGroupCleanupPolicy.decide(true, BatchGroupCleanupPolicy.ConversationState.READ_FAILED),
                "an unread session state must stop cleanup");
        assertEquals(BatchGroupCleanupPolicy.Action.ABORT,
                BatchGroupCleanupPolicy.decide(false, BatchGroupCleanupPolicy.ConversationState.ABSENT),
                "a group that no longer proves invalid must stop cleanup");
    }

    private static void friendPermissionTypeBitsAreIndependent() {
        int type = WeChatContactTypeState.STARRED_FRIEND
                | WeChatContactTypeState.DO_NOT_LET_SEE_MOMENTS
                | WeChatContactTypeState.ONLY_CHAT
                | 0x200000;
        assertTrue(WeChatContactTypeState.matches(type, WeChatContactTypeState.STARRED_FRIEND, true),
                "starred state must read the native contact type bit");
        assertTrue(WeChatContactTypeState.matches(type, WeChatContactTypeState.DO_NOT_LET_SEE_MOMENTS, true),
                "the independent outside permission bit must be read independently");
        assertTrue(WeChatContactTypeState.matches(type, WeChatContactTypeState.ONLY_CHAT, true),
                "only-chat permission must be read independently");
        assertTrue(!WeChatContactTypeState.matches(type & ~WeChatContactTypeState.ONLY_CHAT,
                        WeChatContactTypeState.ONLY_CHAT, true)
                        && WeChatContactTypeState.matches(type & ~WeChatContactTypeState.ONLY_CHAT,
                        WeChatContactTypeState.STARRED_FRIEND, true)
                        && WeChatContactTypeState.matches(type & ~WeChatContactTypeState.ONLY_CHAT,
                        WeChatContactTypeState.DO_NOT_LET_SEE_MOMENTS, true),
                "changing one permission state must not alter the other independent flags");
    }

    private static void serialResultsAndPartialFailure() {
        BatchTaskRunner.Report report = BatchTaskRunner.run(
                Arrays.asList("ok", "bad", "unknown", "already"), "account",
                () -> "account", new BatchTaskRunner.StopSignal(), 0,
                (id, stop) -> {
                    switch (id) {
                        case "ok": return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "verified");
                        case "bad": return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "rejected");
                        case "unknown": return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "submitted");
                        default: return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "already done");
                    }
                }, null);
        assertEquals(4, report.attempted, "all test targets should be attempted serially");
        assertEquals(1, report.success, "verified successes should be counted separately");
        assertEquals(1, report.failed, "partial failure should be counted");
        assertEquals(1, report.unconfirmed, "submitted but unconfirmed should be counted separately");
        assertEquals(1, report.skipped, "idempotent targets should be skipped");
    }

    private static void stopPreventsLaterSubmissions() {
        BatchTaskRunner.StopSignal stop = new BatchTaskRunner.StopSignal();
        AtomicInteger submissions = new AtomicInteger();
        BatchTaskRunner.Report report = BatchTaskRunner.run(
                Arrays.asList("one", "two", "three"), "account", () -> "account", stop, 0,
                (id, signal) -> {
                    submissions.incrementAndGet();
                    signal.stop();
                    return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "done");
                }, null);
        assertEquals(1, submissions.get(), "stop must prevent later task submissions");
        assertEquals(1, report.attempted, "only the in-flight item should be attempted");
        assertEquals(2, report.skipped, "remaining items should be counted as skipped");
    }

    private static void accountSwitchPreventsLaterSubmissions() {
        AtomicInteger identityReads = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();
        BatchTaskRunner.Report report = BatchTaskRunner.run(
                Arrays.asList("one", "two"), "account", () -> identityReads.incrementAndGet() == 1
                        ? "account" : "another-account",
                new BatchTaskRunner.StopSignal(), 0,
                (id, stop) -> {
                    submissions.incrementAndGet();
                    return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "done");
                }, null);
        assertEquals(1, submissions.get(), "account switch must stop later submissions");
        assertEquals(1, report.skipped, "remaining items after account switch should be skipped");
    }

    private static void progressCountsOnlyCompletedItems() {
        AtomicInteger completed = new AtomicInteger();
        BatchTaskRunner.Report report = BatchTaskRunner.run(
                Arrays.asList("one", "two"), "account", () -> "account", new BatchTaskRunner.StopSignal(), 0,
                (id, stop) -> BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "done"),
                new BatchTaskRunner.ProgressListener() {
                    @Override public void onStart(int index, String id) {
                        assertEquals(index - 1, completed.get(), "onStart must not count an unfinished task");
                    }

                    @Override public void onResult(int index, String id, BatchTaskRunner.TaskResult result) {
                        completed.set(index);
                    }
                });
        assertEquals(2, report.attempted, "both progress test tasks should run");
        assertEquals(2, completed.get(), "completed count should advance after each result callback");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
