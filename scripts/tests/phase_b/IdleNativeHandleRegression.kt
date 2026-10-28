package org.luckypray.dexkit

private class FakeCancellation(private val cancelAction: () -> Unit) : IdleCancellation {
    private var active = true
    override fun cancel() {
        if (active) {
            active = false
            cancelAction()
        }
    }
}

private class FakeScheduler(private val now: () -> Long) : IdleTaskScheduler {
    private data class Task(val due: Long, val action: () -> Unit, var active: Boolean = true)
    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMillis: Long, action: () -> Unit): IdleCancellation {
        val task = Task(now() + delayMillis, action)
        tasks += task
        return FakeCancellation { task.active = false }
    }

    fun runDue() {
        while (true) {
            val task = tasks.firstOrNull { it.active && it.due <= now() } ?: return
            task.active = false
            task.action()
        }
    }
}

fun main() {
    var nowNanos = 0L
    val scheduler = FakeScheduler { nowNanos / 1_000_000L }
    var nextHandle = 40L
    var opens = 0
    val released = mutableListOf<Long>()
    var idleCallbacks = 0
    val bridge = IdleNativeHandle(
        idleTimeoutMillis = 30_000L,
        open = { opens++; ++nextHandle },
        release = { released += it },
        nanoTime = { nowNanos },
        scheduler = scheduler,
        onIdleRelease = { idleCallbacks++; error("diagnostic callback failure is isolated") }
    )

    check(!bridge.isLoaded)
    val first = bridge.use { it }
    check(first == 41L && opens == 1 && bridge.isLoaded)
    nowNanos += 20_000_000_000L
    check(bridge.use { it } == first && opens == 1) { "warm query must reuse the native handle" }

    nowNanos += 10_000_000_000L
    scheduler.runDue()
    check(bridge.isLoaded && released.isEmpty()) { "old idle ticket must respect the latest query" }
    nowNanos += 19_999_000_000L
    scheduler.runDue()
    check(bridge.isLoaded && released.isEmpty()) { "release must wait for the full idle interval" }
    nowNanos += 1_000_000L
    scheduler.runDue()
    check(!bridge.isLoaded && released == listOf(first) && idleCallbacks == 1)

    val recovered = bridge.use { it }
    check(recovered == 42L && opens == 2 && bridge.isLoaded) {
        "cold query after idle reclaim must reopen the native handle"
    }
    bridge.close()
    check(!bridge.isValid && !bridge.isLoaded && released == listOf(first, recovered))
    println("DexKit idle lifecycle regression passed: cold, warm, 30s reclaim, lazy reopen, close")
}
