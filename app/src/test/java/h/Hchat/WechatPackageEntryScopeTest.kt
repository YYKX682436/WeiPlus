package h.Hchat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WechatPackageEntryScopeTest {
    @Test fun entryScopeKeepsBothPackagesAndTheirProcessNamespacesWithoutVersionRules() {
        for (pkg in listOf(WechatPackage.OFFICIAL, WechatPackage.MODIFIED)) {
            assertTrue(WechatPackage.isModuleEntryScope(pkg, pkg))
            assertTrue(WechatPackage.isModuleEntryScope(pkg, "$pkg:appbrand0"))
            assertTrue(WechatPackage.isModuleEntryScope(pkg, "$pkg:tools"))
            assertFalse(WechatPackage.isModuleEntryScope(pkg, "$pkg.evil"))
            assertFalse(WechatPackage.isModuleEntryScope(pkg, "other:$pkg"))
            assertFalse(WechatPackage.isModuleEntryScope(pkg, null))
        }
        assertFalse(WechatPackage.isModuleEntryScope("com.tencent.mm.evil", "com.tencent.mm.evil"))
    }
}
