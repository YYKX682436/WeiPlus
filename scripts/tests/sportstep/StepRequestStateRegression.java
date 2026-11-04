import h.Hchat.hooks.items.sportstep.StepRequestState;
import static h.Hchat.hooks.items.sportstep.StepRequestState.Status.*;

public final class StepRequestStateRegression {
    static void check(boolean result) { if (!result) throw new AssertionError(); }
    static void expect(StepRequestState state, StepRequestState.Status expected) { check(state.status() == expected); }
    public static void main(String[] args) {
        StepRequestState s = new StepRequestState();
        Object first = new Object();
        check(!s.begin("com.tencent.mm", "", first));
        check(s.begin("com.tencent.mm", "A", first));
        check(!s.begin("com.tencent.mm", "A", new Object()));
        s.enqueueResult("com.tencent.mm", "A", first, true); expect(s, ENQUEUED);
        s.networkResult("com.tencent.mm", "A", first, 0, 0); expect(s, NETWORK_SUCCESS);
        s.networkResult("com.tencent.mm", "A", first, 1, 1); expect(s, NETWORK_SUCCESS);
        Object second = new Object();
        check(s.begin("com.tencent.mm", "A", second));
        s.networkResult("com.tencent.mm", "A", second, 0, 7); expect(s, SUBMITTING);
        s.enqueueResult("com.tencent.mm", "A", second, true); expect(s, NETWORK_FAILURE);
        Object third = new Object();
        check(s.begin("com.tencent.mm", "A", third));
        s.networkResult("com.tencent.mm", "A", third, 0, 0);
        s.enqueueResult("com.tencent.mm", "A", third, false); expect(s, ENQUEUE_FAILURE);
        Object fourth = new Object();
        check(s.begin("com.tencent.mm", "A", fourth));
        s.enqueueResult("com.tencent.mm", "A", fourth, true);
        s.networkResult("com.tencent.mm", "B", fourth, 0, 0); expect(s, ENQUEUED);
        s.networkResult("com.tencent.mm.clone", "A", fourth, 0, 0); expect(s, ENQUEUED);
        s.updateSession("com.tencent.mm", "B"); expect(s, IDLE);
        Object fifth = new Object();
        check(s.begin("com.tencent.mm", "B", fifth));
        s.networkResult("com.tencent.mm", "A", fourth, 0, 0); expect(s, SUBMITTING);
        s.enqueueResult("com.tencent.mm", "B", fifth, true);
        s.networkResult("com.tencent.mm", "B", fifth, 2, 0); expect(s, NETWORK_FAILURE);
        s.updateSession("com.tencent.mm.clone", "B"); expect(s, IDLE);
        check(s.begin("com.tencent.mm.clone", "B", new Object()));
        s.updateSession("com.tencent.mm.clone", ""); expect(s, IDLE);
        Object sixth = new Object();
        check(s.begin("com.tencent.mm", "A", sixth));
        s.enqueueResult("com.tencent.mm", "A", sixth, true);
        s.expire("com.tencent.mm", "A", first); expect(s, ENQUEUED);
        s.expire("com.tencent.mm", "A", sixth); expect(s, TIMEOUT);
        s.networkResult("com.tencent.mm", "A", sixth, 0, 0); expect(s, TIMEOUT);
        System.out.println("Step request state and isolation checks passed");
    }
}
