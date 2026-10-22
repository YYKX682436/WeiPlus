package h.Hchat.hooks.items.keepalive

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Control showing that an exported=false service rejects the separate target UID. */
class PrivateKeepAliveProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
