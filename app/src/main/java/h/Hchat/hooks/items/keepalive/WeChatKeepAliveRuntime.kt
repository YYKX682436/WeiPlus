package h.Hchat.hooks.items.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import h.Hchat.BuildConfig
import h.Hchat.WechatPackage
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WeChatKeepAliveRuntime {
    private const val TAG = "[Hchat:KeepAlive]"
    private const val WAKE_LOCK_TAG = "Hchat:WeChatKeepAlive"
    private const val LAUNCH_COOLDOWN_MS = 180_000L
    private val wakeLocks = ConcurrentHashMap<String, PowerManager.WakeLock>()
    private val lastLaunchAttempt = ConcurrentHashMap<String, Long>()
    private val bindings = ConcurrentHashMap<String, TargetBinding>()
    private val disabledTargets = ConcurrentHashMap.newKeySet<String>()
    private val rootCommandExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Hchat-KeepAlive-Root").apply { isDaemon = true }
    }

    fun apply(
        hostContext: Context?,
        moduleContext: Context? = null,
        allowForegroundStart: Boolean = false
    ) {
        if (hostContext == null) return
        val appContext = hostContext.applicationContext ?: hostContext
        val hostPackage = appContext.packageName
        if (!WechatPackage.isTarget(hostPackage)) return
        val serviceContext = (moduleContext ?: resolveModuleContext(appContext))?.let { it.applicationContext ?: it }
        val prefs = HchatStorage.preferences(appContext, WeChatKeepAliveSettings.PREFS_NAME)
        val enabled = prefs.getBoolean(WeChatKeepAliveSettings.KEY_ENABLE, WeChatKeepAliveSettings.DEFAULT_ENABLE)
        val rootDozeWhitelist = enabled && prefs.getBoolean(
            WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST,
            WeChatKeepAliveSettings.DEFAULT_ROOT_DOZE_WHITELIST
        )
        val rootAppOps = enabled && prefs.getBoolean(
            WeChatKeepAliveSettings.KEY_ROOT_APP_OPS,
            WeChatKeepAliveSettings.DEFAULT_ROOT_APP_OPS
        )
        applyRootOptions(hostPackage, serviceContext ?: appContext, rootDozeWhitelist, rootAppOps)
        if (!enabled) {
            releaseWakeLock(hostPackage)
            stopTarget(hostPackage, appContext, serviceContext)
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
        if (serviceOptions == null || serviceContext == null) {
            if (serviceOptions != null) HLog.e("$TAG 模块 Context 不可用，跳过绑定服务")
            stopTarget(hostPackage, appContext, serviceContext)
        } else {
            disabledTargets.remove(hostPackage)
            updateTarget(hostPackage, appContext, serviceOptions, allowForegroundStart)
        }

    }

    fun stop(hostContext: Context?, moduleContext: Context? = null) {
        if (hostContext == null) return
        val appContext = hostContext.applicationContext ?: hostContext
        val hostPackage = appContext.packageName
        if (!WechatPackage.isTarget(hostPackage)) return
        stopTarget(hostPackage, appContext, moduleContext?.applicationContext ?: moduleContext)
        releaseWakeLock(hostPackage)
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
        if (!WechatPackage.isTarget(hostPackage) || moduleContext == null || !enabled) return
        launchWeChatIfNeeded(moduleContext.applicationContext ?: moduleContext, hostPackage!!)
    }

    /** Runs on the keep-alive worker so one slow request does not allocate a thread each minute. */
    fun heartbeatTick(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val connection = java.net.URL(HEARTBEAT_URL).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = HEARTBEAT_TIMEOUT_MS
                connection.readTimeout = HEARTBEAT_TIMEOUT_MS
                connection.useCaches = false
                connection.requestMethod = "GET"
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            HLog.e("$TAG 网络心跳失败: ${it.message}")
        }
    }

    private fun updateTarget(
        hostPackage: String,
        hostContext: Context,
        options: WeChatKeepAliveTargetOptions,
        allowForegroundStart: Boolean
    ) {
        val binding = bindings.computeIfAbsent(hostPackage) {
            TargetBinding(hostPackage, hostContext.applicationContext ?: hostContext)
        }
        binding.update(options, allowForegroundStart)
    }

    private fun stopTarget(hostPackage: String, hostContext: Context, moduleContext: Context?) {
        val existing = bindings[hostPackage]
        if (existing != null) {
            disabledTargets.add(hostPackage)
            existing.update(null, false)
            return
        }

        // A process can re-enter with the setting off while another target keeps the shared
        // service alive. Bind once to remove only this package; never start an FGS to stop it.
        if (moduleContext == null || !disabledTargets.add(hostPackage)) return
        val binding = TargetBinding(hostPackage, hostContext.applicationContext ?: hostContext)
        val raceWinner = bindings.putIfAbsent(hostPackage, binding)
        if (raceWinner == null) {
            binding.update(null, false)
        } else {
            raceWinner.update(null, false)
        }
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

    private fun applyRootOptions(
        hostPackage: String,
        stateContext: Context,
        dozeWhitelist: Boolean,
        appOps: Boolean
    ) {
        if (!WechatPackage.isTarget(hostPackage)) return
        rootCommandExecutor.execute {
            val state = stateContext.getSharedPreferences(ROOT_STATE_PREFS, Context.MODE_PRIVATE)
            if (dozeWhitelist || state.contains("doze_original_$hostPackage")) {
                applyRootDozeWhitelist(hostPackage, dozeWhitelist, state)
            }
            if (appOps || state.getBoolean("appops_snapshot_$hostPackage", false)) {
                applyRootAppOps(hostPackage, appOps, state)
            }
        }
    }

    /** Captures pre-existing state and restores it only while the setting is still ours. */
    private fun applyRootDozeWhitelist(
        hostPackage: String,
        enabled: Boolean,
        state: android.content.SharedPreferences
    ) {
        val key = "doze_original_$hostPackage"
        val currentOutput = runRootCommandOutput("cmd deviceidle whitelist")
        if (currentOutput == null) {
            HLog.e("$TAG 无法读取 Root Doze 白名单: $hostPackage")
            return
        }
        val isWhitelisted = WeChatKeepAliveRootState.isPackageWhitelisted(currentOutput, hostPackage)
        if (enabled) {
            if (!state.contains(key)) {
                if (!state.edit().putBoolean(key, isWhitelisted).commit()) {
                    HLog.e("$TAG 无法保存 Root Doze 白名单原状态，未更改: $hostPackage")
                    return
                }
            }
            if (!isWhitelisted && !runRootCommand("cmd deviceidle whitelist +$hostPackage")) {
                HLog.e("$TAG Root Doze 白名单执行失败: $hostPackage")
            }
        } else if (state.contains(key)) {
            val wasWhitelisted = state.getBoolean(key, false)
            val restored = !WeChatKeepAliveRootState.dozeWhitelistShouldBeRemoved(wasWhitelisted, isWhitelisted) ||
                runRootCommand("cmd deviceidle whitelist -$hostPackage")
            if (restored) state.edit().remove(key).commit()
            else HLog.e("$TAG Root Doze 白名单恢复失败: $hostPackage")
        }
    }

    private fun applyRootAppOps(
        hostPackage: String,
        enabled: Boolean,
        state: android.content.SharedPreferences
    ) {
        val snapshotKey = "appops_snapshot_$hostPackage"
        val modes = APP_OPS.associateWith { op -> "appops_original_${hostPackage}_$op" }
        if (enabled) {
            if (!state.getBoolean(snapshotKey, false)) {
                val initialModes = APP_OPS.mapNotNull { op ->
                    readAppOpMode(hostPackage, op)?.let { op to it }
                }.toMap()
                if (initialModes.size != APP_OPS.size) {
                    HLog.e("$TAG 无法完整读取 Root AppOps，未更改: $hostPackage")
                    return
                }
                val editor = state.edit().putBoolean(snapshotKey, true)
                initialModes.forEach { (op, mode) -> editor.putString(modes.getValue(op), mode) }
                if (!editor.commit()) {
                    HLog.e("$TAG 无法保存 Root AppOps 原状态，未更改: $hostPackage")
                    return
                }
            }
            APP_OPS.forEach { op ->
                if (!runRootCommand("cmd appops set $hostPackage $op allow")) {
                    HLog.e("$TAG Root AppOps 放行失败: $hostPackage $op")
                }
            }
            return
        }

        if (!state.getBoolean(snapshotKey, false)) return
        var restored = true
        APP_OPS.forEach { op ->
            val currentMode = readAppOpMode(hostPackage, op)
            val originalMode = state.getString(modes.getValue(op), null)
            if (currentMode == null || originalMode == null) {
                restored = false
            } else {
                val modeToRestore = WeChatKeepAliveRootState.appOpModeToRestore(currentMode, originalMode)
                if (modeToRestore != null && !runRootCommand("cmd appops set $hostPackage $op $modeToRestore")) {
                    restored = false
                }
            }
        }
        if (restored) {
            val editor = state.edit().remove(snapshotKey)
            modes.values.forEach(editor::remove)
            editor.commit()
        } else {
            HLog.e("$TAG Root AppOps 原状态恢复不完整: $hostPackage")
        }
    }

    private fun readAppOpMode(hostPackage: String, operation: String): String? {
        val output = runRootCommandOutput("cmd appops get $hostPackage $operation") ?: return null
        return WeChatKeepAliveRootState.parseAppOpMode(output, operation)
    }

    private fun runRootCommandOutput(command: String): String? = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = process.waitFor(ROOT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        val output = if (finished) process.inputStream.bufferedReader().use { it.readText() } else null
        if (finished && process.exitValue() == 0) output else null
    }.getOrNull()

    private fun runRootCommand(command: String): Boolean = runRootCommandOutput(command) != null

    private fun resolveModuleContext(context: Context): Context? = runCatching {
        context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()

    private fun runRootLaunch(hostPackage: String): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        val command = "am start --user current -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -p $hostPackage"
        return runRootCommand(command)
    }

    private fun launchWeChatIfNeeded(moduleContext: Context, hostPackage: String) {
        if (!WechatPackage.isTarget(hostPackage)) return
        runCatching {
            val alive = isWeChatProcessAlive(hostPackage)
            if (!WeChatKeepAliveTargetState.shouldLaunch(hostPackage, alive)) return
            val now = System.currentTimeMillis()
            val previous = lastLaunchAttempt[hostPackage] ?: 0L
            if (now - previous < LAUNCH_COOLDOWN_MS) return
            lastLaunchAttempt[hostPackage] = now

            if (runRootLaunch(hostPackage)) {
                HLog.e("$TAG Root 看门狗已请求启动: $hostPackage")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                moduleContext.packageManager.getLaunchIntentSenderForPackage(hostPackage)
                    .sendIntent(moduleContext, 0, null, null, null)
            } else {
                @Suppress("DEPRECATION")
                val intent = moduleContext.packageManager.getLaunchIntentForPackage(hostPackage)
                    ?: throw IllegalStateException("目标没有可启动 Activity")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                moduleContext.startActivity(intent)
            }
        }.onFailure {
            HLog.e("$TAG 看门狗启动受 Android 后台启动限制或目标不可见: $hostPackage (${it.message})", it)
        }
    }

    private fun isWeChatProcessAlive(hostPackage: String): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        return runCatching {
            val process = ProcessBuilder("pidof", hostPackage).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private class TargetBinding(
        private val hostPackage: String,
        private val hostContext: Context
    ) {
        private val lock = Any()
        private var desiredOptions: WeChatKeepAliveTargetOptions? = null
        private var allowStart = false
        private var bound = false
        private var closed = false
        @Volatile private var control: IWeChatKeepAliveControl? = null

        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val connected = IWeChatKeepAliveControl.Stub.asInterface(binder)
                synchronized(lock) {
                    if (closed) {
                        runCatching { connected?.removeTarget(hostPackage) }
                        unbindLocked()
                        WeChatKeepAliveRuntime.bindings.remove(hostPackage, this@TargetBinding)
                        return
                    }
                    control = connected
                }
                synchronizeRemote()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                control = null
                synchronized(lock) { allowStart = false }
            }

            override fun onBindingDied(name: ComponentName?) {
                control = null
                synchronized(lock) {
                    allowStart = false
                    unbindLocked()
                }
                WeChatKeepAliveRuntime.bindings.remove(hostPackage, this@TargetBinding)
                if (desiredOptions != null) bindAgain()
            }

            override fun onNullBinding(name: ComponentName?) {
                HLog.e("${WeChatKeepAliveRuntime.TAG} 服务没有提供 Binder: $hostPackage")
                synchronized(lock) { unbindLocked() }
                WeChatKeepAliveRuntime.bindings.remove(hostPackage, this@TargetBinding)
                WeChatKeepAliveRuntime.disabledTargets.remove(hostPackage)
            }
        }

        fun update(options: WeChatKeepAliveTargetOptions?, allowForegroundStart: Boolean) {
            var shouldBind = false
            var shouldSynchronize = false
            synchronized(lock) {
                desiredOptions = options
                allowStart = allowForegroundStart
                if (!bound) {
                    bound = true
                    shouldBind = true
                }
                if (options == null) {
                    closed = true
                    val service = control
                    if (service != null) {
                        runCatching { service.removeTarget(hostPackage) }
                            .onFailure {
                                HLog.e("${WeChatKeepAliveRuntime.TAG} 清除目标状态失败: $hostPackage (${it.message})", it)
                            }
                        control = null
                        unbindLocked()
                        WeChatKeepAliveRuntime.bindings.remove(hostPackage, this)
                    }
                }
                else {
                    closed = false
                    shouldSynchronize = !shouldBind && control != null
                }
            }
            if (shouldBind) bindAgain() else if (shouldSynchronize) synchronizeRemote()
        }

        private fun synchronizeRemote() {
            synchronized(lock) {
                if (closed) return
                val service = control ?: return
                val options = desiredOptions ?: return
                runCatching {
                    val token = service.registerTarget(hostPackage, options.watchdog, options.networkHeartbeat)
                    val requestForegroundStart = allowStart
                    allowStart = false
                    if (token.isNotBlank() && requestForegroundStart) {
                        val intent = WeChatKeepAliveService.promotionIntent(
                            BuildConfig.APPLICATION_ID,
                            WeChatKeepAliveService::class.java.name,
                            token
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            hostContext.startForegroundService(intent)
                        } else {
                            @Suppress("DEPRECATION")
                            hostContext.startService(intent)
                        }
                    }
                }.onFailure {
                    HLog.e("${WeChatKeepAliveRuntime.TAG} 注册目标或启动前台通知失败: $hostPackage (${it.message})", it)
                }
            }
        }

        private fun bindAgain() {
            val intent = Intent().setComponent(
                ComponentName(BuildConfig.APPLICATION_ID, WeChatKeepAliveService::class.java.name)
            )
            val ok = runCatching {
                hostContext.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
                )
            }.onFailure {
                HLog.e("${WeChatKeepAliveRuntime.TAG} 跨 UID 绑定服务失败: $hostPackage (${it.message})", it)
            }
                .getOrDefault(false)
            if (!ok) {
                synchronized(lock) {
                    bound = false
                    control = null
                }
                HLog.e(
                    "${WeChatKeepAliveRuntime.TAG} Android 拒绝跨 UID 绑定或模块包不可见: $hostPackage; " +
                        "仅保留本地 WakeLock/Root 设置，不运行看门狗和网络心跳"
                )
                WeChatKeepAliveRuntime.bindings.remove(hostPackage, this)
                WeChatKeepAliveRuntime.disabledTargets.remove(hostPackage)
            }
        }

        private fun unbindLocked() {
            if (!bound) return
            bound = false
            runCatching { hostContext.unbindService(serviceConnection) }
                .onFailure {
                    HLog.e("${WeChatKeepAliveRuntime.TAG} 解除服务绑定失败: $hostPackage (${it.message})", it)
                }
        }
    }

    private const val HEARTBEAT_URL = "https://connectivitycheck.gstatic.com/generate_204"
    private const val HEARTBEAT_TIMEOUT_MS = 3000
    private const val ROOT_COMMAND_TIMEOUT_SECONDS = 10L
    private const val ROOT_STATE_PREFS = "Hchat_wechat_keep_alive_root_state"
    private val APP_OPS = listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "WAKE_LOCK", "START_FOREGROUND")
}
