import io.github.libxposed.api.XposedInterface
import h.Hchat.tests.api102.FrameworkFixture
import h.Hchat.tests.api102.ChainFixture
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.hooks.core.HookRegistry
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeTarget { fun value() = "target" }

private class ChildTargetLoader(parent: ClassLoader, private val bytes: ByteArray) : ClassLoader(parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name != RuntimeTarget::class.java.name) return super.loadClass(name, resolve)
        var loaded = findLoadedClass(name)
        if (loaded == null) loaded = defineClass(name, bytes, 0, bytes.size)
        if (resolve) resolveClass(loaded)
        return loaded
    }
}

fun main() {
    val framework = FrameworkFixture()
    XposedRuntime.initialize(framework.create())
    val registry = HookRegistry.get()
    val observe = XposedInterface.Hooker { it.proceed() }
    val method = String::class.java.getMethod("length")
    val permanent = registry.hook(method, observe)
    repeat(1_000) {
        val first = registry.hook(method, observe)
        val second = registry.hook(method, observe)
        check(registry.size() == 3)
        registry.unhook(first)
        registry.unhook(second)
        check(!FrameworkFixture.handle(first).active && !FrameworkFixture.handle(second).active)
        check(registry.size() == 1) { "Removed hooks remained in the registry" }
        check(FrameworkFixture.handle(permanent).active) { "Unrelated hook removed" }
    }

    val keyedMethod = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)
    val hooksBefore = framework.calls
    val keyedA = registry.hookOnce("shared:test", keyedMethod, observe)
    val keyedB = registry.hookOnce("shared:test", keyedMethod, observe)
    check(keyedA === keyedB) { "Repeated shared-hook installation returned different handles" }
    check(framework.calls == hooksBefore + 1) { "Shared Hook was installed more than once" }
    check(registry.size() == 2)
    check(runCatching {
        registry.hookOnce("shared:test", method, observe)
    }.isFailure) { "A key must not be reassigned to another method" }
    registry.unhook(keyedA)
    val keyedC = registry.hookOnce("shared:test", keyedMethod, observe)
    check(keyedC !== keyedA && registry.size() == 2) { "Unhook must release the key for a later generation" }

    val leaseActive = AtomicBoolean(true)
    var partialInstall: XposedInterface.HookHandle? = null
    val callsBeforeCancelledInstall = framework.calls
    check(runCatching {
        registry.withInstallationGeneration(
            registry.installationGeneration(),
            isLeaseActive = { leaseActive.get() }
        ) {
            partialInstall = registry.hook(method, observe)
            leaseActive.set(false)
            registry.hook(method, observe)
        }
    }.isFailure) { "A canceled install task must not add another Hook" }
    check(partialInstall?.let { FrameworkFixture.handle(it).active } == false && registry.size() == 2) {
        "Hooks installed by a canceled task must be rolled back"
    }
    check(framework.calls == callsBeforeCancelledInstall + 1)

    val beforeFailure = registry.size()
    framework.failOn = keyedMethod
    check(runCatching {
        registry.withInstallationGeneration(registry.installationGeneration()) {
            registry.hook(method, observe)
            registry.hook(keyedMethod, observe)
        }
    }.isFailure)
    framework.failOn = null
    check(registry.size() == beforeFailure) { "A partial framework installation failure leaked a prefix" }

    registry.unhook(null)
    val failing = registry.hook(method, observe)
    FrameworkFixture.handle(failing).fail = true
    check(runCatching { registry.unhook(failing) }.isFailure)
    check(registry.size() == 3) { "Failed unhook lost the handle needed to retry" }
    FrameworkFixture.handle(failing).fail = false
    registry.unhook(failing)
    registry.unhook(failing)
    check(registry.size() == 2)

    val bulkFailure = registry.hookOnce("shared:bulk-failure", method, observe)
    FrameworkFixture.handle(bulkFailure).fail = true
    val oldGeneration = registry.installationGeneration()
    registry.unhookAll()
    check(registry.size() == 1 && FrameworkFixture.handle(bulkFailure).active) { "Bulk unload must retain a failed handle for retry" }
    check(!FrameworkFixture.handle(permanent).active && !FrameworkFixture.handle(keyedC).active)
    val callsBeforeStaleSharedHook = framework.calls
    check(runCatching {
        registry.hookOnce("shared:bulk-failure", method, observe)
    }.isFailure) { "A failed unhook from an old generation must not be reused" }
    check(framework.calls == callsBeforeStaleSharedHook) {
        "A failed shared-hook teardown must not create a duplicate callback"
    }
    FrameworkFixture.handle(bulkFailure).fail = false
    registry.unhookAll()
    check(registry.size() == 0 && !FrameworkFixture.handle(bulkFailure).active) { "A later lifecycle pass must retry failed unhooks" }
    check(runCatching {
        registry.withInstallationGeneration(oldGeneration) {
            registry.hook(method, observe)
        }
    }.isFailure) { "A stale asynchronous generation reinstalled a Hook" }
    val order = ArrayList<String>()
    val priorityMethod = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)
    val low = registry.hook(priorityMethod, XposedInterface.Hooker { chain ->
        order += "low-in"
        val args = chain.args.toTypedArray()
        args[0] = 2
        val result = chain.proceed(args)
        order += "low-out"
        "low[$result]"
    }, -100)
    val high = registry.hook(priorityMethod, XposedInterface.Hooker { chain ->
        order += "high-in"
        check(runCatching { (chain.args as MutableList<Any?>)[0] = 3 }.isFailure) {
            "The official Chain argument list must stay immutable"
        }
        val result = chain.proceed()
        order += "high-out"
        "high[$result]"
    }, 100)
    val installed = listOf(low, high).map(FrameworkFixture::handle).sortedByDescending { it.priority }
    var hostCalls = 0
    fun invokeAt(index: Int, owner: Any?, arguments: Array<Any?>): Any? {
        if (index == installed.size) { hostCalls++;return (owner as String).substring(arguments[0] as Int) }
        return ChainFixture(priorityMethod, owner, arguments) { receiver, args ->
            invokeAt(index+1, receiver, args)
        }.run(installed[index].hooker)
    }
    check(invokeAt(0,"abcd",arrayOf(1)) == "high[low[cd]]")
    check(hostCalls == 1 && order == listOf("high-in","low-in","low-out","high-out"))
    var retained: XposedInterface.Chain? = null
    val boundary = ChainFixture(method,"abcd",emptyArray()) { _, _ -> 4 }
    check(boundary.run { chain -> retained=chain;chain.proceed() } == 4)
    check(runCatching { retained!!.proceed() }.isFailure) { "A returned Chain remained usable" }
    val blocked=ChainFixture(method,"abcd",emptyArray()) { _, _ -> error("Short circuit entered the host") }
    check(blocked.run { "blocked" } == "blocked" && blocked.proceeds==0)
    val hostFailure=IllegalStateException("host failed")
    val failedBoundary=ChainFixture(method,"abcd",emptyArray()) { _, _ -> throw hostFailure }
    check(runCatching { failedBoundary.run(observe) }.exceptionOrNull() === hostFailure && failedBoundary.proceeds==1)
    registry.unhookAll()
    val overloads = String::class.java.declaredMethods.filter { it.name == "substring" }
    check(overloads.size > 1)
    val batchStart = framework.handles.size
    framework.failOn = overloads.last()
    check(runCatching { XposedRuntime.hookAllMethods(String::class.java, "substring", observe) }.isFailure)
    framework.failOn = null
    val partialBatch = framework.handles.drop(batchStart)
    check(partialBatch.isNotEmpty() && partialBatch.none { it.active }) { "A direct multi-method batch retained its installed prefix" }
    var observations = 0
    val batchObserver = XposedInterface.Hooker { chain -> observations++; chain.proceed() }
    framework.failOn = overloads.last()
    framework.afterRegistration = Runnable { framework.handles.last().fail = true }
    check(runCatching { XposedRuntime.hookAllMethods(String::class.java, "substring", batchObserver) }.isFailure)
    framework.afterRegistration = null
    framework.failOn = null
    val stranded = framework.handles.last()
    check(stranded.active && XposedRuntime.failedUnhookCount() == 1) { "Rollback failure lost ownership" }
    val retried = XposedRuntime.hookAllMethods(String::class.java, "substring", batchObserver)
    val callbacks = framework.handles.filter { it.active && it.executable == stranded.executable }
    var calls = 0
    fun invokeBatch(index: Int): Any? = if (index == callbacks.size) { calls++; "host" } else {
        ChainFixture(stranded.executable, "abcd", arrayOf(1)) { _, _ -> invokeBatch(index + 1) }.run(callbacks[index].hooker)
    }
    check(invokeBatch(0) == "host" && calls == 1 && observations == 1) { "A failed batch still executed module work" }
    stranded.fail = false
    retried.forEach { it.unhook() }
    registry.unhookAll()
    check(XposedRuntime.failedUnhookCount() == 0 && framework.handles.none { it.active })
    val original = XposedRuntime.hook(method, XposedInterface.Hooker { "old" })
    val snapshot = FrameworkFixture.handle(original).hooker
    val replacement = original.replaceHook(XposedInterface.Hooker { "new" })
    check(ChainFixture(method,"abcd",emptyArray()) { _, _ -> 4 }.run(snapshot) == "old")
    original.unhook()
    check(ChainFixture(method,"abcd",emptyArray()) { _, _ -> 4 }.run(FrameworkFixture.handle(replacement).hooker) == "new")
    replacement.unhook()
    check(framework.handles.none { it.active })

    val featureGeneration = registry.installationGeneration()
    val changedMethod = RuntimeTarget::class.java.getMethod("value")
    val unchangedMethod = String::class.java.getMethod("length")
    lateinit var changedFeatureHook: XposedInterface.HookHandle
    lateinit var unchangedFeatureHook: XposedInterface.HookHandle
    lateinit var partialFeatureHook: XposedInterface.HookHandle
    registry.withInstallationGeneration(featureGeneration) {
        registry.withFeature("changed_feature") {
            changedFeatureHook = registry.hookOnce("feature:changed", changedMethod, observe)
            check(changedFeatureHook === registry.hookOnce("feature:changed", changedMethod, observe)) {
                "A feature retry must reuse its active Hook"
            }
        }
        registry.withFeature("unchanged_feature") {
            unchangedFeatureHook = registry.hookOnce("feature:unchanged", unchangedMethod, observe)
        }
        check(runCatching {
            registry.withFeature("partial_feature") {
                partialFeatureHook = registry.hookOnce("feature:partial", method, observe)
                error("injected feature install failure")
            }
        }.isFailure)
    }
    check(!registry.isActive(partialFeatureHook)) { "A failed feature install must roll back its own partial hook" }
    val targetBytes = RuntimeTarget::class.java.getResourceAsStream("/RuntimeTarget.class")!!.use { it.readBytes() }
    val changedLoader = ChildTargetLoader(RuntimeTarget::class.java.classLoader!!, targetBytes)
    check(registry.featuresWithChangedClasses(changedLoader) == setOf("changed_feature")) {
        "Runtime code comparison must identify only the feature whose declaring class changed"
    }
    registry.unhookFeature("changed_feature")
    check(!registry.isActive(changedFeatureHook) && registry.isActive(unchangedFeatureHook)) {
        "Feature cleanup must remove only the affected feature's hooks"
    }
    registry.unhookAll()
    println("PASS: feature-owned rollback, per-feature loaded-class change detection, selective unhook and retry dedupe")
    println("PASS: combined batch install/unhook failure remains owned and inert on retry, explicit teardown clears all; replacement preserves old snapshots and handle ownership")
    println("PASS: API102 immutable arguments, copied argument replacement, return replacement, short circuit, priority nesting, host exception identity and expired Chain rejection")
    println("PASS: 1000 removal cycles, shared-key dedupe, key release, individual and bulk failed-unhook retry, canceled-task rollback, stale-generation rejection")
}
