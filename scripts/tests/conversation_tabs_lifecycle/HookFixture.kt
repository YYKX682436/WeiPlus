package h.Hchat.hooks.core

import android.content.Context
import io.github.libxposed.api.XposedInterface
import h.Hchat.tests.api102.ChainFixture
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class FeatureContext {
    fun hostClassLoader() = javaClass.classLoader!!
    fun hostContext() = Context()
}
class HookRegistry private constructor() {
    private class Handle(val method: Method, val callback: XposedInterface.Hooker, val remove: (Handle) -> Unit): XposedInterface.HookHandle {
        override fun getExecutable(): Executable = method
        override fun getId(): String? = null
        override fun unhook() = remove(this)
        override fun replaceHook(callback: XposedInterface.Hooker): XposedInterface.HookHandle = error("Not used by lifecycle test")
    }
    private val hooks = linkedMapOf<Method, MutableList<Handle>>()
    var failOn: String? = null
    var unhooks = 0
        private set
    val count get() = hooks.values.sumOf { it.size }
    fun hook(method: Method, callback: XposedInterface.Hooker): XposedInterface.HookHandle {
        if (method.name == failOn) error("Simulated hook failure: ${method.name}")
        return Handle(method,callback) { handle ->
            hooks.values.forEach { it.remove(handle) }
            unhooks++
        }.also { hooks.getOrPut(method) { arrayListOf() }.add(it) }
    }
    fun unhook(hook: XposedInterface.HookHandle) = hook.unhook()
    private fun call(owner: Any,method: Method,original: () -> Any?): Any? {
        val callbacks=hooks[method]?.toList().orEmpty()
        fun at(index: Int): Any? = if(index==callbacks.size) original() else
            ChainFixture(method,owner,emptyArray()) { _, _ -> at(index+1) }.run(callbacks[index].callback)
        return at(0)
    }
    fun invoke(owner: Any, method: Method): Any? = call(owner,method) {
        try { method.invoke(owner) } catch(failure: InvocationTargetException) { throw failure.cause!! }
    }
    fun dispatch(owner: Any, method: Method, result: Any?, error: Throwable? = null) = call(owner,method) {
        if(error!=null)throw error
        result
    }
    fun reset() { hooks.clear(); failOn = null; unhooks = 0 }
    companion object { private val registry = HookRegistry(); fun get() = registry }
}
