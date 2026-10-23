package h.Hchat.hooks.api.contact;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Tracks queue submission separately from the asynchronous server callback. */
final class ContactRequestDispatch {
    static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;

    enum Outcome {
        DISPATCH_FAILED,
        NOT_SUBMITTED,
        SERVER_REJECTED,
        SERVER_ACCEPTED,
        TIMED_OUT,
        INTERRUPTED
    }

    interface Callback {
        void onResponse(int errorType, int errorCode);
    }

    interface Submitter {
        /** Returns the submitted scene/request, or null when the queue rejected it. */
        Object submit(Callback callback) throws Throwable;
    }

    static final class Result {
        final Outcome outcome;
        final int errorType;
        final int errorCode;

        private Result(Outcome outcome, int errorType, int errorCode) {
            this.outcome = outcome;
            this.errorType = errorType;
            this.errorCode = errorCode;
        }

        boolean wasSubmitted() {
            return outcome == Outcome.SERVER_REJECTED
                    || outcome == Outcome.SERVER_ACCEPTED
                    || outcome == Outcome.TIMED_OUT
                    || outcome == Outcome.INTERRUPTED;
        }

        boolean serverAccepted() {
            return outcome == Outcome.SERVER_ACCEPTED;
        }
    }

    private ContactRequestDispatch() {}

    static Result submitAndAwait(Submitter submitter) {
        return submitAndAwait(submitter, DEFAULT_TIMEOUT_MILLIS);
    }

    static Result submitAndAwait(Submitter submitter, long timeoutMillis) {
        if (submitter == null) return result(Outcome.DISPATCH_FAILED, -1, -1);
        Completion completion = new Completion();
        final Object submitted;
        try {
            submitted = submitter.submit(completion);
        } catch (Throwable ignored) {
            return result(Outcome.DISPATCH_FAILED, -1, -1);
        }
        if (submitted == null) return result(Outcome.NOT_SUBMITTED, -1, -1);

        try {
            if (!completion.completed.await(Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS)) {
                return result(Outcome.TIMED_OUT, -1, -1);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return result(Outcome.INTERRUPTED, -1, -1);
        }
        return completion.result.get();
    }

    static Result notSubmitted() {
        return result(Outcome.NOT_SUBMITTED, -1, -1);
    }

    static Result dispatchFailed() {
        return result(Outcome.DISPATCH_FAILED, -1, -1);
    }

    private static Result result(Outcome outcome, int errorType, int errorCode) {
        return new Result(outcome, errorType, errorCode);
    }

    private static final class Completion implements Callback {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<Result> result = new AtomicReference<>();

        @Override
        public void onResponse(int errorType, int errorCode) {
            Outcome outcome = errorType == 0 && errorCode == 0
                    ? Outcome.SERVER_ACCEPTED
                    : Outcome.SERVER_REJECTED;
            if (result.compareAndSet(null, ContactRequestDispatch.result(outcome, errorType, errorCode))) {
                completed.countDown();
            }
        }
    }
}
