package h.Hchat.hooks.items.batch;

/** Owns one cancellable batch session at a time and ignores stale disposal callbacks. */
public final class BatchTaskLifecycle {
    public static final class Session {
        private final long generation;
        private final BatchTaskRunner.StopSignal stopSignal = new BatchTaskRunner.StopSignal();
        private boolean active = true;

        private Session(long generation) {
            this.generation = generation;
        }

        public long generation() { return generation; }
        public BatchTaskRunner.StopSignal stopSignal() { return stopSignal; }
        public synchronized boolean isActive() { return active; }

        private synchronized void finish() {
            active = false;
        }
    }

    private long generation;
    private Session current;

    /** Starts a new task. A prior active task is stopped before it is replaced. */
    public synchronized Session begin() {
        if (current != null && current.isActive()) {
            current.stopSignal().stop();
            current.finish();
        }
        current = new Session(++generation);
        return current;
    }

    /** Stops only this still-current session; delayed disposal of an older page is harmless. */
    public synchronized boolean stop(Session session) {
        if (session == null || current != session || !session.isActive()) return false;
        session.stopSignal().stop();
        return true;
    }

    /** Marks only this session complete; subsequent starts receive a fresh signal. */
    public synchronized boolean finish(Session session) {
        if (session == null || current != session || !session.isActive()) return false;
        session.finish();
        return true;
    }

    public synchronized boolean isCurrent(Session session) {
        return session != null && current == session && session.isActive();
    }
}
