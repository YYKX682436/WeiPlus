package h.Hchat.keepalivesystemtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/** Test-only service that lets API 35 deliver its real dataSync timeout callback. */
class DataSyncTimeoutProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("keepalive_timeout_test", "Timeout probe", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "keepalive_timeout_test")
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Isolated dataSync timeout test")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION") startForeground(ID, notification)
        }
        Log.i(TAG, "DATASYNC_STARTED startId=$startId")
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.i(TAG, "DATASYNC_ON_TIMEOUT startId=$startId type=$fgsType")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    companion object {
        private const val TAG = "KeepAliveSystemProbe"
        private const val ID = 520136
    }
}
