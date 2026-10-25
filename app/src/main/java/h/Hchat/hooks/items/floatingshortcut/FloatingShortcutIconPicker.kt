package h.Hchat.hooks.items.floatingshortcut

import android.app.Activity
import android.content.Intent
import io.github.libxposed.api.XposedInterface
import h.Hchat.hooks.core.XposedRuntime
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object FloatingShortcutIconPicker {
    private data class Pending(
        val activity: WeakReference<Activity>,
        val key: String,
        val callback: (FloatingShortcutIconPickResult) -> Unit
    )

    private val nextRequestCode = AtomicInteger(REQUEST_CODE_START)
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val ownedHooks = ArrayList<XposedInterface.HookHandle>()
    @Volatile private var hookGeneration = 0L
    private val hookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    @Synchronized
    fun launch(
        activity: Activity,
        key: String,
        callback: (FloatingShortcutIconPickResult) -> Unit
    ) {
        try {
            hookActivityHierarchy(activity.javaClass)
        } catch (error: Throwable) {
            // This owner cannot deliver a partial hierarchy: invalidate it and roll back every handle.
            val abandoned = pending.values.toList()
            pending.clear()
            releaseHooksIfIdle()
            abandoned.forEach { request ->
                runCatching { request.callback(FloatingShortcutIconPickResult.CANCELLED) }
                    .onFailure { h.Hchat.utils.HLog.e("[Hchat:Picker] 取消选择请求失败", it) }
            }
            h.Hchat.utils.HLog.e("[Hchat:Picker] 安装选择结果 Hook 失败", error)
            callback(FloatingShortcutIconPickResult.FAILED)
            return
        }
        val requestCode = allocateRequestCode()
        pending[requestCode] = Pending(WeakReference(activity), key, callback)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivityForResult(intent, requestCode) }
            .onFailure {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    activity.startActivityForResult(
                        Intent.createChooser(fallback, "选择悬浮菜单图标"),
                        requestCode
                    )
                }.onFailure {
                    pending.remove(requestCode).also { releaseHooksIfIdle() }?.callback?.invoke(FloatingShortcutIconPickResult.FAILED)
                }
            }
    }

    private fun hookActivityHierarchy(activityClass: Class<*>) {
        var current: Class<*>? = activityClass
        while (current != null && Activity::class.java.isAssignableFrom(current)) {
            hookActivityResult(current)
            hookActivityDestroy(current)
            current = current.superclass
        }
    }

    private fun allocateRequestCode(): Int {
        repeat(REQUEST_CODE_END - REQUEST_CODE_START + 1) {
            val candidate = nextRequestCode.updateAndGet { current ->
                if (current >= REQUEST_CODE_END) REQUEST_CODE_START else current + 1
            }
            if (!pending.containsKey(candidate)) return candidate
        }
        val oldest = pending.keys.minOrNull() ?: REQUEST_CODE_START
        pending.remove(oldest).also { releaseHooksIfIdle() }?.callback?.invoke(FloatingShortcutIconPickResult.CANCELLED)
        return oldest
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (!hookedClasses.add(clazz)) return
        runCatching {
            val installingGeneration = hookGeneration
            XposedRuntime.hookAllMethods(clazz, "onActivityResult", XposedInterface.Hooker { chain ->
                val args = chain.args
                val receiver = chain.thisObject
                var succeeded = false
                val value = try {
                    chain.proceed().also { succeeded = true }
                } finally {
                    if (!succeeded && hookGeneration == installingGeneration) {
                        val code = args.getOrNull(0) as? Int
                        val request = code?.let(pending::get)
                        if (request != null && request.activity.get() === receiver) {
                            pending.remove(code, request)
                            releaseHooksIfIdle()
                        }
                    }
                }
                run after@ {
                    if (hookGeneration != installingGeneration) return@after
                    val requestCode = args.getOrNull(0) as? Int ?: return@after
                    val result = pending[requestCode] ?: return@after
                    val activity = result.activity.get()
                    if (activity == null) {
                        pending.remove(requestCode, result).also { releaseHooksIfIdle() }
                        return@after
                    }
                    if (receiver !== activity || !pending.remove(requestCode, result).also { releaseHooksIfIdle() }) return@after
                    val resultCode = args.getOrNull(1) as? Int ?: return@after
                    val data = args.getOrNull(2) as? Intent
                    val uri = data?.data
                    if (resultCode != Activity.RESULT_OK || uri == null) {
                        result.callback(FloatingShortcutIconPickResult.CANCELLED)
                        return@after
                    }
                    Thread({
                        val path = runCatching {
                            FloatingShortcutIconStore.saveFromUri(activity, result.key, uri)
                        }.getOrNull()
                        activity.runOnUiThread {
                            if (activity.isFinishing || activity.isDestroyed) {
                                FloatingShortcutIconStore.delete(activity, path)
                                return@runOnUiThread
                            }
                            result.callback(
                                if (path != null) {
                                    FloatingShortcutIconPickResult.Saved(path)
                                } else {
                                    FloatingShortcutIconPickResult.FAILED
                                }
                            )
                        }
                    }, "Hchat-FloatingShortcutIcon").start()
                }
                value
            }).also { ownedHooks.addAll(it) }
        }.onFailure {
            hookedClasses.remove(clazz)
            throw it
        }
    }

    @Synchronized
    private fun hookActivityDestroy(clazz: Class<*>) {
        if (!destroyHookedClasses.add(clazz)) return
        runCatching {
            val installingGeneration = hookGeneration
            XposedRuntime.hookAllMethods(clazz, "onDestroy", XposedInterface.Hooker { chain ->
                val receiver = chain.thisObject
                try {
                    chain.proceed()
                } finally {
                    run after@ {
                        if (hookGeneration != installingGeneration) return@after
                        val activity = receiver as? Activity ?: return@after
                        pending.entries.forEach { entry ->
                            val owner = entry.value.activity.get()
                            if (owner == null || owner === activity) {
                                pending.remove(entry.key, entry.value).also { releaseHooksIfIdle() }
                            }
                        }
                    }
                }
            }).also { ownedHooks.addAll(it) }
        }.onFailure {
            destroyHookedClasses.remove(clazz)

            throw it
        }
    }

    private val destroyHookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()


    @Synchronized
    private fun releaseHooksIfIdle() {
        if (pending.isNotEmpty()) return
        hookGeneration++
        val iterator = ownedHooks.iterator()
        while (iterator.hasNext()) {
            val handle = iterator.next()
            runCatching { handle.unhook() }
                .onSuccess { iterator.remove() }
                .onFailure { h.Hchat.utils.HLog.e("[Hchat:Picker] 卸载结果 Hook 失败", it) }
        }
        hookedClasses.clear()
        destroyHookedClasses.clear()
    }

    private const val REQUEST_CODE_START = 0x7410
    private const val REQUEST_CODE_END = 0x74ff
}

sealed class FloatingShortcutIconPickResult {
    data class Saved(val path: String) : FloatingShortcutIconPickResult()
    object CANCELLED : FloatingShortcutIconPickResult()
    object FAILED : FloatingShortcutIconPickResult()
}
