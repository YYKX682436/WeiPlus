package h.Hchat.hooks.items.custombottombar

import android.app.Activity
import android.content.Intent
import io.github.libxposed.api.XposedInterface
import h.Hchat.hooks.core.XposedRuntime
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object CustomBottomBarIconPicker {
    private data class Pending(
        val activity: WeakReference<Activity>,
        val tabKey: String,
        val callback: (CustomBottomBarIconPickResult) -> Unit,
        val processing: AtomicBoolean = AtomicBoolean(false)
    )

    private val nextRequestCode = AtomicInteger(REQUEST_CODE_START)
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val ownedHooks = ArrayList<XposedInterface.HookHandle>()
    @Volatile private var hookGeneration = 0L
    private val resultHookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val destroyHookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    @JvmStatic
    fun launch(
        activity: Activity,
        tabKey: String,
        callback: (CustomBottomBarIconPickResult) -> Unit
    ) {
        synchronized(this) {
            val key = tabKey.trim()
            if (!CustomBottomBarIconStore.isSupportedTabKey(key)) {
                callback(CustomBottomBarIconPickResult.FAILED)
                return
            }
            try {
                hookActivityHierarchy(activity.javaClass)
            } catch (error: Throwable) {
                // This owner cannot deliver a partial hierarchy: invalidate it and roll back every handle.
                val abandoned = pending.values.toList()
                pending.clear()
                releaseHooksIfIdle()
                abandoned.forEach { request ->
                    runCatching { request.callback(CustomBottomBarIconPickResult.CANCELLED) }
                        .onFailure { h.Hchat.utils.HLog.e("[Hchat:Picker] 取消选择请求失败", it) }
                }
                h.Hchat.utils.HLog.e("[Hchat:Picker] 安装选择结果 Hook 失败", error)
                callback(CustomBottomBarIconPickResult.FAILED)
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
                            Intent.createChooser(fallback, "选择底栏图标"),
                            requestCode
                        )
                    }.onFailure {
                        pending.remove(requestCode).also { releaseHooksIfIdle() }?.callback?.invoke(
                            CustomBottomBarIconPickResult.FAILED
                        )
                    }
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
        val reused = pending.keys.minOrNull() ?: REQUEST_CODE_START
        pending.remove(reused).also { releaseHooksIfIdle() }?.callback?.invoke(CustomBottomBarIconPickResult.CANCELLED)
        return reused
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (!resultHookedClasses.add(clazz)) return
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
                    val request = pending[requestCode] ?: return@after
                    val activity = request.activity.get()
                    if (activity == null) {
                        pending.remove(requestCode, request).also { releaseHooksIfIdle() }
                        return@after
                    }
                    if (receiver !== activity) return@after
                    val resultCode = args.getOrNull(1) as? Int ?: return@after
                    val data = args.getOrNull(2) as? Intent
                    val uri = data?.data
                    if (resultCode != Activity.RESULT_OK || uri == null) {
                        if (pending.remove(requestCode, request).also { releaseHooksIfIdle() }) {
                            request.callback(CustomBottomBarIconPickResult.CANCELLED)
                        }
                        return@after
                    }
                    if (!request.processing.compareAndSet(false, true)) return@after
                    val appContext = activity.applicationContext
                    Thread({
                        val path = runCatching {
                            CustomBottomBarIconStore.saveFromUri(appContext, request.tabKey, uri)
                        }.getOrNull()
                        val owner = request.activity.get()
                        if (owner == null) {
                            pending.remove(requestCode, request).also { releaseHooksIfIdle() }
                            CustomBottomBarIconStore.delete(appContext, path)
                            return@Thread
                        }
                        owner.runOnUiThread {
                            if (!pending.remove(requestCode, request).also { releaseHooksIfIdle() } ||
                                owner.isFinishing || owner.isDestroyed
                            ) {
                                CustomBottomBarIconStore.delete(appContext, path)
                                return@runOnUiThread
                            }
                            request.callback(
                                path?.let(CustomBottomBarIconPickResult::Saved)
                                ?: CustomBottomBarIconPickResult.FAILED
                            )
                        }
                    }, "Hchat-CustomBottomBarIcon").start()
                }
                value
            }).also { ownedHooks.addAll(it) }
        }.onFailure {
            resultHookedClasses.remove(clazz)
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


    @Synchronized
    private fun releaseHooksIfIdle() {
        if (pending.isNotEmpty()) return
        XposedRuntime.retryFailedUnhooks()
        hookGeneration++
        val iterator = ownedHooks.iterator()
        while (iterator.hasNext()) {
            val handle = iterator.next()
            runCatching { handle.unhook() }
                .onSuccess { iterator.remove() }
                .onFailure { h.Hchat.utils.HLog.e("[Hchat:Picker] 卸载结果 Hook 失败", it) }
        }
        resultHookedClasses.clear()
        destroyHookedClasses.clear()
    }

    private const val REQUEST_CODE_START = 0x7710
    private const val REQUEST_CODE_END = 0x77ff
}

sealed class CustomBottomBarIconPickResult {
    data class Saved(val path: String) : CustomBottomBarIconPickResult()
    object CANCELLED : CustomBottomBarIconPickResult()
    object FAILED : CustomBottomBarIconPickResult()
}
