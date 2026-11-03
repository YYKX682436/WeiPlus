package h.Hchat.hooks.core

import h.Hchat.event.Events
import h.Hchat.hooks.items.conversationgroup.ConversationGroupHomeProjection
import h.Hchat.utils.HLog
import java.util.Collections
import java.util.LinkedHashSet

/** Installs feature modules after core APIs are ready and tracks their hook ownership. */
class FeatureManager {
    private val stateLock = Any()
    private val features = ArrayList<Feature>()
    private val lifecycleFeatures = ArrayList<Feature>()
    private val installedFeatures = ArrayList<Feature>()
    private val pendingRevalidations = LinkedHashSet<String>()
    @Volatile private var lifecycleInstalled = false
    private var installing = false
    private var lifecycleGeneration = 0L
    @Volatile private var currentContext: FeatureContext? = null

    fun register(feature: Feature?): FeatureManager {
        if (feature != null) synchronized(stateLock) { features.add(feature) }
        return this
    }

    fun features(): List<Feature> = synchronized(stateLock) {
        Collections.unmodifiableList(features.toList())
    }

    fun installedFeatures(): List<Feature> = synchronized(stateLock) {
        Collections.unmodifiableList(installedFeatures.toList())
    }

    /** Returns false when a required core feature failed and may be retried by the caller. */
    fun installAll(context: FeatureContext): Boolean {
        val lifecycleToken = synchronized(stateLock) {
            if (lifecycleInstalled) return true
            if (installing) return false
            installing = true
            ++lifecycleGeneration
        }
        val installationGeneration = HookRegistry.get().installationGeneration()
        currentContext = context
        try {
            val registered = features()
            for (feature in registered) {
                if (!isLifecycleCurrent(lifecycleToken)) return false
                if (synchronized(stateLock) { lifecycleFeatures.contains(feature) }) continue
                val displayName = safeName(feature)
                val owner = safeFeatureId(feature)
                try {
                    HookRegistry.get().withInstallationGeneration(installationGeneration) {
                        HookRegistry.get().withFeature(owner) { feature.onInit(context) }
                    }
                    val retained = synchronized(stateLock) {
                        if (installing && lifecycleGeneration == lifecycleToken) {
                            lifecycleFeatures.add(feature)
                            true
                        } else false
                    }
                    if (!retained) {
                        runCatching { feature.onDestroy(context) }
                        return false
                    }
                } catch (failure: Throwable) {
                    HLog.e("$TAG onInit 失败: $displayName, error=$failure", failure)
                    runCatching { feature.onDestroy(context) }
                    if (isRequiredCore(feature)) return false
                }
            }

            for (feature in registered) {
                if (!isLifecycleCurrent(lifecycleToken)) return false
                val initialized = synchronized(stateLock) { lifecycleFeatures.contains(feature) }
                if (!initialized || synchronized(stateLock) { installedFeatures.contains(feature) }) continue
                val displayName = safeName(feature)
                val policy = try {
                    feature.installationPolicy()
                } catch (failure: Throwable) {
                    HLog.e("$TAG installationPolicy 异常，安全跳过: $displayName", failure)
                    if (isRequiredCore(feature)) return false
                    continue
                }
                val shouldInstall = FeatureInstallDecision.shouldInstall(
                    enabledOnly = policy == Feature.InstallationPolicy.ENABLED_ONLY,
                    isEnabled = { feature.isEnabled(context) },
                    onEnabledCheckError = { HLog.e("$TAG isEnabled 检查异常，安全跳过: $displayName", it) }
                )
                if (!shouldInstall) {
                    if (isRequiredCore(feature)) {
                        HLog.e("$TAG 必需核心功能未启用，停止后续初始化: $displayName")
                        return false
                    }
                    continue
                }

                val owner = safeFeatureId(feature)
                try {
                    HookRegistry.get().withInstallationGeneration(installationGeneration) {
                        HookRegistry.get().withFeature(owner) { feature.install(context) }
                    }
                    val retained = synchronized(stateLock) {
                        if (installing && lifecycleGeneration == lifecycleToken) {
                            installedFeatures.add(feature)
                            true
                        } else false
                    }
                    if (!retained) return false
                    runCatching { context.eventBus().post(Events.FeatureInstalled(displayName)) }
                } catch (failure: Throwable) {
                    HLog.e("$TAG 功能安装失败: $displayName, error=$failure", failure)
                    synchronized(stateLock) {
                        installedFeatures.remove(feature)
                        lifecycleFeatures.remove(feature)
                    }
                    runCatching { feature.onDestroy(context) }
                    if (isRequiredCore(feature)) return false
                }
            }

            if (!isLifecycleCurrent(lifecycleToken)) return false
            synchronized(stateLock) {
                if (!installing || lifecycleGeneration != lifecycleToken) return false
                lifecycleInstalled = true
                installing = false
            }
            DexInstallScheduler.markDexBridgeReady()
            return true
        } finally {
            synchronized(stateLock) {
                if (lifecycleGeneration == lifecycleToken && !lifecycleInstalled) installing = false
            }
        }
    }

