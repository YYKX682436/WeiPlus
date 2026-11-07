import h.Hchat.hooks.api.contact.ContactLabelIds;
import h.Hchat.hooks.items.batch.BatchSelectionPolicy;
import h.Hchat.hooks.items.batch.BatchStatePolicy;
import h.Hchat.hooks.items.batch.BatchTaskLifecycle;
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
        lifecycleSurvivesStateTransitionsAndRestarts();
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
        List<String> nativeHome = BatchSelectionPolicy.filterCandidateIds(
                candidates, sessions, friends, groups, true, false, false);
        assertEquals(Arrays.asList("friend1", "group1@chatroom", "nonfriend@chat", "filehelper"), nativeHome,
                "native home rows must keep non-friend chats and operable system chats while filtering virtual/pseudo rows");
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

    private static void lifecycleSurvivesStateTransitionsAndRestarts() {
        BatchTaskLifecycle lifecycle = new BatchTaskLifecycle();
        BatchTaskLifecycle.Session first = lifecycle.begin();
        AtomicInteger firstStarts = new AtomicInteger();
        BatchTaskRunner.Report firstReport = BatchTaskRunner.run(
                Arrays.asList("first-task"), "account", () -> "account", first.stopSignal(), 0,
                (id, stop) -> {
                    firstStarts.incrementAndGet();
                    return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "executed");
                }, null);
        assertEquals(1, firstStarts.get(), "a newly-created session must start its first task");
        assertEquals(1, firstReport.attempted, "the first session should complete its task");
        assertTrue(lifecycle.finish(first), "finishing the current session should succeed");

        BatchTaskLifecycle.Session second = lifecycle.begin();
        assertTrue(!lifecycle.stop(first), "disposing the old effect must not stop a newer session");
        assertTrue(!second.stopSignal().isStopped(), "a normal state transition must not stop the new task");
        AtomicInteger secondStarts = new AtomicInteger();
        BatchTaskRunner.Report secondReport = BatchTaskRunner.run(
                Arrays.asList("restart-task"), "account", () -> "account", second.stopSignal(), 0,
                (id, stop) -> {
                    secondStarts.incrementAndGet();
                    return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "executed");
                }, null);
        assertEquals(1, secondStarts.get(), "a completed page must be able to start another task");
        assertEquals(1, secondReport.attempted, "the restarted session should execute");
        assertTrue(lifecycle.finish(second), "the restarted session should finish cleanly");

        BatchTaskLifecycle.Session third = lifecycle.begin();
        assertTrue(lifecycle.stop(third), "active session stop should be accepted");
        assertEquals(0, BatchTaskRunner.run(
                Arrays.asList("must-not-start"), "account", () -> "account", third.stopSignal(), 0,
                (id, stop) -> BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "unexpected"),
                null).attempted, "stop before startup must prevent the first submission");
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
