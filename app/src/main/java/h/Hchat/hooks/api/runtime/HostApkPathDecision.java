package h.Hchat.hooks.api.runtime;

import java.io.File;
import java.io.IOException;

/** Compares the three current path reports without searching for historical APKs. */
public final class HostApkPathDecision {
    public enum Result { MATCH, INCOMPLETE, MISMATCH }

    private HostApkPathDecision() {}

    public static Result compare(String frameworkPath, String contextPath, String packageManagerPath) {
        if (empty(frameworkPath) || empty(contextPath) || empty(packageManagerPath)) {
            return Result.INCOMPLETE;
        }
        return samePath(frameworkPath, contextPath) && samePath(frameworkPath, packageManagerPath)
                ? Result.MATCH : Result.MISMATCH;
    }

    public static boolean samePath(String first, String second) {
        if (empty(first) || empty(second)) return false;
        try {
            return new File(first).getCanonicalPath().equals(new File(second).getCanonicalPath());
        } catch (IOException unavailable) {
            return first.equals(second);
        }
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
