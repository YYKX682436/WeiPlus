package h.Hchat.hooks.api.runtime;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostRuntimeIdentityLifecycleTest {
    @Test public void legalTinkerLoaderRotationSurvivesAndPatchChangeCancelsGeneration() {
        String host = "com.tencent.mm|8.0.80|3220|apk=18-dex|patch=stable";
        HostRuntimeIdentityDecision lifecycle = new HostRuntimeIdentityDecision();
        AtomicInteger cancelledTasksAndHookGenerations = new AtomicInteger();

        assertTrue(lifecycle.approve(host, "package-ready-loader"));
        assertEquals(HostRuntimeIdentityDecision.Result.SAME_GENERATION,
                lifecycle.verify(host, "package-ready-loader", cancelledTasksAndHookGenerations::incrementAndGet));
        assertEquals(HostRuntimeIdentityDecision.Result.CLASS_LOADER_GENERATION_CHANGED,
                lifecycle.verify(host, "tinker-attached-loader", cancelledTasksAndHookGenerations::incrementAndGet));
        assertEquals("tinker-attached-loader", lifecycle.approvedLoaderIdentity());
        assertFalse(lifecycle.isInvalidated());
        assertEquals("legal loader rotation must preserve installed generation", 0,
                cancelledTasksAndHookGenerations.get());

        assertEquals(HostRuntimeIdentityDecision.Result.HOST_IDENTITY_CHANGED,
                lifecycle.verify(host + "|patch=changed", "tinker-attached-loader",
                        cancelledTasksAndHookGenerations::incrementAndGet));
        assertTrue(lifecycle.isInvalidated());
        assertEquals("identity loss cancels old install generation once", 1,
                cancelledTasksAndHookGenerations.get());
        assertEquals(HostRuntimeIdentityDecision.Result.ALREADY_INVALIDATED,
                lifecycle.verify(host, "later-loader", cancelledTasksAndHookGenerations::incrementAndGet));
        assertEquals(1, cancelledTasksAndHookGenerations.get());
    }

    @Test public void incompleteIdentityFailsClosedAndInvalidatesOldGeneration() {
        HostRuntimeIdentityDecision lifecycle = new HostRuntimeIdentityDecision();
        AtomicInteger invalidations = new AtomicInteger();
        assertTrue(lifecycle.approve("approved-apk-and-patch", "loader-A"));
        assertEquals(HostRuntimeIdentityDecision.Result.INCOMPLETE,
                lifecycle.verify("", "loader-B", invalidations::incrementAndGet));
        assertTrue(lifecycle.isInvalidated());
        assertEquals(1, invalidations.get());
    }
}
