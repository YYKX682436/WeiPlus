package h.Hchat.hooks.items.hotupdate

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpgradeResponseEmptyCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val identity = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-crc|patch-files|stable"

    @Test fun emptyIsReusableOnlyForMatchingCompleteIdentityAndRule() {
        val cache = UpgradeResponseEmptyCache(temp.root)
        cache.withIdentity(identity, "v1") { state -> assertFalse(state.isEmpty()); state.markEmpty() }
        cache.withIdentity(identity, "v1") { state -> assertTrue(state.isEmpty()) }
        cache.withIdentity(identity, "v2") { state -> assertFalse(state.isEmpty()) }
        cache.withIdentity(identity.replace("dex-crc", "new-dex"), "v1") { state -> assertFalse(state.isEmpty()) }
        cache.withIdentity(identity.replace("patch-files", "new-patch"), "v1") { state -> assertFalse(state.isEmpty()) }
        cache.withIdentity("broken", "v1") { state -> state.markEmpty(); assertFalse(state.isEmpty()) }
    }

    @Test fun positiveDiscoveryPreventsStaleEmptyFromWinningAcrossInstances() {
        val writer = UpgradeResponseEmptyCache(temp.root)
        val reader = UpgradeResponseEmptyCache(temp.root)
        writer.withIdentity(identity, "v1") { it.markEmpty() }
        reader.withIdentity(identity, "v1") { it.markPositive() }
        writer.withIdentity(identity, "v1") { it.markEmpty(); assertFalse(it.isEmpty()) }
        reader.withIdentity(identity, "v2") { assertFalse(it.isEmpty()) }
    }

    @Test fun storageFailureDisablesEmptyReuseWithoutBlockingPositiveResult() {
        val cache = UpgradeResponseEmptyCache(temp.root)
        cache.withIdentity(identity, "v1") { it.markEmpty() }
        val marker = temp.root.listFiles()!!.single { it.name.endsWith(".empty") }
        val positive = java.io.File(marker.path.removeSuffix(".empty") + ".positive")
        assertTrue(positive.mkdir())
        cache.withIdentity(identity, "v1") { it.markPositive() }
        assertTrue(java.io.File(temp.root, "upgrade-response-unsafe").isFile)
        cache.withIdentity(identity, "v1") { assertFalse(it.isEmpty()) }
    }

    @Test fun separateProcessesReuseOneResultWithoutTouchingOtherCaches() {
        val sync = temp.newFile("sync-cache").apply { writeText("sync") }
        val manual = temp.newFile("manual-cache").apply { writeText("manual") }
        val sharedDir = java.io.File(temp.root, "initially-absent")
        val classes = listOf(
            UpgradeResponseProcessProbe::class.java, UpgradeResponseEmptyCache::class.java,
            h.Hchat.dexkit.DexCacheIdentity::class.java, h.Hchat.WechatPackage::class.java,
            kotlin.Unit::class.java
        ).map { java.io.File(it.protectionDomain.codeSource.location.toURI()).path }.distinct()
        val javaCommand = java.io.File(System.getProperty("java.home"), "bin/java").path
        val children = (0 until 5).map {
            ProcessBuilder(javaCommand, "-cp", classes.joinToString(java.io.File.pathSeparator),
                UpgradeResponseProcessProbe::class.java.name, sharedDir.path)
                .redirectErrorStream(true).start()
        }
        children.forEach { child ->
            assertTrue("child timed out", child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS))
            val output = child.inputStream.bufferedReader().readText()
            assertEquals(output, 0, child.exitValue())
        }
        assertEquals(listOf("query"), java.io.File(sharedDir, "queries").readLines())
        assertEquals("sync", sync.readText())
        assertEquals("manual", manual.readText())
    }

    @Test fun brokenLockForcesConservativeRetryForOtherInstances() {
        val cache = UpgradeResponseEmptyCache(temp.root)
        cache.withIdentity(identity, "v1") { it.markEmpty() }
        val lockFile = java.io.File(temp.root, "upgrade-response.lock")
        assertTrue(lockFile.delete())
        assertTrue(lockFile.mkdir())
        cache.withIdentity(identity, "v1") { assertFalse(it.isEmpty()) }
        assertTrue(lockFile.delete())
        UpgradeResponseEmptyCache(temp.root).withIdentity(identity, "v1") { assertFalse(it.isEmpty()) }
    }

    @Test fun invalidationAndOtherIdentityDoNotErasePositiveCacheFiles() {
        val existing = temp.newFile("positive-cache")
        existing.writeText("sync-and-manual")
        val cache = UpgradeResponseEmptyCache(temp.root)
        cache.withIdentity(identity, "v1") { it.markEmpty() }
        cache.withIdentity(identity, "v1") { state -> assertTrue(state.isEmpty()); state.invalidate(); assertFalse(state.isEmpty()) }
        assertEquals("sync-and-manual", existing.readText())
    }
}
