import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import h.Hchat.hooks.core.HookRegistry
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val registry = HookRegistry.get()
    val method = String::class.java.getMethod("length")
    val permanent = registry.hook(method, XC_MethodHook())
    repeat(1_000) {
        val first = registry.hook(method, XC_MethodHook())
        val second = registry.hook(method, XC_MethodHook())
        check(registry.size() == 3)
        registry.unhook(first)
        registry.unhook(second)
        check(!first.active && !second.active)
        check(registry.size() == 1) { "Removed hooks remained in the registry" }
        check(permanent.active) { "Unrelated hook removed" }
    }

    val keyedMethod = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)
    val hooksBefore = XposedBridge.totalHookCalls
    val keyedA = registry.hookOnce("shared:test", keyedMethod, XC_MethodHook())
    val keyedB = registry.hookOnce("shared:test", keyedMethod, XC_MethodHook())
    check(keyedA === keyedB) { "Repeated shared-hook installation returned different handles" }
    check(XposedBridge.totalHookCalls == hooksBefore + 1) { "Shared Hook was installed more than once" }
    check(registry.size() == 2)
    check(runCatching {
        registry.hookOnce("shared:test", method, XC_MethodHook())
    }.isFailure) { "A key must not be reassigned to another method" }
    registry.unhook(keyedA)
    val keyedC = registry.hookOnce("shared:test", keyedMethod, XC_MethodHook())
    check(keyedC !== keyedA && registry.size() == 2) { "Unhook must release the key for a later generation" }

    val leaseActive = AtomicBoolean(true)
    var partialInstall: XC_MethodHook.Unhook? = null
    val callsBeforeCancelledInstall = XposedBridge.totalHookCalls
    check(runCatching {
        registry.withInstallationGeneration(
            registry.installationGeneration(),
            isLeaseActive = { leaseActive.get() }
        ) {
            partialInstall = registry.hook(method, XC_MethodHook())
            leaseActive.set(false)
            registry.hook(method, XC_MethodHook())
        }
    }.isFailure) { "A canceled install task must not add another Hook" }
    check(partialInstall?.active == false && registry.size() == 2) {
        "Hooks installed by a canceled task must be rolled back"
    }
    check(XposedBridge.totalHookCalls == callsBeforeCancelledInstall + 1)

    registry.unhook(null)
    val failing = registry.hook(method, XC_MethodHook())
    failing.fail = true
    check(runCatching { registry.unhook(failing) }.isFailure)
    check(registry.size() == 3) { "Failed unhook lost the handle needed to retry" }
    failing.fail = false
    registry.unhook(failing)
    registry.unhook(failing)
    check(registry.size() == 2)

    val bulkFailure = registry.hookOnce("shared:bulk-failure", method, XC_MethodHook())
    bulkFailure.fail = true
    val oldGeneration = registry.installationGeneration()
    registry.unhookAll()
    check(registry.size() == 1 && bulkFailure.active) { "Bulk unload must retain a failed handle for retry" }
    check(!permanent.active && !keyedC.active)
    val callsBeforeStaleSharedHook = XposedBridge.totalHookCalls
    check(runCatching {
        registry.hookOnce("shared:bulk-failure", method, XC_MethodHook())
    }.isFailure) { "A failed unhook from an old generation must not be reused" }
    check(XposedBridge.totalHookCalls == callsBeforeStaleSharedHook) {
        "A failed shared-hook teardown must not create a duplicate callback"
    }
    bulkFailure.fail = false
    registry.unhookAll()
    check(registry.size() == 0 && !bulkFailure.active) { "A later lifecycle pass must retry failed unhooks" }
    check(runCatching {
        registry.withInstallationGeneration(oldGeneration) {
            registry.hook(method, XC_MethodHook())
        }
    }.isFailure) { "A stale asynchronous generation reinstalled a Hook" }
    println("PASS: 1000 removal cycles, shared-key dedupe, key release, individual and bulk failed-unhook retry, canceled-task rollback, stale-generation rejection")
}
