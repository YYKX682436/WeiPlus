package h.Hchat.hooks.api.runtime;

/** Tracks the approved host identity across package-ready and Tinker loader generations. */
public final class HostRuntimeIdentityDecision {
    public enum Result {
        SAME_GENERATION,
        CLASS_LOADER_GENERATION_CHANGED,
        HOST_IDENTITY_CHANGED,
        INCOMPLETE,
        ALREADY_INVALIDATED
    }

    private String approvedHostIdentity = "";
    private String approvedLoaderIdentity = "";
    private boolean invalidated;

    public synchronized boolean approve(String hostIdentity, String loaderIdentity) {
        if (empty(hostIdentity) || empty(loaderIdentity)) return false;
        approvedHostIdentity = hostIdentity;
        approvedLoaderIdentity = loaderIdentity;
        invalidated = false;
        return true;
    }

    /**
     * Accepts a loader-generation rotation only while immutable package and patch identity stays
     * the same. An identity failure invokes the supplied production invalidation path once.
     */
    public synchronized Result verify(String currentHostIdentity, String currentLoaderIdentity,
                                      Runnable invalidateHost) {
        if (invalidated) return Result.ALREADY_INVALIDATED;
        if (empty(approvedHostIdentity) || empty(approvedLoaderIdentity)
                || empty(currentHostIdentity) || empty(currentLoaderIdentity)) {
            invalidate(invalidateHost);
            return Result.INCOMPLETE;
        }
        if (!approvedHostIdentity.equals(currentHostIdentity)) {
            invalidate(invalidateHost);
            return Result.HOST_IDENTITY_CHANGED;
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
    public synchronized boolean isInvalidated() { return invalidated; }

    private void invalidate(Runnable action) {
        if (invalidated) return;
        invalidated = true;
        if (action != null) {
            try { action.run(); } catch (Throwable ignored) {}
        }
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
