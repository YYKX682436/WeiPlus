package h.Hchat.hooks.core

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object FeatureRuntimeSlotRegression {
    private data class Runtime(val id: Int, var pendingTasks: Int = 0)

    fun run() {
        val slot = FeatureRuntimeSlot<Runtime>()
        val created = AtomicInteger()
        val workers = 32
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        val instances = Collections.synchronizedList(mutableListOf<Runtime>())
        try {
            repeat(workers) {
                pool.execute {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    instances += slot.getOrCreate {
                        Thread.yield()
                        Runtime(created.incrementAndGet())
                    }
                }
            }
            check(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            pool.shutdown()
            check(pool.awaitTermination(10, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        check(created.get() == 1 && instances.size == workers)
        check(instances.all { it === instances.first() }) {
            "Concurrent config and DexReady requests must share one runtime"
        }
        val pendingRuntime = instances.first().apply { pendingTasks = 1 }
        val afterUnrelatedRuleEdit = slot.getOrCreate { Runtime(created.incrementAndGet()) }
        check(afterUnrelatedRuleEdit === pendingRuntime && afterUnrelatedRuleEdit.pendingTasks == 1) {
            "An unrelated rule edit must leave delayed tasks in the active runtime"
        }

        val closed = AtomicInteger()
        slot.clear { closed.incrementAndGet() }
        slot.clear { closed.incrementAndGet() }
        check(slot.current() == null && closed.get() == 1) { "Destroy must be idempotent" }
        val reopened = slot.getOrCreate { Runtime(created.incrementAndGet()) }
        check(reopened.id == 2 && slot.current() === reopened) { "A new feature install must create a fresh runtime" }
        slot.clear { closed.incrementAndGet() }
        check(closed.get() == 2)
        println("PASS: concurrent runtime creation is single-instance and destroy/reinstall is clean")
    }
}
