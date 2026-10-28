package h.Hchat.hooks.api.message;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/** Deduplicates retries only when WeChat provides a reliable server message identity. */
public final class AddMsgEventDeduplicator {
    private static final long RETENTION_MS = 120_000L;
    private static final int MAX_ENTRIES = 512;
    private final ConcurrentHashMap<String, Entry> events = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Entry> insertionOrder = new ConcurrentLinkedDeque<>();
    private final AtomicInteger reservedEntries = new AtomicInteger();

    public boolean markIfNew(String talker, String sender, long createTimeSeconds,
                             long msgSvrId, String content, long nowMs) {
        // Time/content/sender are not unique. Never drop a legitimate message to hide a retry
        // when the host did not provide a trustworthy msgSvrId.
        if (msgSvrId <= 0L) return true;
        String key = safe(talker) + '\u0000' + msgSvrId;
        while (true) {
            Entry previous = events.get(key);
            if (previous != null) {
                if (nowMs - previous.createdAtMs < RETENTION_MS) return false;
                remove(previous);
                continue;
            }

            if (!reserveSlot()) {
                if (!evictOldest()) return true; // Contended: fail open without growing storage.
                continue;
            }
            Entry entry = new Entry(key, nowMs);
            Entry raced = events.putIfAbsent(key, entry);
            if (raced == null) {
                insertionOrder.addLast(entry);
                if (events.get(key) != entry) insertionOrder.remove(entry);
                return true;
            }
            reservedEntries.decrementAndGet();
        }
    }

    int cachedEntryCount() {
        return events.size();
    }

    private boolean reserveSlot() {
        while (true) {
            int current = reservedEntries.get();
            if (current >= MAX_ENTRIES) return false;
            if (reservedEntries.compareAndSet(current, current + 1)) return true;
        }
    }

    private boolean evictOldest() {
        Entry entry;
        while ((entry = insertionOrder.pollFirst()) != null) {
            if (events.remove(entry.key, entry)) {
                reservedEntries.decrementAndGet();
                return true;
            }
        }
        return false;
    }

    private void remove(Entry entry) {
        if (events.remove(entry.key, entry)) {
            insertionOrder.remove(entry);
            reservedEntries.decrementAndGet();
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static final class Entry {
        final String key;
        final long createdAtMs;

        Entry(String key, long createdAtMs) {
            this.key = key;
            this.createdAtMs = createdAtMs;
        }
    }
}
