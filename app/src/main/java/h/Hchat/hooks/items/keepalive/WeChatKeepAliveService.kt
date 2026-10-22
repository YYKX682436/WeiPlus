package h.Hchat.hooks.items.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import h.Hchat.WechatPackage
import h.Hchat.R
import h.Hchat.utils.HLog

class WeChatKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val keepAliveTask = object : Runnable {
        override fun run() {
            val targets = readTargets()
            if (targets.isEmpty()) {
                stopSelf()
                return
            }
            targets.forEach { (hostPackage, options) ->
                WeChatKeepAliveRuntime.watchdogTick(this@WeChatKeepAliveService, hostPackage, options.watchdog)
            }
            WeChatKeepAliveRuntime.heartbeatTick(targets.values.any { it.networkHeartbeat })
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return runCatching {
            startForeground(NOTIFICATION_ID, buildNotification())
            applyTargetCommand(intent)
            if (readTargets().isEmpty()) {
                handler.removeCallbacks(keepAliveTask)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            handler.removeCallbacks(keepAliveTask)
            handler.postDelayed(keepAliveTask, WATCHDOG_INTERVAL_MS)
            START_STICKY
        }.onFailure {
            HLog.e("$TAG 启动前台保活服务失败: ${it.message}", it)
        }.getOrDefault(START_NOT_STICKY)
    }

    override fun onDestroy() {
        handler.removeCallbacks(keepAliveTask)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Hchat 微信保活", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager?.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Hchat 微信强保活")
            .setContentText("正在尝试保持微信息屏运行")
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun applyTargetCommand(intent: Intent?) {
        val action = intent?.action ?: return
        val hostPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (!WechatPackage.isTarget(hostPackage)) {
            HLog.e("$TAG 拒绝无效微信包名: $hostPackage")
            return
        }
        val current = readTargets()
        val updated = when (action) {
            ACTION_SET_TARGET -> WeChatKeepAliveTargetState.update(
                current,
                hostPackage,
                WeChatKeepAliveTargetOptions(
                    watchdog = intent.getBooleanExtra(EXTRA_WATCHDOG, false),
                    networkHeartbeat = intent.getBooleanExtra(EXTRA_NETWORK_HEARTBEAT, false)
                )
            )
            ACTION_REMOVE_TARGET -> WeChatKeepAliveTargetState.update(current, hostPackage, null)
            else -> current
        }
        val committed = getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
            .putStringSet(KEY_TARGETS, WeChatKeepAliveTargetState.encode(updated))
            .commit()
        if (!committed) HLog.e("$TAG 保活目标状态持久化失败")
    }

    private fun readTargets(): Map<String, WeChatKeepAliveTargetOptions> = runCatching {
        WeChatKeepAliveTargetState.decode(
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).getStringSet(KEY_TARGETS, emptySet())
        )
    }.getOrDefault(emptyMap())

    companion object {
        private const val TAG = "[Hchat:KeepAliveService]"
        private const val CHANNEL_ID = "hchat_wechat_keep_alive"
        private const val NOTIFICATION_ID = 520134
        private const val WATCHDOG_INTERVAL_MS = 60_000L
        private const val STATE_PREFS = "Hchat_wechat_keep_alive_service_state"
        private const val KEY_TARGETS = "targets"
        private const val EXTRA_TARGET_PACKAGE = "h.Hchat.extra.TARGET_PACKAGE"
        private const val EXTRA_WATCHDOG = "h.Hchat.extra.WATCHDOG"
        private const val EXTRA_NETWORK_HEARTBEAT = "h.Hchat.extra.NETWORK_HEARTBEAT"
        private const val ACTION_SET_TARGET = "h.Hchat.action.SET_KEEP_ALIVE_TARGET"
        private const val ACTION_REMOVE_TARGET = "h.Hchat.action.REMOVE_KEEP_ALIVE_TARGET"

        fun start(context: Context, hostPackage: String, watchdog: Boolean, networkHeartbeat: Boolean) {
            if (!WechatPackage.isTarget(hostPackage)) return
            val appContext = context.applicationContext ?: context
            val intent = Intent(appContext, WeChatKeepAliveService::class.java).apply {
                action = ACTION_SET_TARGET
                putExtra(EXTRA_TARGET_PACKAGE, hostPackage)
                putExtra(EXTRA_WATCHDOG, watchdog)
                putExtra(EXTRA_NETWORK_HEARTBEAT, networkHeartbeat)
            }
            startAsForegroundService(appContext, intent)
        }

        fun stop(context: Context, hostPackage: String) {
            if (!WechatPackage.isTarget(hostPackage)) return
            val appContext = context.applicationContext ?: context
            val intent = Intent(appContext, WeChatKeepAliveService::class.java).apply {
                action = ACTION_REMOVE_TARGET
                putExtra(EXTRA_TARGET_PACKAGE, hostPackage)
            }
            startAsForegroundService(appContext, intent)
        }

        private fun startAsForegroundService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
