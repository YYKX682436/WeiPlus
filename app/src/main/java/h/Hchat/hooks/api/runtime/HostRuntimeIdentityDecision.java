package h.Hchat.hooks.api.runtime;

/** Tracks immutable APK identity, active runtime code and loader generations independently. */
public final class HostRuntimeIdentityDecision {
    public enum Result {
        SAME_GENERATION,
        CLASS_LOADER_GENERATION_CHANGED,
        RUNTIME_CODE_CHANGED,
        HOST_IDENTITY_CHANGED,
        INCOMPLETE,
        ALREADY_INVALIDATED
    }

    private String approvedHostIdentity = "";
    private String approvedRuntimeCodeIdentity = "";
    private String approvedLoaderIdentity = "";
    private boolean invalidated;

    public synchronized boolean approve(String hostIdentity, String loaderIdentity) {
        return approve(hostIdentity, "none", loaderIdentity);
    }

    public synchronized boolean approve(String hostIdentity, String runtimeCodeIdentity,
                                        String loaderIdentity) {
        if (empty(hostIdentity) || empty(runtimeCodeIdentity) || empty(loaderIdentity)) return false;
        approvedHostIdentity = hostIdentity;
        approvedRuntimeCodeIdentity = runtimeCodeIdentity;
        approvedLoaderIdentity = loaderIdentity;
        invalidated = false;
        return true;
    }

    /** Compatibility overload for callers that do not have a separate runtime-code token. */
    public synchronized Result verify(String currentHostIdentity, String currentLoaderIdentity,
                                      Runnable invalidateHost) {
        return verify(currentHostIdentity, approvedRuntimeCodeIdentity, currentLoaderIdentity,
                invalidateHost);
    }

    /**
     * Keeps immutable APK identity as the process safety boundary while tracking active Tinker
     * DEX code separately. Patch metadata updates never revoke the process; changed loaded DEX
     * code requests targeted locator/Hook revalidation.
     */
    public synchronized Result verify(String currentHostIdentity, String currentRuntimeCodeIdentity,
                                      String currentLoaderIdentity, Runnable invalidateHost) {
        if (invalidated) return Result.ALREADY_INVALIDATED;
        if (empty(approvedHostIdentity) || empty(approvedLoaderIdentity)
                || empty(approvedRuntimeCodeIdentity) || empty(currentHostIdentity)
                || empty(currentRuntimeCodeIdentity) || empty(currentLoaderIdentity)) {
            return Result.INCOMPLETE;
        }
        if (!approvedHostIdentity.equals(currentHostIdentity)) {
            invalidate(invalidateHost);
            return Result.HOST_IDENTITY_CHANGED;
        }
        if (!approvedRuntimeCodeIdentity.equals(currentRuntimeCodeIdentity)) {
            approvedRuntimeCodeIdentity = currentRuntimeCodeIdentity;
            approvedLoaderIdentity = currentLoaderIdentity;
            return Result.RUNTIME_CODE_CHANGED;
        }
        if (!approvedLoaderIdentity.equals(currentLoaderIdentity)) {
            approvedLoaderIdentity = currentLoaderIdentity;
            return Result.CLASS_LOADER_GENERATION_CHANGED;
        }
        return Result.SAME_GENERATION;
    }

    public synchronized boolean invalidate() {
        if (invalidated) return false;
        invalidated = true;
        return true;
    }

    public synchronized String approvedLoaderIdentity() { return approvedLoaderIdentity; }
    public synchronized String approvedRuntimeCodeIdentity() { return approvedRuntimeCodeIdentity; }
    public synchronized boolean isInvalidated() { return invalidated; }
    public synchronized boolean isApproved() {
        return !invalidated && !empty(approvedHostIdentity)
                && !empty(approvedRuntimeCodeIdentity) && !empty(approvedLoaderIdentity);
    }

    private void invalidate(Runnable action) {
        if (invalidated) return;
        invalidated = true;
        if (action != null) {
            try { action.run(); } catch (Throwable ignored) {}
        }
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
