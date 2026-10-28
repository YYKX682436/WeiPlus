package h.Hchat.hooks.api.runtime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numeric, fail-closed host-version gate shared by the earliest module entry and regressions. */
public final class WeChatVersionGate {
    public static final String OFFICIAL_PACKAGE = "com.tencent.mm";
    public static final String MODIFIED_PACKAGE = "com.tencent.mm.wx";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");

    private WeChatVersionGate() {}

    public static Decision evaluate(String packageName, String versionName, long versionCode) {
        if (!isTargetPackage(packageName)) return Decision.reject("unsupported_package");
        int[] version = parse(versionName);
        if (version == null) return Decision.reject("missing_or_invalid_version_name");
        if (isKnownContradiction(version, versionCode)) {
            return Decision.reject("version_name_version_code_conflict");
        }
        if (compare(version, new int[]{8, 0, 76, 0}) < 0) {
            return Decision.reject("below_minimum_8.0.76");
        }
        return new Decision(true, "supported", version);
    }

    public static Decision evaluateForProcess(String packageName, String processName,
                                              String versionName, long versionCode,
                                              boolean packageMetadataConsistent) {
        if (!isTargetPackage(packageName)) return Decision.reject("unsupported_package");
        if (processName == null || !(processName.equals(packageName)
                || processName.startsWith(packageName + ":"))) {
            return Decision.reject("unsupported_process_identity");
        }
        if (!packageMetadataConsistent) return Decision.reject("package_identity_conflict");
        return evaluate(packageName, versionName, versionCode);
    }

    public static boolean isTargetPackage(String packageName) {
        return OFFICIAL_PACKAGE.equals(packageName) || MODIFIED_PACKAGE.equals(packageName);
    }

    public static int[] parse(String versionName) {
        if (versionName == null) return null;
        Matcher matcher = VERSION.matcher(versionName.trim());
        if (!matcher.matches()) return null;
        try {
            int[] result = new int[4];
            for (int i = 0; i < 3; i++) result[i] = Integer.parseInt(matcher.group(i + 1));
            result[3] = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < 4; i++) {
            int compare = Integer.compare(left[i], right[i]);
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static boolean isKnownContradiction(int[] version, long versionCode) {
        if (versionCode <= 0L) return false; // versionCode is corroborating evidence only.
        boolean version076 = version[0] == 8 && version[1] == 0 && version[2] == 76;
        boolean version080 = version[0] == 8 && version[1] == 0 && version[2] == 80;
        // Only reject cross-release values already observed to identify another release.
        // Build numbers are not one-to-one version identities: 8.0.76 has at least 3140/3141.
        return (version076 && versionCode == 3220L)
                || (version080 && (versionCode == 3140L || versionCode == 3141L));
    }

    public static final class Decision {
        public final boolean accepted;
        public final String reason;
        public final int[] version;

        private Decision(boolean accepted, String reason, int[] version) {
            this.accepted = accepted;
            this.reason = reason;
            this.version = version;
        }

        private static Decision reject(String reason) {
            return new Decision(false, reason, null);
        }

        public String versionString() {
            return version == null ? "" : version[0] + "." + version[1] + "." + version[2]
                    + (version[3] > 0 ? "." + version[3] : "");
        }
    }
}
