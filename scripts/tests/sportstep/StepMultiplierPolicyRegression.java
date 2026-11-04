import h.Hchat.hooks.items.sportstep.StepMultiplierPolicy;
import static h.Hchat.hooks.items.sportstep.StepMultiplierPolicy.Status.*;

public final class StepMultiplierPolicyRegression {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
    private static void decision(StepMultiplierPolicy.Status status, int step, boolean enabled,
                                 String today, String manualDay, long realSteps, int multiplier) {
        StepMultiplierPolicy.Decision result = StepMultiplierPolicy.decide(
                enabled, today, manualDay, realSteps, multiplier);
        check(result.status == status && result.step == step);
    }
    public static void main(String[] args) {
        check(StepMultiplierPolicy.DEFAULT_MULTIPLIER == 5);
        check(StepMultiplierPolicy.parsePositiveInt("1") == 1);
        check(StepMultiplierPolicy.parsePositiveInt("2147483647") == Integer.MAX_VALUE);
        for (String invalid : new String[]{"", "0", "-1", "+1", " 1", "1 ", "1.0", "１", "2147483648", "999999999999999999999"}) {
            check(StepMultiplierPolicy.parsePositiveInt(invalid) == 0);
        }
        decision(DISABLED, 0, false, "2026-11-04", null, 100, 5);
        decision(READY, 500, true, "2026-11-04", null, 100, 5);
        // The same unmodified event always yields the same result; no multiplication of prior output.
        decision(READY, 500, true, "2026-11-04", null, 100, 5);
        decision(READY, 505, true, "2026-11-04", null, 101, 5);
        decision(MANUAL_PRIORITY, 0, true, "2026-11-04", "2026-11-04", 101, 5);
        decision(READY, 10, true, "2026-11-05", "2026-11-04", 2, 5);
        decision(NO_REAL_STEPS, 0, true, "", null, 100, 5);
        decision(NO_REAL_STEPS, 0, true, "2026-11-04", null, 0, 5);
        decision(INVALID_MULTIPLIER, 0, true, "2026-11-04", null, 100, 0);
        decision(READY, Integer.MAX_VALUE, true, "2026-11-04", null, Integer.MAX_VALUE, 1);
        decision(OVERFLOW, 0, true, "2026-11-04", null, 429496730L, 5);
        decision(OVERFLOW, 0, true, "2026-11-04", null, Long.MAX_VALUE, Integer.MAX_VALUE);
        System.out.println("Step multiplier policy checks passed");
    }
}
