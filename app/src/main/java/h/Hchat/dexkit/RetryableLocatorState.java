package h.Hchat.dexkit;

/** Small retryable state machine for a lazily resolved API. */
final class RetryableLocatorState {
    private enum State { UNRESOLVED, RESOLVING, RESOLVED }

    private State state = State.UNRESOLVED;

    synchronized boolean beginAttempt() {
        if (state != State.UNRESOLVED) return false;
        state = State.RESOLVING;
        return true;
    }

    synchronized void finishAttempt(boolean complete) {
        if (state == State.RESOLVING) {
            state = complete ? State.RESOLVED : State.UNRESOLVED;
        }
    }

    synchronized boolean isResolved() {
        return state == State.RESOLVED;
    }
}
