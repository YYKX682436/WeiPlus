import h.Hchat.hooks.api.contact.ContactLabelIds;
import h.Hchat.hooks.items.batch.BatchSelectionPolicy;
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
        labelMembershipEdits();
        stateTransitions();
        serialResultsAndPartialFailure();
        stopPreventsLaterSubmissions();
        accountSwitchPreventsLaterSubmissions();
        System.out.println("Batch operation regression assertions passed");
    }

    private static void selectionFiltering() {
        List<String> candidates = Arrays.asList(
                "friend1", "group1@chatroom", "no-session", "wxid_hchat_group_abc",
                "filehelper", "", "friend1");
        Set<String> sessions = new HashSet<>(Arrays.asList("friend1", "group1@chatroom", "no-session"));
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
    }

    private static void labelMembershipEdits() {
        String original = "keep1,removeMe,unknown99\u0000";
        assertEquals("keep1,removeMe,unknown99,addMe\u0000",
                ContactLabelIds.setMembership(original, "addMe", true),
                "append must preserve all existing IDs");
        assertEquals("keep1,unknown99\u0000",
                ContactLabelIds.setMembership(original, "removeMe", false),
                "remove must preserve unrelated and unknown IDs");
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

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
