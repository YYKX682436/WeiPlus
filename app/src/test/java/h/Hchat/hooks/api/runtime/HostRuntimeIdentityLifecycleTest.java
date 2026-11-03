package h.Hchat.hooks.api.runtime;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostRuntimeIdentityLifecycleTest {
    @Test public void metadataAndLoaderChangesAreSeparatedFromActiveCodeAndApkIdentity() {
        String host = "com.tencent.mm|8.0.80|3220|apk=18-dex|patch=stable";
        HostRuntimeIdentityDecision lifecycle = new HostRuntimeIdentityDecision();
        AtomicInteger cancelledTasksAndHookGenerations = new AtomicInteger();

        assertTrue(lifecycle.approve(host, "active-dex-A", "package-ready-loader"));
        assertEquals(HostRuntimeIdentityDecision.Result.SAME_GENERATION,
                lifecycle.verify(host, "active-dex-A", "package-ready-loader", cancelledTasksAndHookGenerations::incrementAndGet));
        assertEquals(HostRuntimeIdentityDecision.Result.CLASS_LOADER_GENERATION_CHANGED,
                lifecycle.verify(host, "active-dex-A", "tinker-attached-loader", cancelledTasksAndHookGenerations::incrementAndGet));
        assertEquals("tinker-attached-loader", lifecycle.approvedLoaderIdentity());
        assertFalse(lifecycle.isInvalidated());
        assertEquals("legal loader rotation must preserve installed generation", 0,
                cancelledTasksAndHookGenerations.get());

        assertEquals(HostRuntimeIdentityDecision.Result.RUNTIME_CODE_CHANGED,
                lifecycle.verify(host, "active-dex-B", "patched-loader",
                        cancelledTasksAndHookGenerations::incrementAndGet));
        assertFalse(lifecycle.isInvalidated());
        assertEquals("runtime code changes request targeted revalidation without revoking process identity", 0,
                cancelledTasksAndHookGenerations.get());

        assertEquals(HostRuntimeIdentityDecision.Result.HOST_IDENTITY_CHANGED,
                lifecycle.verify(host + "|apk=replaced", "active-dex-B", "patched-loader",
                        cancelledTasksAndHookGenerations::incrementAndGet));
        assertTrue(lifecycle.isInvalidated());
        assertEquals("actual APK identity change cancels old install generation once", 1,
                cancelledTasksAndHookGenerations.get());
        assertEquals(HostRuntimeIdentityDecision.Result.ALREADY_INVALIDATED,
                lifecycle.verify(host, "active-dex-B", "later-loader", cancelledTasksAndHookGenerations::incrementAndGet));
        assertEquals(1, cancelledTasksAndHookGenerations.get());
    }

    @Test public void incompleteIdentityWaitsForRetryWithoutRevokingApprovedHost() {
        HostRuntimeIdentityDecision lifecycle = new HostRuntimeIdentityDecision();
        AtomicInteger invalidations = new AtomicInteger();
        assertTrue(lifecycle.approve("approved-apk", "active-dex-A", "loader-A"));
        assertEquals(HostRuntimeIdentityDecision.Result.INCOMPLETE,
                lifecycle.verify("", "active-dex-A", "loader-B", invalidations::incrementAndGet));
        assertFalse(lifecycle.isInvalidated());
        assertEquals(0, invalidations.get());
        assertEquals(HostRuntimeIdentityDecision.Result.CLASS_LOADER_GENERATION_CHANGED,
                lifecycle.verify("approved-apk", "active-dex-A", "loader-B", invalidations::incrementAndGet));
    }
}
