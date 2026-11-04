import h.Hchat.hooks.items.sportstep.StepScopeKey;
import java.util.HashMap;
import java.util.Map;

public final class StepScopeKeyRegression {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(StepScopeKey.of("", "A") == null);
        check(StepScopeKey.of("host", " ") == null);
        String a = StepScopeKey.of("com.tencent.mm", "account-A");
        String b = StepScopeKey.of("com.tencent.mm", "account-B");
        String clone = StepScopeKey.of("com.tencent.mm.clone", "account-A");
        check(a != null && a.length() == 64 && a.equals(StepScopeKey.of("com.tencent.mm", "account-A")));
        check(!a.equals(b) && !a.equals(clone));
        check(!StepScopeKey.of("ab", "c").equals(StepScopeKey.of("a", "bc")));
        Map<String, String> days = new HashMap<>();
        days.put(a, "2026-11-04");
        check(days.get(b) == null && days.get(clone) == null);
        check("2026-11-04".equals(days.get(a)));
        System.out.println("Step host/account namespace checks passed");
    }
}
