package h.Hchat.dexkit

import android.content.Context
import android.content.SharedPreferences
import h.Hchat.hooks.api.runtime.WeChatVersionApi
import h.Hchat.preferences.HchatStorage
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object DexMethodCache {
    private const val CACHE_KEY = "cache.key"

    fun prefs(context: Context, name: String): SharedPreferences {
        return HchatStorage.preferences(context, name)
    }

    fun runtimeKey(context: Context, classLoader: ClassLoader): String {
        return WeChatVersionApi.buildCacheKey(context, classLoader)
    }

    fun load(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Method? {
        if (runtimeKey.isBlank()) return null
        if (!ensureRuntimeKey(prefs, runtimeKey)) return null
        val descriptor = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return null
        return loadMethodDescriptor(descriptor, classLoader)
    }

    /** Reuses a descriptor only when every persisted package/APK/DEX/Tinker identity field matches. */
    fun loadCrossProcess(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Method? {
        if (runtimeKey.isBlank()) return null
        val cachedRuntimeKey = prefs.getString(CACHE_KEY, "").orEmpty()
        if (!sameRuntimeAcrossProcesses(cachedRuntimeKey, runtimeKey)) return null
        val descriptor = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return null
        return loadMethodDescriptor(descriptor, classLoader)
    }

    fun save(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        method: Method?
    ) {
        if (runtimeKey.isBlank() || method == null) return
        runCatching {
            val editor = prefs.edit()
            if (!sameRuntime(prefs.getString(CACHE_KEY, ""), runtimeKey)) {
                editor.clear()
            }
            editor
                .putString(CACHE_KEY, runtimeKey)
                .putString(name, method.toDexDescriptor())
                .apply()
        }
    }

    fun loadConstructor(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Constructor<*>? {
        if (runtimeKey.isBlank()) return null
        if (!ensureRuntimeKey(prefs, runtimeKey)) return null
        val descriptor = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return null
        return loadConstructorDescriptor(descriptor, classLoader)
    }

    fun saveConstructor(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        constructor: Constructor<*>?
    ) {
        if (runtimeKey.isBlank() || constructor == null) return
        runCatching {
            val editor = prefs.edit()
            if (!sameRuntime(prefs.getString(CACHE_KEY, ""), runtimeKey)) {
                editor.clear()
            }
            editor
                .putString(CACHE_KEY, runtimeKey)
                .putString(name, constructor.toDexDescriptor())
                .apply()
        }
    }

    fun loadList(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): List<Method> {
        if (runtimeKey.isBlank()) return emptyList()
        if (!ensureRuntimeKey(prefs, runtimeKey)) return emptyList()
        val descriptors = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return emptyList()
        return descriptors
            .split('\n')
            .mapNotNull { descriptor ->
                descriptor.takeIf { it.isNotBlank() }?.let {
                    loadMethodDescriptor(it, classLoader)
                }
            }
    }

    fun loadListCrossProcess(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): List<Method> {
        if (runtimeKey.isBlank()) return emptyList()
        val cachedRuntimeKey = prefs.getString(CACHE_KEY, "").orEmpty()
        if (!sameRuntimeAcrossProcesses(cachedRuntimeKey, runtimeKey)) return emptyList()
        val descriptors = prefs.getString(name, "")?.takeIf { it.isNotBlank() } ?: return emptyList()
        return descriptors
            .split('\n')
            .mapNotNull { descriptor ->
                descriptor.takeIf { it.isNotBlank() }?.let {
                    loadMethodDescriptor(it, classLoader)
                }
            }
    }

    data class UpgradeResponseLookup(val methods: List<Method>, val invalid: Boolean)

    /** An incomplete descriptor list is never a successful lookup. */
    fun loadUpgradeResponse(prefs: SharedPreferences, runtimeKey: String, classLoader: ClassLoader): UpgradeResponseLookup {
        val saved = runCatching { prefs.getString(CACHE_KEY, "").orEmpty() }
            .getOrElse { return UpgradeResponseLookup(emptyList(), true) }
        if (!sameRuntimeAcrossProcesses(saved, runtimeKey)) return UpgradeResponseLookup(emptyList(), false)
        val raw = runCatching { prefs.getString("upgrade_response", "").orEmpty() }
            .getOrElse { return UpgradeResponseLookup(emptyList(), true) }
        if (raw.isBlank()) return UpgradeResponseLookup(emptyList(),
            runCatching { prefs.contains("upgrade_response") }.getOrDefault(true))
        val descriptors = raw.split('\n')
        val methods = descriptors.mapNotNull { if (it.isBlank()) null else loadMethodDescriptor(it, classLoader) }
        return if (methods.size == descriptors.size) UpgradeResponseLookup(methods, false)
        else UpgradeResponseLookup(emptyList(), true)
    }

    fun saveList(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        methods: List<Method>
    ) {
        if (runtimeKey.isBlank() || methods.isEmpty()) return
        runCatching {
            val editor = prefs.edit()
            if (!sameRuntime(prefs.getString(CACHE_KEY, ""), runtimeKey)) {
                editor.clear()
            }
            editor
                .putString(CACHE_KEY, runtimeKey)
                .putString(name, methods.distinctBy { it.toGenericString() }.joinToString("\n") { it.toDexDescriptor() })
                .apply()
        }
    }

    fun clear(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String
    ) {
        runCatching {
            val editor = prefs.edit()
            if (!sameRuntime(prefs.getString(CACHE_KEY, ""), runtimeKey)) {
                editor.clear().putString(CACHE_KEY, runtimeKey)
            }
            editor.remove(name).apply()
        }
    }

    private fun Method.toDexDescriptor(): String {
        return buildString {
            append('L')
            append(declaringClass.name.replace('.', '/'))
            append(";->")
            append(name)
            append('(')
            parameterTypes.forEach { append(it.toDexType()) }
            append(')')
            append(returnType.toDexType())
        }
    }

    private fun Constructor<*>.toDexDescriptor(): String {
        return buildString {
            append('L')
            append(declaringClass.name.replace('.', '/'))
            append(";-><init>(")
            parameterTypes.forEach { append(it.toDexType()) }
            append(")V")
        }
    }

    private fun loadMethodDescriptor(descriptor: String, classLoader: ClassLoader): Method? =
        runCatching { DexMethod(descriptor).getMethodInstance(classLoader) }
            .getOrNull()
            ?.takeIf { runCatching { it.toDexDescriptor() == descriptor }.getOrDefault(false) }

    private fun loadConstructorDescriptor(descriptor: String, classLoader: ClassLoader): Constructor<*>? =
        runCatching { DexMethod(descriptor).getConstructorInstance(classLoader) }
            .getOrNull()
            ?.takeIf { runCatching { it.toDexDescriptor() == descriptor }.getOrDefault(false) }

    private fun Class<*>.toDexType(): String {
        if (isPrimitive) {
            return when (this) {
                java.lang.Void.TYPE -> "V"
                java.lang.Boolean.TYPE -> "Z"
                java.lang.Byte.TYPE -> "B"
                java.lang.Character.TYPE -> "C"
                java.lang.Short.TYPE -> "S"
                java.lang.Integer.TYPE -> "I"
                java.lang.Long.TYPE -> "J"
                java.lang.Float.TYPE -> "F"
                java.lang.Double.TYPE -> "D"
                else -> "V"
            }
        }
        if (isArray) return name.replace('.', '/')
        return "L${name.replace('.', '/')};"
    }

    private fun ensureRuntimeKey(prefs: SharedPreferences, runtimeKey: String): Boolean {
        if (runtimeKey.isBlank()) return false
        val cachedKey = prefs.getString(CACHE_KEY, "").orEmpty()
        if (sameRuntime(cachedKey, runtimeKey)) return true
        val updated = runCatching {
            prefs.edit()
                .clear()
                .putString(CACHE_KEY, runtimeKey)
                .commit()
        }.getOrDefault(false)
        return updated && prefs.getString(CACHE_KEY, "") == runtimeKey
    }

    private fun sameRuntimeAcrossProcesses(cachedRuntimeKey: String, runtimeKey: String): Boolean {
        if (cachedRuntimeKey.isBlank() || runtimeKey.isBlank()) return false
        return DexCacheIdentity.sameRuntime(cachedRuntimeKey, runtimeKey)
    }

    private fun sameRuntime(cachedRuntimeKey: String?, runtimeKey: String): Boolean =
        DexCacheIdentity.sameRuntime(cachedRuntimeKey, runtimeKey)
}
