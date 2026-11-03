import android.os.Handler
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.HookRegistry
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

private fun await(label: String, predicate: () -> Boolean) {
    val deadline = System.nanoTime() + 5_000_000_000L
    while (!predicate() && System.nanoTime() < deadline) Thread.sleep(5)
    check(predicate()) { "timed out waiting for $label" }
}

fun main() {
    val handler = Handler.main
    val recoveredAttempts = AtomicInteger()
    val recoveryThreads = Collections.synchronizedSet(mutableSetOf<String>())
    DexInstallScheduler.schedule("recovery", stage = DexInstallScheduler.Stage.EARLY) {
        recoveryThreads.add(Thread.currentThread().name)
        recoveredAttempts.incrementAndGet() >= 2
    }
    await("first transient failure") { recoveredAttempts.get() == 1 && handler.pendingCount() == 1 }
    handler.runNext()
    await("successful recovery") { recoveredAttempts.get() == 2 }
    DexInstallScheduler.schedule("recovery", stage = DexInstallScheduler.Stage.EARLY) {
        recoveredAttempts.incrementAndGet()
        true
    }
    check(recoveredAttempts.get() == 2) { "successful task must stop and deduplicate future schedules" }
    check(recoveryThreads == setOf("Hchat-DexInstall")) { "retry must reuse the single install worker: $recoveryThreads" }

    val exhaustedAttempts = AtomicInteger()
    DexInstallScheduler.schedule("exhausted", stage = DexInstallScheduler.Stage.EARLY) {
        exhaustedAttempts.incrementAndGet()
        false
    }
    for (attempt in 1..7) {
        await("bounded retry $attempt") { exhaustedAttempts.get() == attempt }
        if (attempt < 7) {
            await("scheduled retry $attempt") { handler.pendingCount() == 1 }
            handler.runNext()
        }
    }
    check(handler.pendingCount() == 0) { "retry budget must stop after the initial attempt plus six bounded retries" }

    DexInstallScheduler.schedule("exhausted", stage = DexInstallScheduler.Stage.EARLY) {
        exhaustedAttempts.incrementAndGet() >= 8
    }
    await("later readiness callback restarts an exhausted task") { exhaustedAttempts.get() == 8 }

    val ownedAttempts = AtomicInteger()
    HookRegistry.withFeature("owned_feature") {
        DexInstallScheduler.schedule("owned-pending", stage = DexInstallScheduler.Stage.EARLY) {
            ownedAttempts.incrementAndGet()
            false
        }
    }
    await("feature-owned pending task") { ownedAttempts.get() == 1 && handler.pendingCount() == 1 }
    check(DexInstallScheduler.pendingFeatureOwners() == setOf("owned_feature"))
    DexInstallScheduler.cancelFeature("owned_feature")
    check(handler.pendingCount() == 0 && DexInstallScheduler.pendingFeatureOwners().isEmpty()) {
        "runtime revalidation must cancel old-context feature tasks"
    }
    println("PASS: transient recovery, success stop, six bounded retries, re-entry after exhaustion, feature task cancellation, one worker")
}
