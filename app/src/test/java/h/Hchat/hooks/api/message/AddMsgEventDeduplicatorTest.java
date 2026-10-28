package h.Hchat.hooks.api.message;

import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class AddMsgEventDeduplicatorTest {
    @Test public void deduplicatesByServerIdAndAllowsExpiry() {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        assertTrue(deduplicator.markIfNew("room", "sender", 10L, 42L, "body", 1_000L));
        assertFalse(deduplicator.markIfNew("room", "sender", 10L, 42L, "body", 2_000L));
        assertTrue(deduplicator.markIfNew("room", "sender", 10L, 42L, "body", 123_001L));
        assertTrue(deduplicator.markIfNew("room", "sender", 11L, 0L, "body", 124_000L));
    }

    @Test public void missingServerIdDoesNotDeduplicateDifferentConversations() {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        assertTrue(deduplicator.markIfNew("room-a", "sender", 10L, 0L, "body", 1_000L));
        assertTrue(deduplicator.markIfNew("room-b", "sender", 10L, 0L, "body", 1_001L));
    }

    @Test public void sameTextWithoutReliableIdNeverDropsDistinctMessages() throws Exception {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        assertTrue(deduplicator.markIfNew("room", "sender", 10L, 0L, "same", 1_000L));
        assertTrue(deduplicator.markIfNew("room", "sender", 10L, 0L, "same", 1_001L));
        assertEquals(0, entries(deduplicator).size());
    }

    @Test public void capacityIsBoundedAndStoredKeysNeverRetainMessageBodies() throws Exception {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        String largeBody = "private-payload-" + new String(new char[20_000]).replace('\0', 'x');
        for (int i = 0; i < 600; i++) {
            assertTrue(deduplicator.markIfNew("room", "sender", i, i + 1L,
                    i == 599 ? largeBody : "body", 1_000L + i));
        }
        Map<?, ?> entries = entries(deduplicator);
        assertEquals("dedup cache must honor its hard limit", 512, entries.size());
        for (Object key : entries.keySet()) assertFalse(String.valueOf(key).contains(largeBody));
        assertFalse(deduplicator.markIfNew("room", "sender", 599L, 600L,
                "another body", 2_000L));
    }

    @Test public void concurrentRetryWithServerIdDispatchesOnlyOnce() throws Exception {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(24);
        AtomicInteger fresh = new AtomicInteger();
        for (int i = 0; i < 24; i++) new Thread(() -> {
            try {
                start.await();
                if (deduplicator.markIfNew("room", "sender", 10L, 99L, "body", 1_000L)) fresh.incrementAndGet();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            } finally { done.countDown(); }
        }).start();
        start.countDown();
        done.await();
        assertEquals(1, fresh.get());
    }

    private static Map<?, ?> entries(AddMsgEventDeduplicator deduplicator) throws Exception {
        Field field = AddMsgEventDeduplicator.class.getDeclaredField("events");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(deduplicator);
    }
}
