package h.Hchat.keepalivesystemtest

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.TextView
import java.io.File
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveRuntime
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveSettings
import h.Hchat.hooks.items.keepalive.IWeChatKeepAliveControl
import h.Hchat.preferences.HchatStorage

class ProbeActivity : Activity() {
    private lateinit var status: TextView
    private var bound = false
    private var bindingContext: Context? = null
    private var control: IWeChatKeepAliveControl? = null
    private var action = "register"
    private var modulePackage = DEFAULT_MODULE_PACKAGE
    private var targetPackage = ""
    private var allowForegroundStart = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            control = IWeChatKeepAliveControl.Stub.asInterface(binder)
            Log.i(TAG, "service_connected module=$modulePackage target=$targetPackage action=$action")
            performAction()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            control = null
            allowForegroundStart = false
            Log.i(TAG, "service_disconnected module=$modulePackage target=$targetPackage")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "KeepAlive system probe: $action"
            textSize = 20f
            setPadding(32, 48, 32, 32)
        }
        setContentView(status)

        dispatchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchIntent(intent)
    }

    private fun dispatchIntent(intent: Intent) {
        action = intent.getStringExtra(EXTRA_ACTION) ?: "register"
        modulePackage = intent.getStringExtra(EXTRA_MODULE) ?: DEFAULT_MODULE_PACKAGE
        targetPackage = intent.getStringExtra(EXTRA_TARGET) ?: packageName
        allowForegroundStart = action == "register"
        val useModuleContextForBind = action == "module_context_bind"
        if (useModuleContextForBind) action = intent.getStringExtra(EXTRA_OPERATION) ?: "register"

        if (action == "production_apply") {
            runProductionApply()
            return
        }
        if (action == "storage_identity_probe") {
            runStorageIdentityProbe()
            return
        }
        if (action == "invalid_start") {
            startInvalidPromotion()
            return
        }

        if (action == "background_start") {
            Handler(Looper.getMainLooper()).postDelayed({ startBackgroundService() }, 8_000L)
            Log.i(TAG, "background_start_scheduled")
            return
        }
        if (action == "start_without_query") {
            startProductionServiceWithoutBinding()
            return
        }
        if (action == "timeout") {
            startTimeoutService()
            return
        }
        if (action == "bind_private") {
            val privateService = Intent().setComponent(
                ComponentName(modulePackage, PRIVATE_SERVICE_CLASS)
            )
            try {
                val accepted = bindService(privateService, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.e(TAG, "PRIVATE_BIND_UNEXPECTEDLY_ALLOWED")
                    }
                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                }, Context.BIND_AUTO_CREATE)
                Log.i(TAG, "PRIVATE_BIND_RESULT accepted=$accepted")
            } catch (error: SecurityException) {
                Log.i(TAG, "PRIVATE_BIND_DENIED exception=${error.javaClass.simpleName}")
            }
            return
        }

        if (control != null) {
            performAction()
            return
        }

        val serviceIntent = Intent().setComponent(
            ComponentName(modulePackage, SERVICE_CLASS)
        )
        if (!bound) {
            val context = if (useModuleContextForBind) {
                runCatching {
                    createPackageContext(
                        modulePackage,
                        Context.CONTEXT_IGNORE_SECURITY
                    )
                }
                    .onFailure { Log.e(TAG, "module_context_create_failed package=$modulePackage", it) }
                    .getOrNull()
            } else {
                this
            }
            if (context != null) {
                Log.i(
                    TAG,
                    "BIND_CONTEXT_IDENTITY processUid=${Process.myUid()} contextPackage=${context.packageName} " +
                        "contextPackageUid=${context.applicationInfo.uid} " +
                        "same=${Process.myUid() == context.applicationInfo.uid}"
                )
                bindingContext = context
                bound = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            }
        }
        Log.i(TAG, "bind_requested accepted=$bound module=$modulePackage action=$action viaModuleContext=$useModuleContextForBind")
    }

    override fun onDestroy() {
        if (bound) runCatching { (bindingContext ?: this).unbindService(connection) }
        super.onDestroy()
    }

    private fun performAction() {
        val service = control ?: return
        try {
            when (action) {
                "register" -> registerTarget(service, targetPackage, allowForegroundStart)
                "register_no_promote" -> {
                    val token = service.registerTarget(targetPackage, true, false)
                    status.text = "registered $targetPackage without promotion"
                    Log.i(TAG, "REGISTER_NO_PROMOTE package=$targetPackage token=${token.isNotBlank()}")
                }
                "register_then_invalid_start" -> {
                    val token = service.registerTarget(targetPackage, true, false)
                    Log.i(TAG, "REGISTER_THEN_INVALID_START package=$targetPackage token=${token.isNotBlank()}")
                    Handler(Looper.getMainLooper()).postDelayed({ startInvalidPromotion() }, 1_000L)
                }
                "background_remove" -> {
                    service.registerTarget(targetPackage, true, false)
                    Log.i(TAG, "BACKGROUND_REMOVE_SCHEDULED package=$targetPackage delayMs=8000")
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching { service.removeTarget(targetPackage) }
                            .onSuccess { Log.i(TAG, "BACKGROUND_REMOVE_OK package=$targetPackage") }
                            .onFailure { Log.e(TAG, "BACKGROUND_REMOVE_FAILED package=$targetPackage", it) }
                    }, 8_000L)
                }
                "remove" -> {
                    service.removeTarget(targetPackage)
                    status.text = "removed $targetPackage"
                    Log.i(TAG, "REMOVE_OK package=$targetPackage")
                }
                "attack" -> {
                    service.registerTarget("com.tencent.mm", true, false)
                    Log.e(TAG, "UNEXPECTED_AUTHORIZED attacker=$packageName")
                }
                else -> Log.e(TAG, "unknown_action=$action")
            }
        } catch (error: SecurityException) {
            status.text = "denied ${error.message}"
            Log.i(TAG, "DENIED attacker=$packageName target=$targetPackage")
        } catch (error: Throwable) {
            status.text = "error ${error.javaClass.simpleName}: ${error.message}"
            Log.e(TAG, "PROBE_ERROR action=$action target=$targetPackage", error)
        }
    }

    private fun registerTarget(
        service: IWeChatKeepAliveControl,
        packageName: String,
        allowPromotion: Boolean
    ) {
        val token = service.registerTarget(packageName, true, false)
        allowForegroundStart = false
        status.text = "registered $packageName; token=${token.isNotBlank()}"
        Log.i(TAG, "REGISTER_OK package=$packageName token=${token.isNotBlank()}")
        if (token.isNotBlank() && allowPromotion) {
            val intent = Intent().setComponent(ComponentName(modulePackage, SERVICE_CLASS)).apply {
                action = PROMOTE_ACTION
                putExtra(PROMOTION_EXTRA, token)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else @Suppress("DEPRECATION") startService(intent)
                Log.i(TAG, "PROMOTION_REQUESTED package=$packageName")
            } catch (error: Throwable) {
                Log.i(TAG, "PROMOTION_DENIED package=$packageName reason=${error.javaClass.simpleName}")
            }
        }
    }

    private fun startBackgroundService() {
        val intent = Intent(this, BackgroundStartProbeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else @Suppress("DEPRECATION") startService(intent)
            Log.e(TAG, "BACKGROUND_FGS_UNEXPECTEDLY_ALLOWED")
        } catch (error: Throwable) {
            Log.i(TAG, "BACKGROUND_FGS_DENIED exception=${error.javaClass.simpleName}")
        }
    }

    private fun startProductionServiceWithoutBinding() {
        val serviceIntent = Intent().setComponent(
            ComponentName(modulePackage, SERVICE_CLASS)
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else @Suppress("DEPRECATION") startService(serviceIntent)
            Log.i(TAG, "PRODUCTION_EXPLICIT_START_ACCEPTED module=$modulePackage")
        } catch (error: Throwable) {
            Log.i(TAG, "PRODUCTION_EXPLICIT_START_DENIED module=$modulePackage reason=${error.javaClass.simpleName}")
        }
    }

    private fun startTimeoutService() {
        val intent = Intent(this, DataSyncTimeoutProbeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else @Suppress("DEPRECATION") startService(intent)
            Log.i(TAG, "DATASYNC_TIMEOUT_PROBE_STARTED")
        } catch (error: Throwable) {
            Log.e(TAG, "DATASYNC_TIMEOUT_PROBE_START_FAILED", error)
        }
    }

    /** Mirrors the production settings page's apply(context, allowForegroundStart = true) call. */
    private fun runProductionApply() {
        val prefs = HchatStorage.preferences(this, WeChatKeepAliveSettings.PREFS_NAME)
        prefs.edit()
            .putBoolean(WeChatKeepAliveSettings.KEY_ENABLE, true)
            .putBoolean(WeChatKeepAliveSettings.KEY_FOREGROUND_SERVICE, true)
            .putBoolean(WeChatKeepAliveSettings.KEY_WAKE_LOCK, false)
            .putBoolean(WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST, false)
            .putBoolean(WeChatKeepAliveSettings.KEY_ROOT_APP_OPS, false)
            .putBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, true)
            .putBoolean(WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT, false)
            .commit()
        Log.i(TAG, "PRODUCTION_APPLY_BEGIN package=$packageName callerUid=${Process.myUid()}")
        WeChatKeepAliveRuntime.apply(this, allowForegroundStart = true)
        Handler(Looper.getMainLooper()).postDelayed({
            Log.i(TAG, "PRODUCTION_APPLY_SETTLED package=$packageName")
        }, 1_000L)
        val disableAfterMs = intent.getLongExtra(EXTRA_DISABLE_AFTER_MS, 0L)
        if (disableAfterMs > 0L) {
            Handler(Looper.getMainLooper()).postDelayed({
                prefs.edit().putBoolean(WeChatKeepAliveSettings.KEY_ENABLE, false).commit()
                WeChatKeepAliveRuntime.apply(this)
                Log.i(TAG, "PRODUCTION_DISABLE_APPLIED package=$packageName")
            }, disableAfterMs)
        }
    }

    private fun runStorageIdentityProbe() {
        val moduleContext = runCatching {
            createPackageContext(modulePackage, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse {
            Log.i(TAG, "MODULE_CONTEXT_UNAVAILABLE package=$modulePackage reason=${it.javaClass.simpleName}")
            return
        }
        Log.i(
            TAG,
            "STORAGE_CONTEXT_IDENTITY processUid=${Process.myUid()} hostUid=${applicationInfo.uid} " +
                "moduleContextPackage=${moduleContext.packageName} moduleUid=${moduleContext.applicationInfo.uid}"
        )
        val moduleWrite = writeSnapshot(moduleContext, "module_uid_write")
        val hostWrite = writeSnapshot(this, "host_uid_write")
        Log.i(
            TAG,
            "STORAGE_WRITE_RESULTS module=$moduleWrite host=$hostWrite"
        )
    }

    private fun writeSnapshot(context: Context, key: String): String = runCatching {
        val prefs: SharedPreferences = context.getSharedPreferences(ROOT_STATE_PROBE_PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().putBoolean(key, true).commit()) { "commit returned false" }
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$ROOT_STATE_PROBE_PREFS.xml")
        check(file.isFile && file.length() > 0L) { "snapshot file missing: ${file.absolutePath}" }
        check(prefs.getBoolean(key, false)) { "snapshot value did not read back" }
        "ok:${file.absolutePath}:${file.length()}"
    }.getOrElse { "failed:${it.javaClass.simpleName}:${it.message}" }

    private fun startInvalidPromotion() {
        val serviceIntent = Intent().setComponent(ComponentName(modulePackage, SERVICE_CLASS)).apply {
            action = PROMOTE_ACTION
            putExtra(PROMOTION_EXTRA, "invalid-test-token")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else @Suppress("DEPRECATION") startService(serviceIntent)
            Log.i(TAG, "INVALID_PROMOTION_REQUESTED module=$modulePackage caller=$packageName")
        } catch (error: Throwable) {
            Log.i(TAG, "INVALID_PROMOTION_DENIED reason=${error.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "KeepAliveSystemProbe"
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_MODULE = "module"
        private const val EXTRA_OPERATION = "operation"
        private const val EXTRA_DISABLE_AFTER_MS = "disable_after_ms"
        private const val DEFAULT_MODULE_PACKAGE = "h.Hchat.r2system"
        private const val SERVICE_CLASS = "h.Hchat.hooks.items.keepalive.WeChatKeepAliveService"
        private const val PRIVATE_SERVICE_CLASS = "h.Hchat.hooks.items.keepalive.PrivateKeepAliveProbeService"
        private const val PROMOTE_ACTION = "h.Hchat.action.PROMOTE_KEEP_ALIVE_MONITOR"
        private const val PROMOTION_EXTRA = "h.Hchat.extra.KEEP_ALIVE_PROMOTION_TOKEN"
        private const val ROOT_STATE_PROBE_PREFS = "Hchat_r3_keepalive_uid_probe"
    }
}
