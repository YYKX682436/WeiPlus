import io.github.libxposed.api.XposedInterface
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.tests.api102.ChainFixture
import h.Hchat.tests.api102.FrameworkFixture
import io.github.libxposed.api.XposedInterface.HookHandle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private class InstallTarget {
    fun installed() = 1
    fun fail() = 2
}

fun main() {
    val framework = FrameworkFixture()
    XposedRuntime.initialize(framework.create())
    val registry = HookRegistry.get()
    val target = InstallTarget::class.java.getDeclaredMethod("installed")
    val failTarget = InstallTarget::class.java.getDeclaredMethod("fail")
    val generation = registry.installationGeneration()
    val notes = ArrayList<String>()
    var failures = 0
    fun expect(name: String, result: Boolean, evidence: String = "") {
        if (result) println("PASS hookOnce R2 $name${if (evidence.isEmpty()) "" else ": $evidence"}")
        else {
            failures++
            System.err.println("FAIL hookOnce R2 $name${if (evidence.isEmpty()) "" else ": $evidence"}")
        }
    }
    fun callback(name: String) = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        notes += name
        result
    }
    fun handlesForTarget() = framework.handles.filter { it.executable == target }
    fun failNextInstallAfterSharedRegistration(key: String, cancel: Boolean): HookHandle {
        framework.afterRegistration = Runnable {
            framework.afterRegistration = null
            framework.handles.last().fail = true
        }
        if (!cancel) framework.failOn = failTarget
        val lease = AtomicBoolean(true)
        var installed: HookHandle? = null
        val result = runCatching {
            registry.withInstallationGeneration(generation, isLeaseActive = { lease.get() }) {
                installed = registry.hookOnce(key, target, callback("old:$key"))
                if (cancel) lease.set(false)
                registry.hook(failTarget, callback("unrelated:$key"))
            }
        }
        framework.afterRegistration = null
        framework.failOn = null
        expect("${if (cancel) "cancel" else "install failure"} triggered lease rollback", result.isFailure)
        return checkNotNull(installed)
    }
    fun invokeAllTargetHooks(
        handles: List<FrameworkFixture.Handle>,
        hostFailure: Throwable? = null,
        hostCalls: AtomicInteger = AtomicInteger()
    ): Pair<Any?, Int> {
        fun invokeAt(index: Int): Any? {
            if (index == handles.size) {
                hostCalls.incrementAndGet()
                if (hostFailure != null) throw hostFailure
                return 17
            }
            return ChainFixture(target, InstallTarget(), emptyArray()) { _, _ -> invokeAt(index + 1) }
                .run(handles[index].hooker)
        }
        return runCatching { invokeAt(0) }.fold({ it }, { throw it }) to hostCalls.get()
    }

    val dedupeBefore = framework.calls
    val normalA = registry.hookOnce("r2:normal", target, callback("normal"))
    val normalB = registry.hookOnce("r2:normal", target, callback("ignored"))
    expect("valid shared hooks still deduplicate", normalA === normalB && framework.calls == dedupeBefore + 1)
    registry.unhook(normalA)

    val firstKey = "r2:rollback-immediate"
    val oldImmediate = failNextInstallAfterSharedRegistration(firstKey, cancel = false)
    val oldRawImmediate = FrameworkFixture.handle(oldImmediate)
    val immediateBefore = framework.calls
    val replacementImmediate = registry.hookOnce(firstKey, target, callback("new:$firstKey"))
    val immediateRegistrations = framework.calls - immediateBefore
    expect("same-generation reinstall after failed rollback", replacementImmediate !== oldImmediate && immediateRegistrations == 1,
        "oldReturned=${replacementImmediate === oldImmediate}, registrations=$immediateRegistrations")
    val immediateHandles = handlesForTarget().filter { it.active }
    notes.clear()
    val immediateResult = invokeAllTargetHooks(immediateHandles)
    expect("old rollback residue is inert; new hook runs once; host runs once",
        notes == listOf("new:$firstKey") && immediateResult.first == 17 && immediateResult.second == 1,
        "callbacks=$notes host=${immediateResult.second}")
    val immediateFailure = IllegalStateException("R2 host exception")
    val hostFailureCalls = AtomicInteger()
    val hostFailureResult = runCatching { invokeAllTargetHooks(immediateHandles, immediateFailure, hostFailureCalls) }
        .exceptionOrNull()
    expect("host exception identity survives shared hooks", hostFailureResult === immediateFailure && hostFailureCalls.get() == 1,
        "host=${hostFailureCalls.get()}")
    oldRawImmediate.fail = false
    XposedRuntime.retryFailedUnhooks()
    expect("failed rollback handle is physically cleaned", !oldRawImmediate.active && XposedRuntime.failedUnhookCount() == 0)
    registry.unhook(replacementImmediate)

    val secondKey = "r2:retry-before-reinstall"
    val oldHandleRetried = failNextInstallAfterSharedRegistration(secondKey, cancel = false)
    val oldRawRetried = FrameworkFixture.handle(oldHandleRetried)
    oldRawRetried.fail = false
    XposedRuntime.retryFailedUnhooks()
    val retryBefore = framework.calls
    val newAfterRetry = registry.hookOnce(secondKey, target, callback("new:$secondKey"))
    val retryRegistrations = framework.calls - retryBefore
    expect("hookOnce reinstalls after explicit global retry", newAfterRetry !== oldHandleRetried && retryRegistrations == 1,
        "registrations=$retryRegistrations")
    val afterRetryHandles = handlesForTarget().filter { it.active }
    notes.clear()
    val afterRetryResult = invokeAllTargetHooks(afterRetryHandles)
    expect("post-retry call invokes only new callback and host once",
        notes == listOf("new:$secondKey") && afterRetryResult.first == 17 && afterRetryResult.second == 1)
    registry.unhook(oldHandleRetried)
    expect("cleaning old handle preserves new same-key dedupe",
        registry.hookOnce(secondKey, target, callback("ignored:$secondKey")) === newAfterRetry)
    registry.unhook(newAfterRetry)

    val thirdKey = "r2:cancelled-rollback"
    val oldHandleCancelled = failNextInstallAfterSharedRegistration(thirdKey, cancel = true)
    val oldRawCancelled = FrameworkFixture.handle(oldHandleCancelled)
    val cancelBefore = framework.calls
    val newAfterCancel = registry.hookOnce(thirdKey, target, callback("new:$thirdKey"))
    expect("same-generation retry after cancelled lease", newAfterCancel !== oldHandleCancelled && framework.calls == cancelBefore + 1)
    val cancelledHandles = handlesForTarget().filter { it.active }
    notes.clear()
    val cancelledResult = invokeAllTargetHooks(cancelledHandles)
    expect("cancelled lease residue is inert and host proceeds once",
        notes == listOf("new:$thirdKey") && cancelledResult.first == 17 && cancelledResult.second == 1)
    for (handle in listOf(oldRawImmediate, oldRawRetried, oldRawCancelled)) handle.fail = false
    registry.unhookAll()
    expect("final registry and failed-unhook ownership clear", registry.size() == 0 &&
        XposedRuntime.failedUnhookCount() == 0 && framework.handles.none { it.active })
    check(failures == 0) { "$failures R2 HookRegistry state assertions failed" }
}
