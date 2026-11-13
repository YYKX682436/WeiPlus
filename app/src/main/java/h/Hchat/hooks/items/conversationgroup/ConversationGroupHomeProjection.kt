package h.Hchat.hooks.items.conversationgroup

import android.database.Cursor
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedInterface
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Executable
import java.lang.reflect.Modifier
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Homepage-only projection. Never changes the official account's stored parent or message data. */
internal object ConversationGroupHomeProjection {
    private const val TAG = "[Hchat:ConversationGroupHome]"
    private const val CACHE = "homepage_projection_v1"
    private val lifecycleLock = Any()
    @Volatile private var lifecycleGeneration = 0L
    private data class Installation(val lifecycle: Long, val hooks: Long)
    private val installed = ConcurrentHashMap.newKeySet<Executable>()
    private val legacyQueries = ThreadLocal<ArrayDeque<Installation>>()
    private val refreshers = Collections.synchronizedMap(WeakHashMap<Any, Refresh>())
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var hiddenIds: () -> Set<String> = { emptySet() }
    @Volatile private var legacyStorage: WeakReference<Any>? = null
    private fun installation(): Installation = synchronized(lifecycleLock) {
        Installation(lifecycleGeneration, HookRegistry.get().installationGeneration()).also {
            if (!isActive(it)) throw CancellationException("首页安装代次已失效")
        }
    }

    private fun isActive(installation: Installation): Boolean =
        lifecycleGeneration == installation.lifecycle && HookRegistry.get().isInstallationGenerationActive(installation.hooks)

    private data class Refresh(val method: Method, val argument: WeakReference<Any>? = null)

