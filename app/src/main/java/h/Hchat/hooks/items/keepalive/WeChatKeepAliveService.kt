package h.Hchat.hooks.items.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import h.Hchat.R
import h.Hchat.WechatPackage
import h.Hchat.utils.HLog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The only cross-UID entry is this Binder interface. Every mutating call checks the Binder UID
 * against the exact WeChat package. Service start Intents never carry target state; an FGS start
 * additionally requires a short-lived, one-use token issued by an authorized Binder call.
 */
class WeChatKeepAliveService : Service() {
    private data class Promotion(val hostPackage: String, val expiresAt: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Hchat-KeepAlive").apply { isDaemon = true }
    }
    private val targets = ConcurrentHashMap<String, WeChatKeepAliveTargetOptions>()
    private val promotions = ConcurrentHashMap<String, Promotion>()
    @Volatile private var foreground = false

    private val keepAliveTask = object : Runnable {
        override fun run() {
            val snapshot = targets.toMap()
            if (snapshot.isEmpty()) return
            worker.execute {
                snapshot.forEach { (hostPackage, options) ->
                    WeChatKeepAliveRuntime.watchdogTick(this@WeChatKeepAliveService, hostPackage, options.watchdog)
                }
                WeChatKeepAliveRuntime.heartbeatTick(snapshot.values.any { it.networkHeartbeat })
            }
            if (targets.isNotEmpty()) handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private val control = object : IWeChatKeepAliveControl.Stub() {
        override fun registerTarget(
            hostPackage: String,
            watchdog: Boolean,
            networkHeartbeat: Boolean
        ): String {
            enforceTargetCaller(hostPackage)
            targets[hostPackage] = WeChatKeepAliveTargetOptions(watchdog, networkHeartbeat)
            Log.i(
                TAG,
                "event=target_registered package=$hostPackage callerUid=${Binder.getCallingUid()} active=${targets.size}"
            )
            handler.post(::refreshWork)
            return issuePromotionToken(hostPackage)
        }

        override fun removeTarget(hostPackage: String) {
            enforceTargetCaller(hostPackage)
            targets.remove(hostPackage)
            promotions.entries.removeIf { it.value.hostPackage == hostPackage }
            Log.i(
                TAG,
                "event=target_removed package=$hostPackage callerUid=${Binder.getCallingUid()} active=${targets.size}"
            )
            handler.post(::refreshWork)
        }
    }

    override fun onBind(intent: Intent?): IBinder = control

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PROMOTE) {
            HLog.e("$TAG 拒绝未认证的 Service 启动动作")
            if (targets.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val token = intent.getStringExtra(EXTRA_PROMOTION_TOKEN)
        val promotion = token?.let(promotions::remove)
        if (promotion == null || promotion.expiresAt < System.currentTimeMillis() ||
            targets[promotion.hostPackage] == null
        ) {
            // Arbitrary external start requests have no effect and are never sticky.
            HLog.e("$TAG 拒绝没有有效授权令牌的 Service 启动请求")
            if (targets.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }

        return try {
            startForegroundCompat()
            foreground = true
            refreshWork()
            START_NOT_STICKY
        } catch (error: Throwable) {
            HLog.e("$TAG 前台服务启动被系统拒绝，保留绑定模式: ${error.message}", error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelfResult(startId)
            START_NOT_STICKY
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // This service uses specialUse, which has no Android 15 six-hour quota. If a future
        // platform still reports a timeout, stop cleanly and never restart to evade it.
        HLog.e("$TAG 系统要求停止前台服务: type=$fgsType; 不会自动重启")
        handler.removeCallbacks(keepAliveTask)
        targets.clear()
        promotions.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf(startId)
    }

    override fun onDestroy() {
        handler.removeCallbacks(keepAliveTask)
        worker.shutdownNow()
        targets.clear()
        promotions.clear()
        super.onDestroy()
    }

    private fun enforceTargetCaller(hostPackage: String) {
        val callerUid = Binder.getCallingUid()
        if (!WechatPackage.isTarget(hostPackage)) {
            throw SecurityException("Unsupported keep-alive package")
        }
        val callerPackages = packageManager.getPackagesForUid(callerUid)
        if (!WeChatKeepAliveCallerPolicy.isAuthorized(hostPackage, callerPackages)) {
            HLog.e("$TAG 拒绝非目标微信调用: uid=$callerUid target=$hostPackage")
            throw SecurityException("Caller package is not an allowed WeChat target")
        }
    }

    private fun issuePromotionToken(hostPackage: String): String {
        if (foreground || targets[hostPackage] == null) return ""
        val now = System.currentTimeMillis()
        promotions.entries.removeIf { it.value.expiresAt < now }
        val token = UUID.randomUUID().toString()
        promotions[token] = Promotion(hostPackage, now + TOKEN_TTL_MS)
        return token
    }

    private fun refreshWork() {
        if (targets.isEmpty()) {
            Log.i(TAG, "event=service_idle_stop foreground=$foreground")
            handler.removeCallbacks(keepAliveTask)
            // The OS may retain FGS state across process recreation while this field resets.
            // Always clear the system state when the last target is removed.
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return
        }
        if (foreground) {
            runCatching { startForegroundCompat() }
                .onFailure { HLog.e("$TAG 更新监测通知失败: ${it.message}", it) }
        }
        handler.removeCallbacks(keepAliveTask)
        handler.postDelayed(keepAliveTask, WATCHDOG_INTERVAL_MS)
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Hchat 微信保活", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val watchedTargets = targets.keys.sorted().joinToString("、") { packageLabel(it) }
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Hchat 微信进程监测")
            .setContentText(if (watchedTargets.isBlank()) "监测服务正在启动" else "监测中：$watchedTargets")
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun packageLabel(hostPackage: String): String = when (hostPackage) {
        WechatPackage.OFFICIAL -> "官方微信"
        WechatPackage.MODIFIED -> "修改版微信"
        else -> hostPackage
    }

    companion object {
        private const val TAG = "[Hchat:KeepAliveService]"
        private const val CHANNEL_ID = "hchat_wechat_keep_alive"
        private const val NOTIFICATION_ID = 520134
        private const val WATCHDOG_INTERVAL_MS = 60_000L
        private const val TOKEN_TTL_MS = 10_000L
        private const val EXTRA_PROMOTION_TOKEN = "h.Hchat.extra.KEEP_ALIVE_PROMOTION_TOKEN"
        private const val ACTION_PROMOTE = "h.Hchat.action.PROMOTE_KEEP_ALIVE_MONITOR"

        internal fun promotionIntent(
            modulePackage: String,
            serviceClassName: String,
            token: String
        ): Intent = Intent().apply {
            component = android.content.ComponentName(modulePackage, serviceClassName)
            action = ACTION_PROMOTE
            putExtra(EXTRA_PROMOTION_TOKEN, token)
        }
    }
}
