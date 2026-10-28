package h.Hchat.hooks.core

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Method

open class BaseFeature {
    open fun featureId(): String = ""
    open fun name(): String = ""
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    fun registerSettingsProvider(provider: Any) {}
    fun logError(message: String, error: Throwable?) {}
}
class FeatureContext(
    private val finder: Finder = Finder(),
    private val bus: EventBus = EventBus(),
    private val context: Context = Context()
) {
    fun hostContext(): Context = context
    fun dexFinder(): Finder = finder
    fun eventBus(): EventBus = bus
}
class Finder(
    var resolveResult: Boolean = false,
    var addMsgPreProcessMethod: Method? = null
) {
    fun resolveAddMsgApi(): Boolean = resolveResult
}
class EventBus {
    var throwOnPost: Boolean = false
    val events = mutableListOf<Any>()
    fun post(event: Any) { if (throwOnPost) error("event fixture failure"); events += event }
}
object DexInstallScheduler {
    enum class Stage { WARMUP }
    fun schedule(id: String, name: String, stage: Stage, task: () -> Boolean) {}
}
object HookRegistry {
    private val handles = linkedMapOf<String, StubHandle>()
    private var generation = 1L
    fun get(): HookRegistry = this
    fun installationGeneration(): Long = generation
    fun isInstallationGenerationActive(value: Long): Boolean = value == generation
    fun isActive(handle: HookHandle?): Boolean = (handle as? StubHandle)?.active == true
    fun hookOnce(key: String, method: Method, callback: XposedInterface.Hooker,
                 priority: Int = XposedInterface.PRIORITY_DEFAULT): HookHandle {
        val previous = handles[key]
        if (previous?.active == true) return previous
        return StubHandle(method, callback).also { handles[key] = it }
    }
    fun invalidateGeneration() {
        generation++
        handles.values.forEach { it.unhook() }
    }
    fun clearForTest() {
        handles.values.forEach { it.unhook() }
        handles.clear()
        generation++
    }
    fun callbackFor(method: Method): XposedInterface.Hooker? = handles.values
        .firstOrNull { it.active && it.method == method }?.callback

    private class StubHandle(
        val method: Method,
        var callback: XposedInterface.Hooker
    ) : HookHandle {
        var active = true
        override fun getExecutable(): java.lang.reflect.Executable = method
        override fun getId(): String? = null
        override fun unhook() { active = false }
        override fun replaceHook(callback: XposedInterface.Hooker): HookHandle {
            this.callback = callback
            return this
        }
    }
}