    fun install(context: FeatureContext, hidden: (() -> Set<String>)? = null): Boolean = runCatching {
        val installation = installation()
        synchronized(lifecycleLock) {
            if (!isActive(installation)) throw CancellationException("首页安装代次已失效")
            if (hidden != null) hiddenIds = hidden
        }
        val prefs = DexMethodCache.prefs(context.hostContext(), "Hchat_conversation_group_home_cache")
        val runtime = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        var methods = DexMethodCache.loadList(prefs, runtime, context.hostClassLoader(), CACHE)
        fun cacheComplete(cached: List<Method>): Boolean {
            val legacy = cached.filter { it.parameterTypes.size == 2 && it.returnType.isArray }
            val modern = cached.filter { it.parameterTypes.size == 1 && !it.returnType.isPrimitive }
            val roots = cached.filter {
                it.parameterTypes.isEmpty() && it.returnType == String::class.java
            }
            val notices = cached.filter {
                it.parameterTypes.size == 3 && it.returnType == Void.TYPE
            }
            val updates = cached.filter(::isModernUpdate)
            return (legacy.isNotEmpty() || modern.isNotEmpty()) &&
                (legacy.isEmpty() || roots.size == 1 && notices.size >= legacy.size) &&
                (modern.isEmpty() || modern.all { page ->
                    updates.any { it.declaringClass.isAssignableFrom(page.declaringClass) }
                })
        }
        if (!cacheComplete(methods)) {
            fun find(vararg anchors: String): List<Method> = context.dexKitBridge().findMethod(FindMethod().apply {
                matcher(MethodMatcher().apply { usingStrings(anchors.toList()) })
            }).mapNotNull { runCatching { it.getMethodInstance(context.hostClassLoader()) }.getOrNull() }
                .filter { !Modifier.isAbstract(it.modifiers) && !Modifier.isStatic(it.modifiers) }
            val legacy = find("MicroMsg.ConversationWithCacheAdapter", "refreshChangedConversation searchArray")
                .filter { it.parameterTypes.size == 2 && it.returnType.isArray }
            val modern = find("all flag is same, count:", "message_fold_config2")
                .filter { it.parameterTypes.size == 1 && !it.returnType.isPrimitive }
            val roots = if (legacy.isEmpty()) emptyList() else find("((parentRef is null)")
                .filter { it.parameterTypes.isEmpty() && it.returnType == String::class.java && it.declaringClass.name.startsWith("com.tencent.mm.storage.") }
            val notices = if (legacy.isEmpty()) emptyList() else find("unreadcheck onConversationStorageNotifyChange")
                .filter { method -> legacy.any { it.declaringClass == method.declaringClass } && method.parameterTypes.size == 3 }
            val updates = if (modern.isEmpty()) emptyList() else find("remove conv, parentRef:")
                .filter { method -> modern.any { it.declaringClass.isAssignableFrom(method.declaringClass) } && isModernUpdate(method) }
            check(legacy.isNotEmpty() || modern.isNotEmpty()) { "没有定位到原生首页数据入口" }
            check(legacy.isEmpty() || (roots.size == 1 && notices.size == legacy.size)) { "旧版首页刷新入口不完整" }
            check(modern.isEmpty() || updates.isNotEmpty()) { "新版首页刷新入口不完整" }
            methods = (legacy + modern + roots + notices + updates).distinct()
            DexMethodCache.saveList(prefs, runtime, CACHE, methods)
        }
        val legacy = methods.filter { it.returnType.isArray }
        val modern = methods.filter { it.parameterTypes.size == 1 && !it.returnType.isPrimitive }
        val updates = methods.filter(::isModernUpdate)
        legacy.forEach { changes ->
            val notice = methods.single { it.declaringClass == changes.declaringClass && it.returnType == Void.TYPE }
            // Preserve the specialized native cursor type; only extend its WHERE expression in this scope.
            val queries = KavaReflector.declaredMethods(changes.declaringClass).filter {
                it.parameterTypes.isEmpty() && Cursor::class.java.isAssignableFrom(it.returnType)
            }
            check(queries.isNotEmpty()) { "旧版首页 Cursor 查询缺失" }
            (queries + changes).forEach { method -> hook(method, XposedInterface.Hooker { chain ->
                val entered = captureRefresh(installation, chain.thisObject, Refresh(notice))
                if (!entered) return@Hooker chain.proceed()
                val scopes = legacyQueries.get() ?: ArrayDeque<Installation>().also(legacyQueries::set)
                if (scopes.lastOrNull()?.let { it != installation } == true) scopes.clear()
                scopes.addLast(installation)
                try {
                    chain.proceed()
                } finally {
                    if (scopes.lastOrNull() == installation) scopes.removeLast()
                    if (scopes.isEmpty()) legacyQueries.remove()
                }
            }, installation) }
        }
        methods.filter { it.returnType == String::class.java && it.parameterTypes.isEmpty() }.forEach { root ->
            hook(root, XposedInterface.Hooker { chain ->
                val receiver = chain.thisObject
                var value: Any? = null
                var intercepted = false
                if (!intercepted) value = chain.proceed()
                run after@ {
                    if (!isActive(installation)) return@after
                    if (legacyQueries.get()?.lastOrNull() != installation) return@after
                    synchronized(lifecycleLock) {
                        if (!isActive(installation)) return@after
                        legacyStorage = WeakReference(receiver)
                    }
                    val original = value as? String ?: return@after
                    value = ConversationGroupHomeVisibility.rootWhere(original, hiddenIds())
                }
                value
            }, installation)
        }
        modern.forEach { page ->
            val conversion = KavaReflector.declaredMethods(page.declaringClass).single { method ->
                method.parameterTypes.size == 2 && method.parameterTypes.all { it.name.startsWith("com.tencent.mm.storage.") } &&
                    method.returnType != Void.TYPE && !method.returnType.isPrimitive
            }
            val rowType = conversion.returnType
            val conversation = KavaReflector.declaredFields(rowType).single {
                !Modifier.isStatic(it.modifiers) && it.type == conversion.parameterTypes[0]
            }
            val listField = KavaReflector.declaredFields(page.returnType).single {
                !Modifier.isStatic(it.modifiers) && List::class.java.isAssignableFrom(it.type)
            }
            val visible = KavaReflector.declaredMethods(page.declaringClass).single {
                it.returnType == Boolean::class.javaPrimitiveType && it.parameterTypes.contentEquals(arrayOf(rowType))
            }
            fun username(row: Any?): String? {
                val native = KavaReflector.readField(conversation, row) ?: return null
                // convertTo is the native storage object's stable public serialization API.
                val values = KavaReflector.invokeMethod(native, "convertTo") as? android.content.ContentValues
                return values?.getAsString("username")
            }
            hook(page, XposedInterface.Hooker { chain ->
                val entered = isActive(installation)
                val result = chain.proceed()
                if (!entered || !isActive(installation)) return@Hooker result
                updates.firstOrNull { it.declaringClass.isInstance(chain.thisObject) }
                ?.let { captureRefresh(installation, chain.thisObject, Refresh(it)) }
                val hidden = hiddenIds()
                if (hidden.isEmpty()) return@Hooker result
                runCatching {
                    val rows = KavaReflector.readField(listField, result) as? List<*> ?: return@runCatching
                    val filtered = rows.filterNot { username(it) in hidden }
                    if (filtered.size != rows.size) {
                        if (rows is MutableList<*>) {
                            @Suppress("UNCHECKED_CAST")
                            val mutable = rows as MutableList<Any?>
                            mutable.clear()
                            mutable.addAll(filtered)
                        } else {
                            check(KavaReflector.writeField(listField, result, ArrayList(filtered)))
                        }
                    }
                    // Preserve native hasMore and next flags, including completely hidden pages.
                }.onFailure { HLog.e("$TAG 过滤首页分页失败", it) }
                result
            }, installation)
            hook(visible, XposedInterface.Hooker { chain ->
                val args = chain.args
                var value: Any? = null
                var intercepted = false
                if (!intercepted) value = chain.proceed()
                run after@ {
                    if (!isActive(installation)) return@after
                    if (value != true) return@after
                    val hidden = hiddenIds()
                    if (hidden.isNotEmpty() && username(args.firstOrNull()) in hidden) run { intercepted = true; value = false }
                }
                value
            }, installation)
        }
        updates.forEach { update -> hook(update, XposedInterface.Hooker { chain ->
            val args = chain.args
            val receiver = chain.thisObject
            run before@ {
                val argument = args?.getOrNull(0)
                captureRefresh(installation, receiver, Refresh(
                    update,
                    if (update.parameterTypes.size == 3 && argument != null) {
                        WeakReference(argument)
                    } else null
                ))
            }
            chain.proceed()
        }, installation) }
        true
    }.getOrElse { HLog.e("$TAG 安装首页投影失败", it); false }

