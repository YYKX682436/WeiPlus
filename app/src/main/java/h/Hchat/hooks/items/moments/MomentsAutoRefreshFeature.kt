package h.Hchat.hooks.items.moments

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.preferences.HchatStorage
import java.util.TimeZone
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MomentsAutoRefreshFeature : BaseFeature() {
    private var runtime: MomentsAutoRefreshRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "朋友圈自动刷新"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MomentsAutoRefreshSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = MomentsAutoRefreshRuntime(context, ::logError)
        DexInstallScheduler.schedule(
            "$ID:runtime",
            name(),
            DexInstallScheduler.Stage.WARMUP
        ) {
            val current = runtime ?: return@schedule false
            current.start()
            true
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object {
        const val ID = "moments_auto_refresh"
    }
}

private class MomentsAutoRefreshRuntime(
    context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val hostContext = context.hostContext().applicationContext ?: context.hostContext()
    private val prefs = HchatStorage.preferences(hostContext, MomentsAutoRefreshSettings.PREFS_NAME)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in CONFIG_KEYS) reconfigure()
    }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = scheduler.clockChanged()
    }
    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = scheduler.clockChanged()
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    private val scheduler = MomentsRefreshScheduler(
        object : MomentsRefreshScheduler.Clock {
            override fun now(): Long = System.currentTimeMillis()
            override fun elapsed(): Long = SystemClock.elapsedRealtime()
            override fun timeZone(): TimeZone = TimeZone.getDefault()
        },
        MomentsRefreshScheduler.TimerFactory {
            val executor = ScheduledThreadPoolExecutor(1) { runnable ->
                Thread(runnable, "Hchat-MomentsAutoRefresh").apply { isDaemon = true }
            }.apply { removeOnCancelPolicy = true }
            object : MomentsRefreshScheduler.Timer {
                override fun schedule(task: Runnable, delayMillis: Long): MomentsRefreshScheduler.Cancel {
                    val future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS)
                    return MomentsRefreshScheduler.Cancel { future.cancel(false) }
                }
                override fun shutdown() { executor.shutdownNow() }
            }
        },
        object : MomentsRefreshScheduler.Refresh {
            override fun refresh(): Boolean = WeChatApis.snsApi()?.refreshTimeline() == true
            override fun result(time: Long, success: Boolean, error: Throwable?) {
                val result = when {
                    error != null -> "刷新异常：${error.message.orEmpty()}"
                    success -> "刷新请求已提交"
                    else -> "刷新请求提交失败"
                }
                prefs.edit()
                    .putLong(MomentsAutoRefreshSettings.KEY_LAST_TIME, time)
                    .putString(MomentsAutoRefreshSettings.KEY_LAST_RESULT, result)
                    .apply()
                if (error != null) logger("朋友圈自动刷新失败", error)
            }
        }
    )
    private var started = false
    private var destroyed = false
    private var receiverRegistered = false
    private var lifecycleRegistered = false

    @Synchronized
    fun start() {
        if (started || destroyed) return
        started = true
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        reconfigure()
    }

    private fun registerClockObservers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (!receiverRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hostContext.registerReceiver(clockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    hostContext.registerReceiver(clockReceiver, filter)
                }
                receiverRegistered = true
            }.onFailure { logger("朋友圈自动刷新时间监听注册失败", it) }
        }
        if (!lifecycleRegistered) {
            (hostContext as? Application)?.let { application ->
                runCatching {
                    application.registerActivityLifecycleCallbacks(activityCallbacks)
                    lifecycleRegistered = true
                }.onFailure { logger("朋友圈自动刷新前台监听注册失败", it) }
            }
        }
    }

    @Synchronized
    fun destroy() {
        if (destroyed) return
        destroyed = true
        if (started) prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        scheduler.destroy()
        unregisterClockObservers()
    }

    private fun unregisterClockObservers() {
        if (receiverRegistered) {
            runCatching {
                hostContext.unregisterReceiver(clockReceiver)
                receiverRegistered = false
            }.onFailure { logger("朋友圈自动刷新时间监听注销失败", it) }
        }
        if (lifecycleRegistered) {
            runCatching {
                (hostContext as Application).unregisterActivityLifecycleCallbacks(activityCallbacks)
                lifecycleRegistered = false
            }.onFailure { logger("朋友圈自动刷新前台监听注销失败", it) }
        }
    }

    @Synchronized
    private fun reconfigure() {
        if (destroyed) return
        val enabled = prefs.getBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, false)
        if (enabled) registerClockObservers()
        scheduler.configure(
            MomentsRefreshScheduler.Config(
                enabled,
                prefs.getInt(
                    MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS,
                    MomentsAutoRefreshSettings.DEFAULT_INTERVAL_SECONDS
                ).coerceAtLeast(0) * 1000L,
                prefs.getBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, false),
                prefs.getString(MomentsAutoRefreshSettings.KEY_START_TIME, MomentsAutoRefreshSettings.DEFAULT_START_TIME),
                prefs.getString(MomentsAutoRefreshSettings.KEY_END_TIME, MomentsAutoRefreshSettings.DEFAULT_END_TIME)
            )
        )
        if (!enabled) unregisterClockObservers()
    }

    companion object {
        private val CONFIG_KEYS = setOf(
            MomentsAutoRefreshSettings.KEY_ENABLE,
            MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS,
            MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE,
            MomentsAutoRefreshSettings.KEY_START_TIME,
            MomentsAutoRefreshSettings.KEY_END_TIME
        )
    }
}
