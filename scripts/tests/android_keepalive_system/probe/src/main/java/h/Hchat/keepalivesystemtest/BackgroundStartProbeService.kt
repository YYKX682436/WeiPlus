package h.Hchat.keepalivesystemtest

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/** This must never be started by the background_start activity after it leaves the foreground. */
class BackgroundStartProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Background start probe")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(520137, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            @Suppress("DEPRECATION") startForeground(520137, notification)
        }
        Log.i("KeepAliveSystemProbe", "BACKGROUND_SERVICE_STARTED_UNEXPECTEDLY")
        return START_NOT_STICKY
    }
}
