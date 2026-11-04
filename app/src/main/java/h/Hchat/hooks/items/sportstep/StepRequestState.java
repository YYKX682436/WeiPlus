package h.Hchat.hooks.items.sportstep;

/** Correlates one request with its host and account, including synchronous network callbacks. */
public final class StepRequestState {
    public enum Status { IDLE, SUBMITTING, ENQUEUED, NETWORK_SUCCESS, NETWORK_FAILURE, ENQUEUE_FAILURE, TIMEOUT }

    private String host;
    private String account;
    private Object request;
    private Status status = Status.IDLE;
    private Boolean earlyNetworkResult;

    /** Missing account identity is not a shared anonymous account. */
    public synchronized boolean begin(String host, String account, Object request) {
        if (host == null || host.isEmpty() || account == null || account.isEmpty() || request == null) return false;
        updateSession(host, account);
        if (this.request != null) return false;
        this.host = host;
        this.account = account;
        this.request = request;
        this.earlyNetworkResult = null;
        this.status = Status.SUBMITTING;
        return true;
    }

    /** Call with the identity observed now, not a cached identity from the submission. */
    public synchronized void enqueueResult(String host, String account, Object request, boolean enqueued) {
        if (!matches(host, account, request) || status != Status.SUBMITTING) return;
        if (!enqueued) finish(Status.ENQUEUE_FAILURE);
        else if (earlyNetworkResult != null) finish(earlyNetworkResult ? Status.NETWORK_SUCCESS : Status.NETWORK_FAILURE);
        else status = Status.ENQUEUED;
    }

    /** Success describes the network callback only, not the displayed WeChat Sport result. */
    public synchronized void networkResult(String host, String account, Object request, int errorType, int errorCode) {
        if (!matches(host, account, request)) return;
        boolean success = errorType == 0 && errorCode == 0;
        if (status == Status.SUBMITTING) {
            if (earlyNetworkResult == null) earlyNetworkResult = success;
        } else if (status == Status.ENQUEUED) {
            finish(success ? Status.NETWORK_SUCCESS : Status.NETWORK_FAILURE);
        }
    }

    public synchronized void expire(String host, String account, Object request) {
        if (matches(host, account, request) && status == Status.ENQUEUED) finish(Status.TIMEOUT);
    }

    /** Invalidate pending work on logout, account switch, host change, or lifecycle destruction. */
    public synchronized void invalidate() {
        host = null;
        account = null;
        request = null;
        earlyNetworkResult = null;
        status = Status.IDLE;
    }

    public synchronized Status status() { return status; }

    /** Must be called when observing the current session, including before showing a prior result. */
    public synchronized void updateSession(String host, String account) {
        if (host == null || host.isEmpty() || account == null || account.isEmpty()
                || this.host != null && (!this.host.equals(host) || !this.account.equals(account))) {
            invalidate();
        }
    }

    private boolean matches(String host, String account, Object request) {
        return this.request != null && this.request == request && this.host.equals(host) && this.account.equals(account);
    }

    private void finish(Status next) {
        request = null;
        earlyNetworkResult = null;
        status = next;
    }
}
