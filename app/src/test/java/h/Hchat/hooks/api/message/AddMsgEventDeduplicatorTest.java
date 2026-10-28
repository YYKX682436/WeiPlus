package h.Hchat.hooks.api.message;

import org.junit.Test;
import static org.junit.Assert.*;

public class AddMsgEventDeduplicatorTest {
    @Test public void deduplicatesByServerIdAndAllowsExpiry() {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        assertTrue(deduplicator.markIfNew("room", "sender", 10L, 42L, "body", 1_000L));
        assertFalse(deduplicator.markIfNew("room", "sender", 10L, 42L, "body", 2_000L));
        assertTrue(deduplicator.markIfNew("room", "sender", 10L, 42L, "body", 123_001L));
        assertTrue(deduplicator.markIfNew("room", "sender", 11L, 0L, "body", 124_000L));
    }

    @Test public void fallbackKeySeparatesTalkerSenderTimeAndContent() {
        AddMsgEventDeduplicator deduplicator = new AddMsgEventDeduplicator();
        assertTrue(deduplicator.markIfNew("room-a", "sender", 10L, 0L, "body", 1_000L));
        assertTrue(deduplicator.markIfNew("room-b", "sender", 10L, 0L, "body", 1_001L));
    }
}
