import h.Hchat.hooks.api.runtime.WeChatVersionGate;

public final class WeChatVersionGateRegression {
    public static void main(String[] args) {
        expect(true, "com.tencent.mm", "com.tencent.mm", "8.0.76", 3140L, true, "official main .76");
        expect(true, "com.tencent.mm", "com.tencent.mm:appbrand0", "8.0.80", 3220L, true, "official appbrand .80");
        expect(true, "com.tencent.mm.wx", "com.tencent.mm.wx", "8.0.76", 3140L, true, "modified main .76");
        expect(true, "com.tencent.mm.wx", "com.tencent.mm.wx:tools", "8.0.80", 3220L, true, "modified subprocess .80");
        expect(false, "com.tencent.mm", "com.tencent.mm", "8.0.75", 3130L, true, "below minimum");
        expect(false, "com.tencent.mm.wx", "com.tencent.mm.wx2", "8.0.80", 3220L, true, "package prefix collision");
        expect(false, "com.tencent.mm", "com.tencent.mm", "", 3220L, true, "missing name");
        expect(false, "com.tencent.mm", "com.tencent.mm", "8.0.80-beta", 3220L, true, "non-numeric suffix");
        expect(false, "com.tencent.mm", "com.tencent.mm", "8.0.80", 3140L, true, "contradictory identity");
        expect(false, "com.tencent.mm.wx", "com.tencent.mm.wx:appbrand0", "8.0.80", 3220L, false, "package metadata conflict");
        System.out.println("WeChatVersionGate production regression tests passed");
    }

    private static void expect(boolean accepted, String pkg, String process, String name,
                               long code, boolean metadataConsistent, String label) {
        WeChatVersionGate.Decision result = WeChatVersionGate.evaluateForProcess(
                pkg, process, name, code, metadataConsistent);
        if (result.accepted != accepted) {
            throw new AssertionError(label + ": expected accepted=" + accepted + " but got "
                    + result.accepted + " reason=" + result.reason);
        }
    }
}
