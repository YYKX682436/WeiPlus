package h.Hchat.hooks.core

import h.Hchat.event.Events
import h.Hchat.hooks.items.conversationgroup.ConversationGroupHomeProjection
import h.Hchat.utils.HLog
import java.util.Collections

/**
 * 功能模块调度器。
 */
class FeatureManager {
    private val stateLock = Any()
    private val features = ArrayList<Feature>()
    private val lifecycleFeatures = ArrayList<Feature>()
    private val installedFeatures = ArrayList<Feature>()
    @Volatile private var lifecycleInstalled = false
    private var lifecycleGeneration = 0L

    fun register(feature: Feature?): FeatureManager {
        if (feature != null) {
            synchronized(stateLock) { features.add(feature) }
        }
        return this
    }

    fun features(): List<Feature> = synchronized(stateLock) { Collections.unmodifiableList(features.toList()) }

    fun installedFeatures(): List<Feature> = synchronized(stateLock) {
        Collections.unmodifiableList(installedFeatures.toList())
    }

    fun installAll(context: FeatureContext) {
        val lifecycleToken = synchronized(stateLock) {
            if (lifecycleInstalled) return
            lifecycleInstalled = true
            ++lifecycleGeneration
        }
        val installationGeneration = HookRegistry.get().installationGeneration()
        val initializedFeatures = ArrayList<Feature>()
        for (feature in features()) {
            if (!isLifecycleCurrent(lifecycleToken)) return
            val displayName = safeName(feature)
            try {
                feature.onInit(context)
                initializedFeatures.add(feature)
                val retained = synchronized(stateLock) {
                    if (lifecycleInstalled && lifecycleGeneration == lifecycleToken) {
                        lifecycleFeatures.add(feature)
                        true
                    } else false
                }
                if (!retained) {
                    runCatching { feature.onDestroy(context) }
                    return
                }
            } catch (e: Throwable) {
                HLog.e("$TAG onInit 失败: $displayName, error=$e", e)
                runCatching { feature.onDestroy(context) }
            }
        }

        for (feature in initializedFeatures) {
            if (!isLifecycleCurrent(lifecycleToken)) return
            val displayName = safeName(feature)
            val policy = try {
                feature.installationPolicy()
            } catch (e: Throwable) {
                HLog.e("$TAG installationPolicy 异常，安全跳过: $displayName", e)
                continue
            }
            val shouldInstall = FeatureInstallDecision.shouldInstall(
                enabledOnly = policy == Feature.InstallationPolicy.ENABLED_ONLY,
                isEnabled = { feature.isEnabled(context) },
                onEnabledCheckError = { HLog.e("$TAG isEnabled 检查异常，安全跳过: $displayName", it) }
            )
            if (!shouldInstall) continue

            try {
                HookRegistry.get().withInstallationGeneration(installationGeneration) {
                    feature.install(context)
                }
                val retained = synchronized(stateLock) {
                    if (lifecycleInstalled && lifecycleGeneration == lifecycleToken) {
                        installedFeatures.add(feature)
                        true
                    } else false
                }
                if (!retained) return
                try {
                    context.eventBus().post(Events.FeatureInstalled(displayName))
                } catch (_: Throwable) {
                }
            } catch (e: Throwable) {
                HLog.e("$TAG 功能安装失败: $displayName, error=$e", e)
                val shouldDestroy = synchronized(stateLock) {
                    installedFeatures.remove(feature)
                    lifecycleFeatures.remove(feature)
                }
                if (shouldDestroy) runCatching { feature.onDestroy(context) }
            }
        }
        if (!isLifecycleCurrent(lifecycleToken)) return
        if (isLifecycleCurrent(lifecycleToken)) DexInstallScheduler.markDexBridgeReady()
    }

    fun destroyAll(context: FeatureContext) {
        val featuresToDestroy = synchronized(stateLock) {
            lifecycleInstalled = false
            ++lifecycleGeneration
            val featureSnapshot = lifecycleFeatures.toList().asReversed()
            lifecycleFeatures.clear()
            installedFeatures.clear()
            featureSnapshot
        }
        DexInstallScheduler.cancelAll()
        // 先使旧安装租约失效，不等待后台定位，也不允许销毁后重新挂钩。
        HookRegistry.get().unhookAll()
        for (feature in featuresToDestroy) {
            try {
                feature.onDestroy(context)
            } catch (e: Throwable) {
                HLog.e("$TAG onDestroy 异常: ${safeName(feature)}, error=$e", e)
            }
        }
        // 首页投影由聊天分组与标签栏共享，只在全量卸载后复位。
        ConversationGroupHomeProjection.resetAfterUnhookAll()
    }

    private fun isLifecycleCurrent(token: Long): Boolean =
        lifecycleInstalled && synchronized(stateLock) { lifecycleInstalled && lifecycleGeneration == token }

    private fun safeName(feature: Feature): String {
        return try {
            val name = feature.name()
            if (name.isEmpty()) feature.javaClass.name else name
        } catch (_: Throwable) {
            feature.javaClass.name
        }
    }

    companion object {
        private const val TAG = "[Hchat:FeatureManager]"
    }
}
