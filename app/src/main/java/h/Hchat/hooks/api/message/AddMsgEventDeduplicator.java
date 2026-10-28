package h.Hchat.hooks.api.message;

import java.util.concurrent.ConcurrentHashMap;

/** Deduplicates a single inbound message when a transport retries its processing call. */
public final class AddMsgEventDeduplicator {
    private static final long RETENTION_MS = 120_000L;
    private static final int MAX_ENTRIES = 512;
    private final ConcurrentHashMap<String, Long> events = new ConcurrentHashMap<>();

    public boolean markIfNew(String talker, String sender, long createTimeSeconds,
                             long msgSvrId, String content, long nowMs) {
        String key = msgSvrId > 0L
                ? safe(talker) + ":" + msgSvrId
                : safe(talker) + ":" + safe(sender) + ":" + createTimeSeconds + ":" + safe(content);
        Long previous = events.put(key, nowMs);
        if (previous != null && nowMs - previous < RETENTION_MS) return false;
        if (events.size() > MAX_ENTRIES) {
            events.entrySet().removeIf(entry -> nowMs - entry.getValue() > RETENTION_MS);
        }
        return true;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
