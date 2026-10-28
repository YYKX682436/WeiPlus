import h.Hchat.hooks.api.runtime.HostRuntimeIdentityDecision;

import java.util.concurrent.atomic.AtomicInteger;

public final class HostRuntimeIdentityRegression {
    public static void main(String[] args) {
        String apkAndPatch = "com.tencent.mm|8.0.80|3220|dex=18|tinker=stable|patch=patch-01";
        HostRuntimeIdentityDecision guard = new HostRuntimeIdentityDecision();
        AtomicInteger invalidations = new AtomicInteger();
        if (!guard.approve(apkAndPatch, "package-ready-loader")) {
            throw new AssertionError("initial approval failed");
        }
        expect(HostRuntimeIdentityDecision.Result.SAME_GENERATION,
                guard.verify(apkAndPatch, "package-ready-loader", invalidations::incrementAndGet),
                "same generation");
        expect(HostRuntimeIdentityDecision.Result.CLASS_LOADER_GENERATION_CHANGED,
                guard.verify(apkAndPatch, "tinker-runtime-loader", invalidations::incrementAndGet),
                "legal Tinker ClassLoader replacement");
        if (guard.isInvalidated() || invalidations.get() != 0
                || !"tinker-runtime-loader".equals(guard.approvedLoaderIdentity())) {
            throw new AssertionError("legal Tinker attach invalidated the host generation");
        }
        expect(HostRuntimeIdentityDecision.Result.HOST_IDENTITY_CHANGED,
                guard.verify(apkAndPatch.replace("patch-01", "patch-02"), "tinker-runtime-loader",
                        invalidations::incrementAndGet), "actual patch identity change");
        if (!guard.isInvalidated() || invalidations.get() != 1) {
            throw new AssertionError("patch identity change did not invalidate exactly once");
        }
        expect(HostRuntimeIdentityDecision.Result.ALREADY_INVALIDATED,
                guard.verify(apkAndPatch, "tinker-runtime-loader", invalidations::incrementAndGet),
                "cancelled generation stays invalid");
        if (invalidations.get() != 1) {
            throw new AssertionError("old generation invalidated more than once");
        }
        System.out.println("Host runtime identity production lifecycle regression passed");
    }

    private static void expect(HostRuntimeIdentityDecision.Result expected,
                               HostRuntimeIdentityDecision.Result actual, String label) {
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + " got " + actual);
    }
}
