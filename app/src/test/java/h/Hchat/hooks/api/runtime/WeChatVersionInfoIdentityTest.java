package h.Hchat.hooks.api.runtime;

import static org.junit.Assert.assertTrue;

import h.Hchat.hooks.api.model.WeChatVersionInfo;
import org.junit.Test;

public class WeChatVersionInfoIdentityTest {
    @Test public void versionFieldsAreDiagnosticAndDoNotRejectRuntimeIdentity() {
        WeChatVersionInfo old = info("8.0.49", 1L, "apk-dex-metadata", "patch-metadata");
        WeChatVersionInfo future = info("future-build", -1L, "apk-dex-metadata", "patch-metadata");
        WeChatVersionInfo unnamed = info("", 0L, "apk-dex-metadata", "patch-metadata");
        assertTrue(WeChatVersionApi.hasCompleteRuntimeIdentity(old));
        assertTrue(WeChatVersionApi.hasCompleteRuntimeIdentity(future));
        assertTrue(WeChatVersionApi.hasCompleteRuntimeIdentity(unnamed));
        assertTrue(WeChatVersionApi.hostIdentityKey(old).equals(WeChatVersionApi.hostIdentityKey(future)));
        assertTrue(WeChatVersionApi.hostIdentityKey(old).equals(WeChatVersionApi.hostIdentityKey(unnamed)));
        assertTrue(!WeChatVersionApi.hostIdentityKey(old).equals(WeChatVersionApi.hostIdentityKey(
                info("8.0.49", 1L, "apk-replaced", "patch-metadata"))));
        assertTrue(!WeChatVersionApi.hostIdentityKey(old).equals(WeChatVersionApi.hostIdentityKey(
                info("8.0.49", 1L, "apk-dex-metadata", "patch-replaced"))));
    }

    private static WeChatVersionInfo info(String name, long code, String apk, String patchFile) {
        return new WeChatVersionInfo("com.tencent.mm.wx", name, code, "client", "tinker", "patch",
                0L, apk, patchFile, "loader", "runtime-cache-key");
    }
}
