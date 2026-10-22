package h.Hchat.hooks.items.backgroundassist

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import h.Hchat.WechatPackage
import h.Hchat.preferences.HchatStorage
import h.Hchat.utils.HLog
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object BackgroundRunAssistRuntime {
    private const val TAG = "[Hchat:BackgroundRunAssist]"
    private const val ROOT_STATE_PREFS = "Hchat_wechat_keep_alive_root_state"
    private const val WAKE_LOCK_TAG = "Hchat:BackgroundRunAssist"

    private val generations = ConcurrentHashMap<String, AtomicLong>()
    private val rootExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2),
        ThreadFactory { runnable -> Thread(runnable, "Hchat-BackgroundAssist-Root").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val rootQueue = LatestRootOptionsQueue(
        rootExecutor,
        RootSettingsController(RootShellCommandRunner())
    ) { hostPackage, failure ->
        HLog.e("$TAG Root 状态调整失败，原状态快照已保留: $hostPackage (${failure.message})", failure)
    }
    private val wakeLocks = WakeLockLeaseRegistry()

    fun apply(hostContext: Context?) {
        if (hostContext == null) return
        val appContext = hostContext.applicationContext ?: hostContext
        val hostPackage = appContext.packageName
        if (!WechatPackage.isTarget(hostPackage)) return
        val generation = nextGeneration(hostPackage)
        val prefs = HchatStorage.preferences(appContext, BackgroundRunAssistSettings.PREFS_NAME)
        val enabled = prefs.getBoolean(BackgroundRunAssistSettings.KEY_ENABLE, BackgroundRunAssistSettings.DEFAULT_ENABLE)
        val keepWakeLock = enabled && prefs.getBoolean(
            BackgroundRunAssistSettings.KEY_WAKE_LOCK,
            BackgroundRunAssistSettings.DEFAULT_WAKE_LOCK
        )
        val dozeWhitelist = enabled && prefs.getBoolean(
            BackgroundRunAssistSettings.KEY_ROOT_DOZE_WHITELIST,
            BackgroundRunAssistSettings.DEFAULT_ROOT_DOZE_WHITELIST
        )
        val appOps = enabled && prefs.getBoolean(
            BackgroundRunAssistSettings.KEY_ROOT_APP_OPS,
            BackgroundRunAssistSettings.DEFAULT_ROOT_APP_OPS
        )
        val wakeLockApplied = wakeLocks.setDesired(hostPackage, generation, keepWakeLock) {
            newWakeLock(appContext)
        }
        if (!wakeLockApplied) HLog.e("$TAG WakeLock 未能创建或持有: $hostPackage")
        submitRootState(hostPackage, generation, RootRunOptions(dozeWhitelist, appOps), appContext)
    }

    fun stop(hostContext: Context?) {
        if (hostContext == null) return
        val appContext = hostContext.applicationContext ?: hostContext
        val hostPackage = appContext.packageName
        if (!WechatPackage.isTarget(hostPackage)) return
        val generation = nextGeneration(hostPackage)
        wakeLocks.setDesired(hostPackage, generation, false)
        submitRootState(hostPackage, generation, RootRunOptions(false, false), appContext)
    }

    fun isIgnoringBatteryOptimizations(context: Context?): Boolean {
        if (context == null || !WechatPackage.isTarget(context.packageName)) return false
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
    }

    private fun submitRootState(
        hostPackage: String,
        generation: Long,
        options: RootRunOptions,
        context: Context
    ) {
        val accepted = rootQueue.submit(hostPackage, generation, options) {
            SharedPreferencesRootStateStore(context.getSharedPreferences(ROOT_STATE_PREFS, Context.MODE_PRIVATE))
        }
        if (!accepted) HLog.e("$TAG Root 工作队列暂不可用，最新状态将在下次配置变化时重试: $hostPackage")
    }

    private fun nextGeneration(hostPackage: String): Long =
        generations.computeIfAbsent(hostPackage) { AtomicLong() }.incrementAndGet()

    private fun newWakeLock(context: Context): TimedWakeLockPort {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: throw IllegalStateException("PowerManager unavailable")
        @Suppress("DEPRECATION")
        val platformLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        return object : TimedWakeLockPort {
            override val isHeld: Boolean get() = platformLock.isHeld
            override fun acquire(timeoutMillis: Long) = platformLock.acquire(timeoutMillis)
            override fun release() = platformLock.release()
        }
    }

    private class SharedPreferencesRootStateStore(private val preferences: SharedPreferences) : RootStateStore {
        override fun contains(key: String): Boolean = preferences.contains(key)
        override fun getBoolean(key: String, fallback: Boolean): Boolean = preferences.getBoolean(key, fallback)
        override fun getString(key: String): String? = runCatching { preferences.getString(key, null) }.getOrNull()

        override fun commit(changes: Map<String, Any?>): Boolean {
            val editor = preferences.edit()
            changes.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    else -> return false
                }
            }
            return editor.commit()
        }
    }
}
