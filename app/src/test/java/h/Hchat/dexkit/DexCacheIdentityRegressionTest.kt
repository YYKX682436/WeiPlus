package h.Hchat.dexkit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DexCacheIdentityRegressionTest {
    @Test fun comparesFullPackageDexPatchAndScopeIdentity() {
        val base = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|apk:18dex|patch-files|stable"
        assertTrue(DexCacheIdentity.sameRuntime(base, base))
        assertTrue(DexCacheIdentity.sameRuntime("$base|feature_schema_v4", "$base|feature_schema_v4"))
        assertFalse(DexCacheIdentity.sameRuntime("$base|feature_schema_v3", "$base|feature_schema_v4"))
        assertFalse(DexCacheIdentity.sameRuntime(base, base.replace("apk:18dex", "apk:17dex")))
        assertFalse(DexCacheIdentity.sameRuntime(base, base.replace("patch-files", "patch-files-v2")))
        assertFalse(DexCacheIdentity.sameRuntime(base, base.replace("stable", "other-scope")))
        assertFalse(DexCacheIdentity.sameRuntime(base, "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000"))
        assertFalse(DexCacheIdentity.sameRuntime("", base))
    }

    @Test fun rejectsSchemaSevenSegmentAndDamagedRuntimeKeys() {
        val current = "com.tencent.mm.wx|8.0.76|3140|client|tinker|patch|1700000000|dex-crc|patch-file-metadata|stable"
        assertFalse(DexCacheIdentity.sameRuntime(current.substringBeforeLast('|'), current))
        assertFalse(DexCacheIdentity.sameRuntime("com.tencent.mm.wx|8.0.76|3140|client|tinker|patch|1700000000", "com.tencent.mm.wx|8.0.76|3140|client|tinker|patch|1700000000"))
        assertFalse(DexCacheIdentity.sameRuntime("pkg|8.0.76|3140|client|tinker|patch|mtime", current))
        assertFalse(DexCacheIdentity.sameRuntime(current, current.replace("3140", "not-a-code")))
        assertFalse(DexCacheIdentity.sameRuntime(current, current.replace("patch-file-metadata", "")))
    }

    @Test fun identityChangesInvalidateHotCacheEvenWhenVersionMetadataDoesNot() {
        val first = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-crc-a|patch-files|stable"
        val sameHotRuntime = first
        val replacedApk = first.replace("dex-crc-a", "dex-crc-b")
        assertTrue(DexCacheIdentity.sameRuntime(first, sameHotRuntime))
        assertFalse(DexCacheIdentity.sameRuntime(first, replacedApk))
    }
}
