package h.Hchat.hooks.items.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Serial, cancellable, account-scoped batch executor with in-memory result statistics. */
public final class BatchTaskRunner {
    public enum Outcome {
        SUCCESS,
        FAILED,
        SKIPPED,
        UNCONFIRMED
    }

    public interface AccountIdentity {
        String current();
    }

    public interface Task {
        TaskResult execute(String id, StopSignal stopSignal) throws Throwable;
    }

    public interface ProgressListener {
        void onStart(int index, String id);
        void onResult(int index, String id, TaskResult result);
    }

    public static final class TaskResult {
        public final Outcome outcome;
        public final String message;

        public TaskResult(Outcome outcome, String message) {
            this.outcome = outcome == null ? Outcome.FAILED : outcome;
            this.message = message == null ? "" : message;
        }

        public static TaskResult of(Outcome outcome, String message) {
            return new TaskResult(outcome, message);
        }
    }

    public static final class ItemResult {
        public final String id;
        public final Outcome outcome;
        public final String message;
        public final boolean attempted;

        private ItemResult(String id, Outcome outcome, String message, boolean attempted) {
            this.id = id;
            this.outcome = outcome;
            this.message = message;
            this.attempted = attempted;
        }
    }

    public static final class Report {
        public final int total;
        public final int attempted;
        public final int success;
        public final int failed;
        public final int skipped;
        public final int unconfirmed;
        public final String stopReason;
        public final List<ItemResult> results;

        private Report(int total, int attempted, int success, int failed, int skipped,
                       int unconfirmed, String stopReason, List<ItemResult> results) {
            this.total = total;
            this.attempted = attempted;
            this.success = success;
            this.failed = failed;
            this.skipped = skipped;
            this.unconfirmed = unconfirmed;
            this.stopReason = stopReason;
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
        }
    }

    public static final class StopSignal {
        private final CountDownLatch stopped = new CountDownLatch(1);

        public void stop() {
            stopped.countDown();
        }

        public boolean isStopped() {
            return stopped.getCount() == 0;
        }

        /** Returns false as soon as stop is requested. */
        public boolean awaitNext(long delayMillis) {
            if (isStopped()) return false;
            try {
                return !stopped.await(Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
                return false;
            }
        }
    }

    private BatchTaskRunner() {}

    public static Report run(List<String> ids,
                             String expectedIdentity,
                             AccountIdentity accountIdentity,
                             StopSignal stopSignal,
                             long interTaskDelayMillis,
                             Task task,
                             ProgressListener progressListener) {
        List<String> targets = ids == null ? Collections.emptyList() : new ArrayList<>(ids);
        StopSignal stop = stopSignal == null ? new StopSignal() : stopSignal;
        List<ItemResult> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        int skipped = 0;
        int unconfirmed = 0;
        int attempted = 0;
        String stopReason = "";

        for (int index = 0; index < targets.size(); index++) {
            if (stop.isStopped()) {
                stopReason = "已停止后续操作";
                break;
            }
            String actualIdentity = accountIdentity == null ? "" : safeIdentity(accountIdentity);
            if (expectedIdentity == null || expectedIdentity.trim().isEmpty()
                    || !expectedIdentity.trim().equals(actualIdentity)) {
                stopReason = "账号已切换或账号状态不可用";
                break;
            }
            String id = targets.get(index);
            safeProgressStart(progressListener, index + 1, id);
            TaskResult result;
            try {
                result = task == null
                        ? TaskResult.of(Outcome.FAILED, "批量操作未就绪")
                        : task.execute(id, stop);
            } catch (Throwable e) {
                result = TaskResult.of(Outcome.FAILED,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (result == null) result = TaskResult.of(Outcome.FAILED, "操作没有返回结果");
            attempted++;
            results.add(new ItemResult(id, result.outcome, result.message, true));
            switch (result.outcome) {
                case SUCCESS: success++; break;
                case FAILED: failed++; break;
                case SKIPPED: skipped++; break;
                case UNCONFIRMED: unconfirmed++; break;
                default: failed++; break;
            }
            safeProgressResult(progressListener, index + 1, id, result);
            if (index + 1 < targets.size()) {
                if (stop.isStopped()) {
                    stopReason = "已停止后续操作";
                    break;
                }
                if (!stop.awaitNext(interTaskDelayMillis)) {
                    stopReason = "已停止后续操作";
                    break;
                }
            }
        }

        if (attempted < targets.size()) {
            if (stopReason.isEmpty() && stop.isStopped()) stopReason = "已停止后续操作";
            for (int index = attempted; index < targets.size(); index++) {
                results.add(new ItemResult(targets.get(index), Outcome.SKIPPED,
                        stopReason.isEmpty() ? "未执行" : stopReason, false));
                skipped++;
            }
        }
        return new Report(targets.size(), attempted, success, failed, skipped, unconfirmed,
                stopReason, results);
    }

    private static String safeIdentity(AccountIdentity accountIdentity) {
        try {
            String value = accountIdentity.current();
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void safeProgressStart(ProgressListener listener, int index, String id) {
        if (listener == null) return;
        try { listener.onStart(index, id); } catch (Throwable ignored) {}
    }

    private static void safeProgressResult(ProgressListener listener, int index, String id, TaskResult result) {
        if (listener == null) return;
        try { listener.onResult(index, id, result); } catch (Throwable ignored) {}
    }
}
