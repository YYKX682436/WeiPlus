package h.Hchat.hooks.items.sportstep;

/** Pure decisions for one day's unmodified step count; callers own account and date storage. */
public final class StepMultiplierPolicy {
    public static final int DEFAULT_MULTIPLIER = 5;

    public enum Status { DISABLED, MANUAL_PRIORITY, NO_REAL_STEPS, INVALID_MULTIPLIER, OVERFLOW, READY }

    public static final class Decision {
        public final Status status;
        public final int step;

        private Decision(Status status, int step) {
            this.status = status;
            this.step = step;
        }
    }

    private StepMultiplierPolicy() {}

    /** Strict decimal positive integer, without signs, whitespace, truncation, or overflow. */
    public static int parsePositiveInt(String text) {
        if (text == null || text.isEmpty()) return 0;
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return 0;
            int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) return 0;
            value = value * 10 + digit;
        }
        return value;
    }

    /** The day keys must be derived using the same local-date/time-zone policy by the caller. */
    public static Decision decide(boolean enabled, String today, String manualDay,
                                  long realSteps, int multiplier) {
        if (!enabled) return new Decision(Status.DISABLED, 0);
        if (today == null || today.isEmpty()) return new Decision(Status.NO_REAL_STEPS, 0);
        if (today.equals(manualDay)) return new Decision(Status.MANUAL_PRIORITY, 0);
        if (multiplier <= 0) return new Decision(Status.INVALID_MULTIPLIER, 0);
        if (realSteps <= 0) return new Decision(Status.NO_REAL_STEPS, 0);
        if (realSteps > Integer.MAX_VALUE / (long) multiplier) return new Decision(Status.OVERFLOW, 0);
        return new Decision(Status.READY, (int) (realSteps * multiplier));
    }
}
