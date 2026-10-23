package h.Hchat.hooks.api.contact;

import java.util.concurrent.TimeUnit;

public final class ContactDispatchRegression {
    public static void main(String[] args) throws Exception {
        nullDispatchIsNotSubmitted();
        thrownDispatchIsFailure();
        asynchronousFailureIsRejected();
        asynchronousSuccessIsAccepted();
        timeoutRemainsSubmittedButUnconfirmed();
        System.out.println("Contact dispatch regression tests passed");
    }

    private static void nullDispatchIsNotSubmitted() {
        ContactRequestDispatch.Result result = ContactRequestDispatch.submitAndAwait(callback -> null, 10);
        check(result.outcome == ContactRequestDispatch.Outcome.NOT_SUBMITTED, "null dispatch outcome");
        check(!result.wasSubmitted() && !result.serverAccepted(), "null dispatch status");
    }

    private static void thrownDispatchIsFailure() {
        ContactRequestDispatch.Result result = ContactRequestDispatch.submitAndAwait(callback -> {
            throw new IllegalStateException("dispatch failed");
        }, 10);
        check(result.outcome == ContactRequestDispatch.Outcome.DISPATCH_FAILED, "thrown dispatch outcome");
        check(!result.wasSubmitted() && !result.serverAccepted(), "thrown dispatch status");
    }

    private static void asynchronousFailureIsRejected() throws Exception {
        ContactRequestDispatch.Result result = ContactRequestDispatch.submitAndAwait(callback -> {
            Thread response = new Thread(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                callback.onResponse(0, 1001);
            });
            response.start();
            return new Object();
        }, 500);
        check(result.outcome == ContactRequestDispatch.Outcome.SERVER_REJECTED, "async failure outcome");
        check(result.errorCode == 1001 && result.wasSubmitted() && !result.serverAccepted(),
                "async failure status");
    }

    private static void asynchronousSuccessIsAccepted() {
        ContactRequestDispatch.Result result = ContactRequestDispatch.submitAndAwait(callback -> {
            Thread response = new Thread(() -> callback.onResponse(0, 0));
            response.start();
            return new Object();
        }, 500);
        check(result.outcome == ContactRequestDispatch.Outcome.SERVER_ACCEPTED, "async success outcome");
        check(result.wasSubmitted() && result.serverAccepted(), "async success status");
    }

    private static void timeoutRemainsSubmittedButUnconfirmed() {
        ContactRequestDispatch.Result result = ContactRequestDispatch.submitAndAwait(callback -> new Object(), 5);
        check(result.outcome == ContactRequestDispatch.Outcome.TIMED_OUT, "timeout outcome");
        check(result.wasSubmitted() && !result.serverAccepted(), "timeout status");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
