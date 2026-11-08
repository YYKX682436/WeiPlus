package h.Hchat.hooks.api.contact;

import java.util.concurrent.TimeUnit;

public final class ContactDispatchRegression {
    public static void main(String[] args) throws Exception {
        nullDispatchIsNotSubmitted();
        thrownDispatchIsFailure();
        asynchronousFailureIsRejected();
        asynchronousSuccessIsAccepted();
        timeoutRemainsSubmittedButUnconfirmed();
        nativeSceneArgumentOrderSeparatesNetIdFromErrorCodes();
        duplicateNativeCallbacksKeepTheFirstResponse();
        permissionResultRequiresServerAcceptanceAndMatchingState();
        ambiguousDispatchIsNotRetried();
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

    private static void nativeSceneArgumentOrderSeparatesNetIdFromErrorCodes() {
        NativeSceneResponse success = NativeSceneResponse.fromArguments(
                new Object[]{9017, 0, 0, "", new Object(), new byte[0]});
        check(success != null && success.errorType == 0 && success.errorCode == 0 && success.isAccepted(),
                "nonzero network ID with zero errType/errCode must be accepted");
        NativeSceneResponse typeFailure = NativeSceneResponse.fromArguments(
                new Object[]{9017, 4, 0, "rejected", new Object(), new byte[0]});
        check(typeFailure != null && typeFailure.errorType == 4 && !typeFailure.isAccepted(),
                "the second integer is errType");
        NativeSceneResponse codeFailure = NativeSceneResponse.fromArguments(
                new Object[]{9017, 0, -100, "rejected", new Object(), new byte[0]});
        check(codeFailure != null && codeFailure.errorCode == -100 && !codeFailure.isAccepted(),
                "the third integer is errCode");
        check(NativeSceneResponse.fromArguments(new Object[]{9017, 0}) == null,
                "malformed native callback argument lists must be ignored");
    }

    private static void duplicateNativeCallbacksKeepTheFirstResponse() {
        ContactRequestDispatch.Result result = ContactRequestDispatch.submitAndAwait(callback -> {
            NativeSceneResponse accepted = NativeSceneResponse.fromArguments(
                    new Object[]{9017, 0, 0, "", new Object(), new byte[0]});
            NativeSceneResponse duplicateRejected = NativeSceneResponse.fromArguments(
                    new Object[]{9018, 2, -1, "duplicate", new Object(), new byte[0]});
            callback.onResponse(accepted.errorType, accepted.errorCode);
            callback.onResponse(duplicateRejected.errorType, duplicateRejected.errorCode);
            return new Object();
        }, 100);
        check(result.outcome == ContactRequestDispatch.Outcome.SERVER_ACCEPTED,
                "a repeated callback must not replace the first response");
    }

    private static void permissionResultRequiresServerAcceptanceAndMatchingState() {
        ContactRequestDispatch.Result rejected = ContactRequestDispatch.submitAndAwait(callback -> {
            callback.onResponse(1, 0);
            return new Object();
        }, 100);
        check(WeChatPermissionResultPolicy.classify(rejected, true, true)
                        == WeChatNativeMutationResult.Outcome.SERVER_REJECTED,
                "explicit server rejection must win over a matching local state read");

        ContactRequestDispatch.Result accepted = ContactRequestDispatch.submitAndAwait(callback -> {
            callback.onResponse(0, 0);
            return new Object();
        }, 100);
        check(WeChatPermissionResultPolicy.classify(accepted, true, true)
                        == WeChatNativeMutationResult.Outcome.CONFIRMED,
                "accepted response and matching reread are both required for confirmation");
        check(WeChatPermissionResultPolicy.classify(accepted, true, false)
                        == WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED,
                "accepted response without target state remains unconfirmed");

        ContactRequestDispatch.Result timeout = ContactRequestDispatch.submitAndAwait(callback -> new Object(), 5);
        check(WeChatPermissionResultPolicy.classify(timeout, true, true)
                        == WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                "a timeout must remain unconfirmed even if local state later matches");
        check(WeChatPermissionResultPolicy.classify(accepted, false, false)
                        == WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                "a failed final state read after submission must remain unconfirmed");
    }

    private static void ambiguousDispatchIsNotRetried() {
        check(ContactRequestDispatch.safeToFallbackToPerItem(null),
                "a bulk path unavailable before submission may use the existing single-item path");
        check(ContactRequestDispatch.safeToFallbackToPerItem(ContactRequestDispatch.notSubmitted()),
                "a request explicitly not submitted may use the existing single-item path");
        check(!ContactRequestDispatch.safeToFallbackToPerItem(ContactRequestDispatch.dispatchFailed()),
                "an ambiguous dispatch failure must not risk duplicate label requests");
        check(!ContactRequestDispatch.safeToFallbackToPerItem(
                        ContactRequestDispatch.submitAndAwait(callback -> new Object(), 1)),
                "a submitted timeout must not be retried per item");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
