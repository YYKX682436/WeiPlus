package h.Hchat.hooks.api.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HostApkPathDecisionTest {
    @Test public void matchingCurrentReportsAcceptNormalizedPath() {
        assertEquals(HostApkPathDecision.Result.MATCH,
                HostApkPathDecision.compare("/data/app/wx/base.apk", "/data/app/wx/./base.apk",
                        "/data/app/wx/cache/../base.apk"));
    }

    @Test public void frameworkOldPathIsReportedAsMismatchWithoutFallback() {
        assertEquals(HostApkPathDecision.Result.MISMATCH,
                HostApkPathDecision.compare("/data/app/wx-old/base.apk", "/data/app/wx-new/base.apk",
                        "/data/app/wx-new/base.apk"));
    }

    @Test public void missingPathRemainsRetryableDiagnostic() {
        assertEquals(HostApkPathDecision.Result.INCOMPLETE,
                HostApkPathDecision.compare("/data/app/wx/base.apk", "", "/data/app/wx/base.apk"));
    }
}