    /** Reinstalls only features whose hooked classes changed, plus the shared locator API. */
    fun revalidateRuntime(context: FeatureContext, classLoader: ClassLoader, refreshCoreApi: Boolean): Boolean {
        val previousContext = currentContext ?: context
        val allFeatures = features()
        val featureById = allFeatures.associateBy(::safeFeatureId)
        val affectedByClass = HookRegistry.get().featuresWithChangedClasses(classLoader)
        val pendingTaskOwners = DexInstallScheduler.pendingFeatureOwners()
        val targets = synchronized(stateLock) {
            currentContext = context
            if (!lifecycleInstalled) return true
            pendingRevalidations.addAll(affectedByClass)
            pendingRevalidations.addAll(pendingTaskOwners)
            if (refreshCoreApi) {
                pendingRevalidations.add(CORE_API_ID)
                // Business features own leases to the currently published API objects.
                // Rebind them only when the shared API generation is actually replaced.
                pendingRevalidations.addAll(installedFeatures
                    .asSequence()
                    .map(::safeFeatureId)
                    .filter { it != CORE_API_ID }
                    .toList())
            }
            pendingRevalidations.removeAll { id ->
                featureById[id] == null || installedFeatures.none { safeFeatureId(it) == id }
            }
            pendingRevalidations.toList().sortedBy { if (it == CORE_API_ID) 0 else 1 }
        }
        if (targets.isEmpty()) return true

        val generation = HookRegistry.get().installationGeneration()
        for (featureId in targets) {
            val feature = featureById[featureId] ?: continue
            val displayName = safeName(feature)
            val requiredCore = isRequiredCore(feature)
            DexInstallScheduler.cancelFeature(featureId)
            HookRegistry.get().unhookFeature(featureId)
            try {
                if (!requiredCore) {
                    runCatching { feature.onDestroy(previousContext) }
                        .onFailure { HLog.e("$TAG 运行时代码复核前清理失败: $displayName", it) }
                }
                HookRegistry.get().withInstallationGeneration(generation) {
                    HookRegistry.get().withFeature(featureId) {
                        feature.onInit(context)
                        feature.install(context)
                    }
                }
                synchronized(stateLock) { pendingRevalidations.remove(featureId) }
            } catch (failure: Throwable) {
                HLog.e("$TAG 运行时代码复核失败，单独停用功能: $displayName, error=$failure", failure)
                HookRegistry.get().unhookFeature(featureId)
                runCatching { feature.onDestroy(context) }
                if (requiredCore) return false
            }
        }
        return synchronized(stateLock) { pendingRevalidations.isEmpty() }
    }

    fun destroyAll(context: FeatureContext) {
        val featuresToDestroy = synchronized(stateLock) {
            lifecycleInstalled = false
            installing = false
            ++lifecycleGeneration
            currentContext = null
            pendingRevalidations.clear()
            val featureSnapshot = lifecycleFeatures.toList().asReversed()
            lifecycleFeatures.clear()
            installedFeatures.clear()
            featureSnapshot
        }
        DexInstallScheduler.cancelAll()
        // Revoke the process generation before waiting for any background locator work.
        HookRegistry.get().unhookAll()
        for (feature in featuresToDestroy) {
            try {
                feature.onDestroy(context)
            } catch (failure: Throwable) {
                HLog.e("$TAG onDestroy 异常: ${safeName(feature)}, error=$failure", failure)
            }
        }
        ConversationGroupHomeProjection.resetAfterUnhookAll()
    }

    private fun isLifecycleCurrent(token: Long): Boolean = synchronized(stateLock) {
        lifecycleGeneration == token && (installing || lifecycleInstalled)
    }

    private fun isRequiredCore(feature: Feature): Boolean =
        runCatching { feature.isRequiredCore() }.getOrDefault(safeFeatureId(feature) == CORE_API_ID)

    private fun safeFeatureId(feature: Feature): String = try {
        feature.featureId().takeIf { it.isNotBlank() } ?: feature.javaClass.name
    } catch (_: Throwable) {
        feature.javaClass.name
    }

    private fun safeName(feature: Feature): String = try {
        feature.name().takeIf { it.isNotBlank() } ?: feature.javaClass.name
    } catch (_: Throwable) {
        feature.javaClass.name
    }

    companion object {
        private const val TAG = "[Hchat:FeatureManager]"
        private const val CORE_API_ID = "wechat_api"
    }
}
