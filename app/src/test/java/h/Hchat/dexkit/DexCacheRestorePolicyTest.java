package h.Hchat.dexkit;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class DexCacheRestorePolicyTest {
    @Test public void trustedHotCacheRestoresWithoutRunningAnyDexQuery() {
        AtomicInteger queries = new AtomicInteger();
        String cached = "Lhost/Owner;->receive(Ljava/lang/String;)V";
        String restored = DexCacheRestorePolicy.restore(true, true, cached,
                value -> value.contains("Owner"), () -> {
                    queries.incrementAndGet();
                    return "rescanned";
                });
        assertSame(cached, restored);
        assertEquals(0, queries.get());
    }

    @Test public void hotAddMsgTextAndSnsDescriptorsIssueZeroDexKitQueries() {
        AtomicInteger dexKitQueries = new AtomicInteger();
        Boolean addMsg = DexCacheRestorePolicy.restore(true, true, Boolean.TRUE,
                Boolean.TRUE::equals, () -> { dexKitQueries.incrementAndGet(); return Boolean.FALSE; });
        Boolean sendText = DexCacheRestorePolicy.restore(true, true, Boolean.TRUE,
                Boolean.TRUE::equals, () -> { dexKitQueries.incrementAndGet(); return Boolean.FALSE; });
        String snsVideo = DexCacheRestorePolicy.restore(true, true, "Lhost/Upload;->video()Z",
                value -> value.contains("Upload"), () -> {
                    dexKitQueries.incrementAndGet();
                    return "rescanned";
                });
        assertEquals(Boolean.TRUE, addMsg);
        assertEquals(Boolean.TRUE, sendText);
        assertEquals("Lhost/Upload;->video()Z", snsVideo);
        assertEquals("trusted warm-cache restoration must not enter DexKit query callbacks", 0,
                dexKitQueries.get());
    }

    @Test public void identityChangeOrDamagedDescriptorRunsOnlyTheTargetedQuery() {
        AtomicInteger queries = new AtomicInteger();
        String identityChanged = DexCacheRestorePolicy.restore(false, true, "stale",
                value -> true, () -> { queries.incrementAndGet(); return "targeted"; });
        assertEquals("targeted", identityChanged);
        String descriptorDamaged = DexCacheRestorePolicy.restore(true, true, "bad",
                value -> false, () -> { queries.incrementAndGet(); return "targeted"; });
        assertEquals("targeted", descriptorDamaged);
        assertEquals(2, queries.get());
    }

    @Test public void missingLocatorRunsItsOwnRecoveryQuery() {
        AtomicInteger queries = new AtomicInteger();
        String result = DexCacheRestorePolicy.restore(true, false, null,
                value -> true, () -> { queries.incrementAndGet(); return "targeted"; });
        assertEquals("targeted", result);
        assertEquals(1, queries.get());
    }

    @Test public void failedTargetedQueryDoesNotEscapeAndTriggerGlobalCacheReset() {
        AtomicInteger queries = new AtomicInteger();
        String result = DexCacheRestorePolicy.restore(true, false, null,
                value -> true, () -> {
                    queries.incrementAndGet();
                    throw new IllegalStateException("single locator unavailable");
                });
        assertEquals(null, result);
        assertEquals(1, queries.get());
    }
}
