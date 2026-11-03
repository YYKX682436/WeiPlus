package h.Hchat.hooks.items.hotupdate

import android.content.Context
import io.github.libxposed.api.XposedInterface
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.Feature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.util.LinkedHashMap
import java.util.WeakHashMap

class DisableHotUpdateFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "屏蔽热更新"

    override fun installationPolicy(): Feature.InstallationPolicy = Feature.InstallationPolicy.ENABLED_ONLY

    override fun isEnabled(context: FeatureContext): Boolean = isEnabled(context.hostContext())

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(DisableHotUpdateSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        if (!isEnabled(context.hostContext())) return
        val classLoader = context.hostClassLoader()
        val complete = install(context.hostContext(), classLoader, context.dexKitBridge())
        if (!complete) {
            val loaderToken = loaderKey(classLoader)
            DexInstallScheduler.scheduleTask(
                "$ID:install:$loaderToken",
                "屏蔽热更新入口补装",
                DexInstallScheduler.Stage.EARLY,
                -900,
            ) {
                install(context.hostContext(), classLoader, context.dexKitBridge())
            }
        }
    }

    companion object {
        const val ID = "disable_hot_update"
        private const val TAG = "[Hchat:HotUpdate]"
        private const val TINKER_APP = "com.tencent.tinker.loader.app.TinkerApplication"
        private const val TINKER_LOADER = "com.tencent.tinker.loader.TinkerLoader"
        private const val TINKER_SYNC_RESPONSE = "com.tencent.mm.plugin.hp.util.TinkerSyncResponse"
        private const val PATCH_RESULT = "com.tencent.tinker.lib.service.PatchResult"
        private const val LEGACY_PATCH_SERVICE = "com.tencent.mm.hotpatch.LegacyTinkerCore\$PatchService"
        private const val CACHE_PREFS = "Hchat_hot_update_method_cache"
        private val RESPONSE_HANDLER_STRINGS = listOf(
            "null cannot be cast to non-null type com.tencent.mm.plugin.hp.util.TinkerSyncResponse",
            "onReceiveUpgradePatch. try to start apply",
            "verify patch signature failed, tinker."
        )
        private val RESPONSE_CONSUMER_STRINGS = listOf(
            "before commandNewApkMd5HardCode, response.newApkMd5 = ",
            ", response.fileMd5 = "
        )

        private enum class EntryState { INSTALLED, NOT_APPLICABLE, RETRY }

        /** Loader keys are weak; values store symbolic entry states and do not retain ClassLoader. */
        private val loaderStates = WeakHashMap<ClassLoader, LoaderState>()

        private class LoaderState(val generation: Long) {
            val early = LinkedHashMap<String, EntryState>()
            val late = LinkedHashMap<String, EntryState>()
        }

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            val sp = HchatStorage.preferences(context, DisableHotUpdateSettings.PREFS_NAME)
            return sp.getBoolean(
                DisableHotUpdateSettings.KEY_ENABLE,
                DisableHotUpdateSettings.DEFAULT_ENABLE
            )
        }

        /** Returns true only when every applicable entry has a successful hook handle. */
        @JvmStatic
        fun install(context: Context?, classLoader: ClassLoader?, dexKit: DexKitBridge?): Boolean {
            if (context == null || classLoader == null || dexKit == null) return false
            if (!isEnabled(context)) return true
            val state = stateFor(classLoader)
            synchronized(state) {
                val earlyReady = installEarlyLocked(context, classLoader, state)
                val cache = DexMethodCache.prefs(context, CACHE_PREFS)
                val runtimeKey = DexMethodCache.runtimeKey(context, classLoader)
                recordLate(state, "sync_response_consumer",
                    hookSyncResponseConsumer(classLoader, dexKit, cache, runtimeKey, state))
                recordLate(state, "upgrade_response",
                    hookUpgradeResponse(context, classLoader, dexKit, cache, runtimeKey, state))
                recordLate(state, "manual_updater",
                    hookManualUpdater(classLoader, dexKit, cache, runtimeKey, state))
                recordLate(state, "legacy_patch_service", hookLegacyPatchService(classLoader, state))
                val complete = earlyReady && LATE_ENTRIES.all { entryComplete(state.late[it]) }
                if (!complete) HLog.e("$TAG install_pending loader=${loaderKey(classLoader)} retryable=true")
                return complete
            }
        }

        /** Each early entry records success independently, so a failed overload can be retried. */
        @JvmStatic
        fun installEarly(context: Context?, classLoader: ClassLoader?): Boolean {
            if (context == null || classLoader == null) return false
            if (!isEnabled(context)) return true
            val state = stateFor(classLoader)
            synchronized(state) { return installEarlyLocked(context, classLoader, state) }
        }

        private fun installEarlyLocked(
            context: Context,
            classLoader: ClassLoader,
            state: LoaderState
        ): Boolean {
            recordEarly(state, "tinker_flags", hookTinkerFlags(classLoader))
            val loaderResult = hookTinkerLoader(classLoader, state)
            val complete = entryComplete(state.early["tinker_flags"]) && entryComplete(loaderResult)
            if (!complete) HLog.e("$TAG early_install_pending loader=${loaderKey(classLoader)}")
            return complete
        }

        private fun hookTinkerFlags(classLoader: ClassLoader): EntryState {
            return try {
                val clazz = classLoader.loadClass(TINKER_APP)
                val method = clazz.declaredMethods.firstOrNull {
                    it.name == "getTinkerFlags" && it.parameterTypes.isEmpty() &&
                        (it.returnType == Int::class.javaPrimitiveType || it.returnType == Int::class.javaObjectType)
                } ?: return EntryState.RETRY.also { HLog.e("$TAG 未找到 TinkerApplication.getTinkerFlags") }
                installHook(classLoader, "early:tinker_flags", method) {
                    XposedInterface.Hooker { 0 }
                }
            } catch (failure: Throwable) {
                HLog.e("$TAG Hook getTinkerFlags 失败: ${failure.message}", failure)
                EntryState.RETRY
            }
        }

        private fun hookTinkerLoader(classLoader: ClassLoader, state: LoaderState): EntryState {
            return try {
                val clazz = classLoader.loadClass(TINKER_LOADER)
                val methods = clazz.declaredMethods.filter { method ->
                    method.name == "tryLoadPatchFilesInternal" && method.parameterTypes.size >= 2 &&
                        method.parameterTypes[0].name == TINKER_APP &&
                        method.parameterTypes[1] == android.content.Intent::class.java
                }
                if (methods.isEmpty()) {
                    HLog.e("$TAG 未找到 TinkerLoader.tryLoadPatchFilesInternal")
                    return EntryState.RETRY
                }
                val results = methods.map { method ->
                    val entry = "tinker_loader:${method.toGenericString()}"
                    val result = installHook(classLoader, "early:$entry", method) {
                        XposedInterface.Hooker { chain ->
                            val intent = chain.args.getOrNull(1) as? android.content.Intent
                            runCatching {
                                val shareIntentUtil = classLoader.loadClass("com.tencent.tinker.loader.shareutil.ShareIntentUtil")
                                val setReturnCode = shareIntentUtil.getDeclaredMethod(
                                    "setIntentReturnCode",
                                    android.content.Intent::class.java,
                                    Int::class.javaPrimitiveType
                                )
                                setReturnCode.isAccessible = true
                                if (intent != null) setReturnCode.invoke(null, intent, -1)
                            }
                            blockedReturnValue(method)
                        }
                    }
                    recordEarly(state, entry, result)
                    result
                }
                if (results.all(::entryComplete)) EntryState.INSTALLED else EntryState.RETRY
            } catch (failure: Throwable) {
                HLog.e("$TAG Hook TinkerLoader 失败: ${failure.message}", failure)
                EntryState.RETRY
            }
        }

        private fun hookManualUpdater(
            classLoader: ClassLoader,
            dexKit: DexKitBridge,
            cache: android.content.SharedPreferences,
            runtimeKey: String,
            state: LoaderState
        ): EntryState {
            return try {
                val cached = DexMethodCache.loadCrossProcess(cache, runtimeKey, classLoader, "manual_updater")
                val method = cached ?: dexKit.findMethod(
                    FindMethod().apply {
                        matcher(MethodMatcher().apply {
                            usingStrings(listOf(
                                "start to run patch",
                                "hot patch verfiy signature error",
                                "patch applying is blocked by TinkerEnsuranceOnFault"
                            ))
                        })
                    }
                ).firstNotNullOfOrNull { data ->
                    runCatching { data.getMethodInstance(classLoader) }.getOrNull()
                        ?.takeIf(::isManualUpdaterMethod)
                }
                if (method == null) {
                    DexMethodCache.clear(cache, runtimeKey, "manual_updater")
                    recordLate(state, "manual_updater", EntryState.NOT_APPLICABLE)
                    HLog.e("$TAG 当前运行时代码没有已确认的手动补丁入口")
                    return EntryState.NOT_APPLICABLE
                }
                if (cached == null) DexMethodCache.save(cache, runtimeKey, "manual_updater", method)
                installHook(classLoader, "late:manual_updater:${method.toGenericString()}", method) {
                    XposedInterface.Hooker { blockedReturnValue(method) }
                }
            } catch (failure: Throwable) {
                HLog.e("$TAG Hook 补丁应用入口失败: ${failure.message}", failure)
                EntryState.RETRY
            }
        }

        private fun hookSyncResponseConsumer(
            classLoader: ClassLoader,
            dexKit: DexKitBridge,
            cache: android.content.SharedPreferences,
            runtimeKey: String,
            state: LoaderState
        ): EntryState {
            return try {
                val responseClass = runCatching { classLoader.loadClass(TINKER_SYNC_RESPONSE) }.getOrNull()
                    ?: return EntryState.NOT_APPLICABLE.also {
                        HLog.i("$TAG 当前运行时代码没有 TinkerSyncResponse")
                    }
                // Push and main processes use separate loaders, but the descriptors are validated
                // against each current response class before they are accepted here.
                val cached = DexMethodCache.loadListCrossProcess(cache, runtimeKey, classLoader, "sync_response_consumer")
                val methods = if (cached.isNotEmpty()) cached else dexKit.findMethod(
                    FindMethod().apply {
                        matcher(MethodMatcher().apply { usingStrings(RESPONSE_CONSUMER_STRINGS) })
                    }
                ).mapNotNull { data ->
                    runCatching { data.getMethodInstance(classLoader) }.getOrNull()
                        ?.takeIf { method -> isSyncResponseConsumer(method, responseClass) }
                }
                if (cached.isEmpty()) {
                    if (methods.isNotEmpty()) DexMethodCache.saveList(cache, runtimeKey, "sync_response_consumer", methods)
                    else DexMethodCache.clear(cache, runtimeKey, "sync_response_consumer")
                }
                if (methods.isEmpty()) {
                    HLog.e("$TAG 当前运行时代码没有热更新下发响应消费入口")
                    return EntryState.NOT_APPLICABLE
                }
                val results = methods.map { method ->
                    val entry = "sync_response_consumer:${method.toGenericString()}"
                    val result = installHook(classLoader, "late:$entry", method) {
                        XposedInterface.Hooker { blockedReturnValue(method) }
                    }
                    recordLate(state, entry, result)
                    result
                }
                if (results.all(::entryComplete)) EntryState.INSTALLED else EntryState.RETRY
            } catch (failure: Throwable) {
                HLog.e("$TAG Hook 热更新下发响应消费失败: ${failure.message}", failure)
                EntryState.RETRY
            }
        }

        private fun hookUpgradeResponse(
            context: Context,
            classLoader: ClassLoader,
            dexKit: DexKitBridge,
            cache: android.content.SharedPreferences,
            runtimeKey: String,
            state: LoaderState
        ): EntryState {
            return try {
                val markerDir = java.io.File(HchatStorage.storageDir(context), "hot_update_upgrade_empty")
                UpgradeResponseEmptyCache(markerDir).withIdentity(runtimeKey, "upgrade_response_v1") { marker ->
                    val lookup = DexMethodCache.loadUpgradeResponse(cache, runtimeKey, classLoader)
                    val invalid = lookup.invalid || lookup.methods.any { !isUpgradeResponseHandler(it) }
                    val methods = UpgradeResponseResolver.resolve(
                        marker, lookup.methods, invalid,
                        query = {
                            dexKit.findMethod(
                                FindMethod().apply {
                                    matcher(MethodMatcher().apply { usingStrings(RESPONSE_HANDLER_STRINGS) })
                                }
                            ).toList()
                        },
                        resolveCandidate = { data ->
                            runCatching { data.getMethodInstance(classLoader) }.getOrNull()
                                ?.takeIf(::isUpgradeResponseHandler)
                        },
                        savePositive = { DexMethodCache.saveList(cache, runtimeKey, "upgrade_response", it) }
                    )
                    if (methods.isEmpty()) {
                        HLog.e("$TAG 当前运行时代码没有热更新响应处理入口")
                        EntryState.NOT_APPLICABLE
                    } else {
                        val results = methods.map { method ->
                            val entry = "upgrade_response:${method.toGenericString()}"
                            val result = installHook(classLoader, "late:$entry", method) {
                                XposedInterface.Hooker { blockedReturnValue(method) }
                            }
                            recordLate(state, entry, result)
                            result
                        }
                        if (results.all(::entryComplete)) EntryState.INSTALLED else EntryState.RETRY
                    }
                }
            } catch (failure: Throwable) {
                HLog.e("$TAG Hook 热更新响应处理失败: ${failure.message}", failure)
                EntryState.RETRY
            }
        }

        /** Compatibility entry used by the focused positive-cache integration probe. */
        @Suppress("unused")
        private fun hookUpgradeResponse(
            context: Context,
            classLoader: ClassLoader,
            dexKit: DexKitBridge,
            cache: android.content.SharedPreferences,
            runtimeKey: String
        ) {
            val state = stateFor(classLoader)
            synchronized(state) {
                recordLate(state, "upgrade_response",
                    hookUpgradeResponse(context, classLoader, dexKit, cache, runtimeKey, state))
            }
        }

        private fun hookLegacyPatchService(classLoader: ClassLoader, state: LoaderState): EntryState {
            return try {
                val clazz = try {
                    classLoader.loadClass(LEGACY_PATCH_SERVICE)
                } catch (_: ClassNotFoundException) {
                    recordLate(state, "legacy_patch_service", EntryState.NOT_APPLICABLE)
                    return EntryState.NOT_APPLICABLE
                }
                val methods = clazz.declaredMethods.filter { method ->
                    method.parameterTypes.size == 1 && method.parameterTypes[0].name == PATCH_RESULT &&
                        method.returnType == Void.TYPE
                }
                if (methods.isEmpty()) {
                    recordLate(state, "legacy_patch_service", EntryState.NOT_APPLICABLE)
                    return EntryState.NOT_APPLICABLE
                }
                val results = methods.map { method ->
                    val entry = "legacy_patch_service:${method.toGenericString()}"
                    val result = installHook(classLoader, "late:$entry", method) {
                        XposedInterface.Hooker { null }
                    }
                    recordLate(state, entry, result)
                    result
                }
                if (results.all(::entryComplete)) EntryState.INSTALLED else EntryState.RETRY
            } catch (failure: Throwable) {
                if (failure is ClassNotFoundException) {
                    recordLate(state, "legacy_patch_service", EntryState.NOT_APPLICABLE)
                    EntryState.NOT_APPLICABLE
                } else {
                    HLog.e("$TAG Hook LegacyTinkerCore 失败: ${failure.message}", failure)
                    EntryState.RETRY
                }
            }
        }

        private fun installHook(
            classLoader: ClassLoader,
            entry: String,
            method: Method,
            hooker: () -> XposedInterface.Hooker
        ): EntryState {
            val key = "hotupdate:${loaderKey(classLoader)}:$entry"
            return try {
                val handle = HookRegistry.get().hookOnce(key, method, hooker())
                if (HookRegistry.get().isActive(handle)) EntryState.INSTALLED else EntryState.RETRY
            } catch (failure: Throwable) {
                HLog.e("$TAG Hook 入口失败 entry=$entry: ${failure.message}", failure)
                EntryState.RETRY
            }
        }

        private fun stateFor(classLoader: ClassLoader): LoaderState = synchronized(loaderStates) {
            val generation = HookRegistry.get().installationGeneration()
            val current = loaderStates[classLoader]
            if (current != null && current.generation == generation) current
            else LoaderState(generation).also { loaderStates[classLoader] = it }
        }

        private fun recordEarly(state: LoaderState, entry: String, result: EntryState) =
            record(state, state.early, entry, result)

        private fun recordLate(state: LoaderState, entry: String, result: EntryState) =
            record(state, state.late, entry, result)

        private fun record(state: LoaderState, entries: MutableMap<String, EntryState>, entry: String, result: EntryState) {
            val previous = entries.put(entry, result)
            if (previous != result) {
                HLog.i("$TAG entry_state=$result entry=$entry generation=${state.generation}")
            }
        }

        private fun entryComplete(state: EntryState?): Boolean =
            state == EntryState.INSTALLED || state == EntryState.NOT_APPLICABLE

        private fun isSyncResponseConsumer(method: Method, responseClass: Class<*>): Boolean =
            method.parameterTypes.any { it == responseClass || it.name == TINKER_SYNC_RESPONSE }

        private fun isUpgradeResponseHandler(method: Method): Boolean =
            (method.parameterTypes.size == 1 && method.parameterTypes[0] == java.io.File::class.java) ||
                (method.name == "onGYNetEnd" && method.parameterTypes.size >= 5)

        private fun isManualUpdaterMethod(method: Method): Boolean =
            method.parameterTypes.size == 2 && method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].name == TINKER_SYNC_RESPONSE && method.returnType == Void.TYPE

        private fun blockedReturnValue(method: Method): Any? = when (method.returnType) {
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> 0
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> false
            java.lang.Long.TYPE, java.lang.Long::class.java -> 0L
            else -> null
        }

        private fun loaderKey(classLoader: ClassLoader): String =
            "${System.identityHashCode(classLoader)}:${classLoader}"

        private val LATE_ENTRIES = listOf(
            "sync_response_consumer",
            "upgrade_response",
            "manual_updater",
            "legacy_patch_service"
        )
    }
}