    private fun captureRefresh(installation: Installation, owner: Any, refresh: Refresh): Boolean = synchronized(lifecycleLock) {
        if (!isActive(installation)) return false
        refreshers[owner] = refresh
        true
    }

    private val refreshPending = AtomicBoolean(false)
    private var refreshTask: Runnable? = null

    fun refresh() {
        val installation = runCatching { installation() }.getOrNull() ?: return
        refresh(installation)
    }

    private fun refresh(installation: Installation): Unit = synchronized(lifecycleLock) {
        if (!isActive(installation) || !refreshPending.compareAndSet(false, true)) return
        val task = object : Runnable {
            override fun run() {
                val current = synchronized(lifecycleLock) {
                    if (refreshTask !== this || !isActive(installation)) return
                    refreshTask = null
                    refreshPending.set(false)
                    synchronized(refreshers) { refreshers.entries.map { it.key to it.value } }
                }
                current.forEach { (owner, refresh) ->
                    if (!isActive(installation)) return
                    runCatching {
                        val types = refresh.method.parameterTypes
                        when {
                            types.size == 2 -> KavaReflector.invokeOrThrow(refresh.method, owner, 5, "")
                            types[0] == Integer.TYPE -> legacyStorage?.get()?.takeIf(types[1]::isInstance)?.let {
                                KavaReflector.invokeOrThrow(refresh.method, owner, 5, it, "")
                            }
                            else -> refresh.argument?.get()?.let {
                                KavaReflector.invokeOrThrow(refresh.method, owner, it, 5, "")
                            }
                        }
                    }.onFailure { HLog.e("$TAG 刷新首页投影失败", it) }
                }
            }
        }
        refreshTask = task
        if (!main.post(task)) {
            refreshTask = null
            refreshPending.set(false)
        }
    }

    fun resetAfterUnhookAll() = synchronized(lifecycleLock) {
        lifecycleGeneration++
        installed.clear()
        refreshers.clear()
        legacyStorage = null
        hiddenIds = { emptySet() }
        refreshTask?.let(main::removeCallbacks)
        refreshTask = null
        refreshPending.set(false)
        legacyQueries.remove()
    }

    private fun isModernUpdate(method: Method): Boolean {
        val types = method.parameterTypes
        return method.returnType == Void.TYPE && types.size in 2..3 &&
            types[types.size - 2] == Integer.TYPE && types.last() == String::class.java
    }

    private fun hook(method: Executable, callback: XposedInterface.Hooker, installation: Installation): Unit = synchronized(lifecycleLock) {
        if (!isActive(installation)) throw CancellationException("首页安装代次已失效")
        if (!installed.add(method)) return
        try { HookRegistry.get().withInstallationGeneration(installation.hooks) { HookRegistry.get().hook(method, callback) } }
        catch (error: Throwable) { installed.remove(method); throw error }
    }
}
