package h.Hchat.hooks.items.keepalive

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import h.Hchat.BuildConfig
import h.Hchat.WechatPackage
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog

object WeChatKeepAliveRuntime {
    private const val TAG = "[Hchat:KeepAlive]"
    private const val WAKE_LOCK_TAG = "Hchat:WeChatKeepAlive"
    private val wakeLocks = java.util.concurrent.ConcurrentHashMap<String, PowerManager.WakeLock>()
    private val rootWhitelistApplied = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val rootAppOpsApplied = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun apply(hostContext: Context?, moduleContext: Context? = null) {
        if (hostContext == null) return
        val appContext = hostContext.applicationContext ?: hostContext
        val hostPackage = appContext.packageName
        if (!WechatPackage.isTarget(hostPackage)) return
        val serviceContext = (moduleContext ?: resolveModuleContext(appContext))?.let { it.applicationContext ?: it }
        val prefs = HchatStorage.preferences(appContext, WeChatKeepAliveSettings.PREFS_NAME)
        val enabled = prefs.getBoolean(WeChatKeepAliveSettings.KEY_ENABLE, WeChatKeepAliveSettings.DEFAULT_ENABLE)
        if (!enabled) {
            releaseWakeLock(hostPackage)
            if (serviceContext != null) runCatching { WeChatKeepAliveService.stop(serviceContext, hostPackage) }
            return
        }
        if (prefs.getBoolean(WeChatKeepAliveSettings.KEY_WAKE_LOCK, WeChatKeepAliveSettings.DEFAULT_WAKE_LOCK)) {
            acquireWakeLock(appContext, hostPackage)
        } else {
            releaseWakeLock(hostPackage)
        }
        val serviceOptions = WeChatKeepAliveTargetState.serviceOptions(
            enabled = enabled,
            foregroundService = prefs.getBoolean(
                WeChatKeepAliveSettings.KEY_FOREGROUND_SERVICE,
                WeChatKeepAliveSettings.DEFAULT_FOREGROUND_SERVICE
            ),
            watchdog = prefs.getBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, WeChatKeepAliveSettings.DEFAULT_WATCHDOG),
            networkHeartbeat = prefs.getBoolean(
                WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT,
                WeChatKeepAliveSettings.DEFAULT_NETWORK_HEARTBEAT
            )
        )
        if (serviceContext == null) {
            if (serviceOptions != null) HLog.e("$TAG 模块 Context 不可用，跳过前台服务")
        } else if (serviceOptions != null) {
            runCatching {
                WeChatKeepAliveService.start(
                    serviceContext,
                    hostPackage,
                    serviceOptions.watchdog,
                    serviceOptions.networkHeartbeat
                )
            }.onFailure { HLog.e("$TAG 启动前台服务失败: ${it.message}", it) }
        } else {
            runCatching { WeChatKeepAliveService.stop(serviceContext, hostPackage) }
        }
        if (prefs.getBoolean(WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST, WeChatKeepAliveSettings.DEFAULT_ROOT_DOZE_WHITELIST)) {
            applyRootDozeWhitelistOnce(hostPackage)
        }
        if (prefs.getBoolean(WeChatKeepAliveSettings.KEY_ROOT_APP_OPS, WeChatKeepAliveSettings.DEFAULT_ROOT_APP_OPS)) {
            applyRootAppOpsOnce(hostPackage)
        }
    }

    fun stop(hostPackage: String?, moduleContext: Context? = null) {
        if (!WechatPackage.isTarget(hostPackage)) return
        releaseWakeLock(hostPackage!!)
        val serviceContext = moduleContext?.applicationContext ?: moduleContext
        if (serviceContext != null) {
            runCatching { WeChatKeepAliveService.stop(serviceContext, hostPackage) }
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context?): Boolean {
        if (context == null) return false
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            WechatPackage.isTarget(context.packageName) &&
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
    }

    fun watchdogTick(moduleContext: Context?, hostPackage: String?, enabled: Boolean) {
        if (!WechatPackage.isTarget(hostPackage)) return
        if (moduleContext == null) return
        if (!enabled) return
        val moduleAppContext = moduleContext.applicationContext ?: moduleContext
        launchWeChatIfNeeded(moduleAppContext, hostPackage!!)
    }

    fun heartbeatTick(enabled: Boolean) {
        if (!enabled) return
        Thread {
            runCatching {
                val connection = java.net.URL(HEARTBEAT_URL).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = HEARTBEAT_TIMEOUT_MS
                connection.readTimeout = HEARTBEAT_TIMEOUT_MS
                connection.useCaches = false
                connection.requestMethod = "GET"
                connection.responseCode
                connection.disconnect()
            }.onFailure {
                HLog.e("$TAG 网络心跳失败: ${it.message}")
            }
        }.apply {
            name = "Hchat-KeepAlive-Heartbeat"
            isDaemon = true
        }.start()
    }

    private fun acquireWakeLock(context: Context, hostPackage: String) {
        if (!WechatPackage.isTarget(hostPackage)) return
        val current = wakeLocks[hostPackage]
        if (current?.isHeld == true) return
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
            wakeLocks.put(hostPackage, lock)?.let { old ->
                runCatching { old.takeIf { it.isHeld }?.release() }
            }
        }.onFailure {
            HLog.e("$TAG 获取 WakeLock 失败: ${it.message}", it)
        }
    }

    private fun releaseWakeLock(hostPackage: String) {
        runCatching {
            wakeLocks.remove(hostPackage)?.takeIf { it.isHeld }?.release()
        }.onFailure {
            HLog.e("$TAG 释放 WakeLock 失败: ${it.message}", it)
        }
    }

    private fun applyRootDozeWhitelistOnce(hostPackage: String) {
        if (!WechatPackage.isTarget(hostPackage) || !rootWhitelistApplied.add(hostPackage)) return
        Thread {
            val result = runRootCommand("cmd deviceidle whitelist +$hostPackage")
            if (!result) {
                HLog.e("$TAG Root Doze 白名单执行失败")
                rootWhitelistApplied.remove(hostPackage)
            }
        }.apply {
            name = "Hchat-KeepAlive-RootWhitelist"
            isDaemon = true
        }.start()
    }

    private fun applyRootAppOpsOnce(hostPackage: String) {
        if (!WechatPackage.isTarget(hostPackage) || !rootAppOpsApplied.add(hostPackage)) return
        Thread {
            val commands = listOf(
                "cmd appops set $hostPackage RUN_ANY_IN_BACKGROUND allow",
                "cmd appops set $hostPackage RUN_IN_BACKGROUND allow",
                "cmd appops set $hostPackage WAKE_LOCK allow",
                "cmd appops set $hostPackage START_FOREGROUND allow"
            )
            val ok = commands.all { runRootCommand(it) }
            if (!ok) {
                HLog.e("$TAG Root AppOps 放行执行不完整")
                rootAppOpsApplied.remove(hostPackage)
            }
        }.apply {
            name = "Hchat-KeepAlive-RootAppOps"
            isDaemon = true
        }.start()
    }

    private fun resolveModuleContext(context: Context): Context? {
        return runCatching {
            context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
    }

    private fun runRootCommand(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private fun launchWeChatIfNeeded(moduleContext: Context, hostPackage: String) {
        runCatching {
            val alive = isWeChatProcessAlive(hostPackage)
            if (!WeChatKeepAliveTargetState.shouldLaunch(hostPackage, alive)) return
            val manager = moduleContext.packageManager
            val intent = manager.getLaunchIntentForPackage(hostPackage) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            moduleContext.startActivity(intent)
        }.onFailure {
            HLog.e("$TAG 看门狗拉起微信失败: ${it.message}", it)
        }
    }

    private fun isWeChatProcessAlive(hostPackage: String): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        return runCatching {
            val process = ProcessBuilder("pidof", hostPackage)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private const val HEARTBEAT_URL = "https://connectivitycheck.gstatic.com/generate_204"
    private const val HEARTBEAT_TIMEOUT_MS = 3000
}
