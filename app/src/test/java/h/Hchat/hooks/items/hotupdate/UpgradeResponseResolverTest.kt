package h.Hchat.hooks.items.hotupdate

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpgradeResponseResolverTest {
    @get:Rule val temp = TemporaryFolder()
    private val identity = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-crc|patch-files|stable"
    private fun resolve(cache: UpgradeResponseEmptyCache, invalid: Boolean = false, cached: List<String> = emptyList(), query: () -> List<String>, save: (List<String>) -> Unit = {}): List<String> =
        cache.withIdentity(identity, "v1") { state ->
            UpgradeResponseResolver.resolve(state, cached, invalid, query, { it.takeUnless { value -> value == "invalid" } }, save)
        }

    @Test fun successfulEmptyIsScannedOnceAcrossInstances() {
        var calls = 0
        val first = UpgradeResponseEmptyCache(temp.root)
        val next = UpgradeResponseEmptyCache(temp.root)
        repeat(3) { resolve(if (it == 0) first else next, query = { calls++; emptyList() }) }
        assertEquals(1, calls)
    }

    @Test fun concurrentInstancesSerializeTheFirstEmptyQuery() {
        val start = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val workers = (0 until 8).map {
            Thread {
                try {
                    start.await()
                    resolve(UpgradeResponseEmptyCache(temp.root), query = {
                        calls.incrementAndGet()
                        Thread.sleep(30)
                        emptyList()
                    })
                } catch (error: Throwable) { errors.add(error) }
            }.apply { start() }
        }
        start.countDown()
        workers.forEach { it.join(5000); assertFalse(it.isAlive) }
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(1, calls.get())
    }

    @Test fun errorsNonzeroAndInvalidDescriptorsRemainRetryable() {
        val cache = UpgradeResponseEmptyCache(temp.root)
        try { resolve(cache, query = { error("dex failure") }); fail() } catch (_: IllegalStateException) { }
        var calls = 0
        repeat(2) { resolve(cache, query = { calls++; listOf("invalid") }) }
        assertEquals(2, calls)
        resolve(cache, query = { emptyList() })
        resolve(cache, invalid = true, query = { calls++; emptyList() })
        resolve(cache, invalid = true, query = { calls++; emptyList() })
        assertEquals(4, calls)
    }

    @Test fun unavailableStorageKeepsScanningAndDoesNotBlockPositiveResults() {
        val path = temp.newFile("not-a-directory")
        val cache = UpgradeResponseEmptyCache(path)
        var calls = 0
        repeat(2) { resolve(cache, query = { calls++; emptyList() }) }
        assertEquals(2, calls)
        assertEquals(listOf("ok"), resolve(cache, query = { listOf("ok") }))
    }

    @Test fun cancelledGenerationCannotInstallAResolvedHook() {
        val registry = h.Hchat.hooks.core.HookRegistry.get()
        registry.unhookAll()
        val generation = registry.installationGeneration()
        val method = String::class.java.getMethod("length")
        try {
            registry.withInstallationGeneration(generation) {
                val methods = UpgradeResponseEmptyCache(temp.root).withIdentity(identity, "v1") { marker ->
                    UpgradeResponseResolver.resolve(marker, emptyList<java.lang.reflect.Method>(), false,
                        query = { registry.unhookAll(); listOf(method) },
                        resolveCandidate = { it }, savePositive = {})
                }
                methods.forEach { registry.hook(it, io.github.libxposed.api.XposedInterface.Hooker { chain -> chain.proceed() }) }
            }
            fail("revoked generation must not install a hook")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertEquals(0, registry.size())
    }

    @Test fun positiveAndPartialCandidatesCannotBeHiddenByEmptyMarker() {
        val cache = UpgradeResponseEmptyCache(temp.root)
        resolve(cache, query = { emptyList() })
        var calls = 0
        assertEquals(listOf("ok"), resolve(cache, cached = listOf("ok"), query = { calls++; emptyList() }))
        assertEquals(0, calls)
        var saved = 0
        repeat(2) { assertEquals(listOf("ok"), resolve(cache, query = { calls++; listOf("ok", "invalid") }, save = { saved++ })) }
        assertEquals(2, calls)
        assertEquals(0, saved)
    }
}
