package h.Hchat.hooks.items.batch;

/** State comparison helpers; unknown reads never count as already applied or verified. */
public final class BatchStatePolicy {
    private BatchStatePolicy() {}

    public static boolean alreadySatisfied(Boolean current, boolean desired) {
        return current != null && current == desired;
    }

    public static boolean verified(Boolean current, boolean desired) {
        return current != null && current == desired;
    }
}
