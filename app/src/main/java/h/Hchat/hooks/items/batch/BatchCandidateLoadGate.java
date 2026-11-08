package h.Hchat.hooks.items.batch;

import java.util.concurrent.atomic.AtomicLong;

/** Rejects stale asynchronous candidate-list callbacks after a scope/tag change or page disposal. */
public final class BatchCandidateLoadGate {
    private final AtomicLong generation = new AtomicLong();

    public long beginLoad() {
        return generation.incrementAndGet();
    }

    public boolean isCurrent(long requestGeneration) {
        return generation.get() == requestGeneration;
    }

    public void invalidate() {
        generation.incrementAndGet();
    }
}
