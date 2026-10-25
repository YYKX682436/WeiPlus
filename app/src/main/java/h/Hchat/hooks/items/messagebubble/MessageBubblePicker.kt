package h.Hchat.hooks.items.messagebubble

import android.app.Activity
import android.content.Intent
import android.net.Uri
import io.github.libxposed.api.XposedInterface
import h.Hchat.hooks.core.XposedRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object MessageBubblePicker {
    private data class Pending(
        val activity: Activity,
        val slot: MessageBubbleSlot,
        val callback: (MessageBubblePickResult) -> Unit
    )

    private val nextRequestCode = AtomicInteger(REQUEST_CODE_START)
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val ownedHooks = ArrayList<XposedInterface.HookHandle>()
    @Volatile private var hookGeneration = 0L
    private val hookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    @JvmStatic
    fun launch(
        activity: Activity,
        slot: MessageBubbleSlot,
        callback: (MessageBubblePickResult) -> Unit = {}
    ) {
        synchronized(this) {
            try {
                hookActivityResult(activity.javaClass)
                hookActivityResult(Activity::class.java)
                hookActivityDestroy()
            } catch (error: Throwable) {
                // This owner cannot deliver a partial hierarchy: invalidate it and roll back every handle.
                val abandoned = pending.values.toList()
                pending.clear()
                releaseHooksIfIdle()
                abandoned.forEach { request ->
                    runCatching { request.callback(MessageBubblePickResult.CANCELLED) }
                        .onFailure { h.Hchat.utils.HLog.e("[Hchat:Picker] 取消选择请求失败", it) }
                }
                h.Hchat.utils.HLog.e("[Hchat:Picker] 安装选择结果 Hook 失败", error)
                callback(MessageBubblePickResult.FAILED)
                return
            }
            val requestCode = nextRequestCode.updateAndGet { current ->
                if (current >= REQUEST_CODE_END) REQUEST_CODE_START else current + 1
            }
            pending[requestCode] = Pending(activity, slot, callback)
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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
                            Intent.createChooser(fallback, "选择消息气泡图片"),
                            requestCode
                        )
                    }.onFailure {
                        pending.remove(requestCode).also { releaseHooksIfIdle() }?.callback?.invoke(MessageBubblePickResult.FAILED)
                    }
                }
        }
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
                        if (request != null && request.activity === receiver) {
                            pending.remove(code, request)
                            releaseHooksIfIdle()
                        }
                    }
                }
                run after@ {
                    if (hookGeneration != installingGeneration) return@after
                    val requestCode = args.getOrNull(0) as? Int ?: return@after
                    val result = pending[requestCode] ?: return@after
                    if (result.activity !== receiver || !pending.remove(requestCode, result)) return@after
                    releaseHooksIfIdle()
                    val resultCode = args.getOrNull(1) as? Int ?: return@after
                    val data = args.getOrNull(2) as? Intent
                    val uri = data?.data
                    if (resultCode != Activity.RESULT_OK || uri == null) {
                        result.callback(MessageBubblePickResult.CANCELLED)
                        return@after
                    }
                    takeReadPermission(result.activity, data, uri)
                    Thread({
                        val success = MessageBubbleStore.saveFromUri(result.activity, result.slot, uri)
                        result.activity.runOnUiThread {
                            result.callback(
                                if (success) MessageBubblePickResult.SAVED else MessageBubblePickResult.FAILED
                            )
                        }
                    }, "Hchat-MessageBubbleSave").start()
                }
                value
            }).also { ownedHooks.addAll(it) }
        }.onFailure {
            hookedClasses.remove(clazz)
            throw it
        }
    }

    private fun takeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }


    @Synchronized
    private fun hookActivityDestroy() {
        if (ownedDestroyHook) return
        val installingGeneration = hookGeneration
        val handles = XposedRuntime.hookAllMethods(Activity::class.java, "onDestroy", XposedInterface.Hooker { chain ->
            val owner = chain.thisObject
            try {
                chain.proceed()
            } finally {
                if (hookGeneration == installingGeneration) pending.entries.forEach { entry ->
                    if (entry.value.activity === owner) pending.remove(entry.key, entry.value)
                }
                releaseHooksIfIdle()
            }
        })
        ownedHooks.addAll(handles)
        ownedDestroyHook = true
    }

    private var ownedDestroyHook = false

    @Synchronized
    private fun releaseHooksIfIdle() {
        if (pending.isNotEmpty()) return
        hookGeneration++
        ownedDestroyHook = false
        val iterator = ownedHooks.iterator()
        while (iterator.hasNext()) {
            val handle = iterator.next()
            runCatching { handle.unhook() }
                .onSuccess { iterator.remove() }
                .onFailure { h.Hchat.utils.HLog.e("[Hchat:Picker] 卸载结果 Hook 失败", it) }
        }
        hookedClasses.clear()
    }

    private const val REQUEST_CODE_START = 0x6B10
    private const val REQUEST_CODE_END = 0x6BFF
}

enum class MessageBubblePickResult {
    SAVED,
    CANCELLED,
    FAILED
}
