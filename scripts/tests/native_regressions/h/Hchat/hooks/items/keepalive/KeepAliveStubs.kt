package h.Hchat.hooks.items.keepalive

import android.content.Context

object WeChatKeepAliveSettings {
    const val PREFS_NAME = "Hchat_wechat_keep_alive_config"
    const val KEY_ENABLE = "wechat_keep_alive_enable"
    const val KEY_FOREGROUND_SERVICE = "wechat_keep_alive_foreground_service"
    const val KEY_WAKE_LOCK = "wechat_keep_alive_wake_lock"
    const val KEY_ROOT_DOZE_WHITELIST = "wechat_keep_alive_root_doze_whitelist"
    const val KEY_WATCHDOG = "wechat_keep_alive_watchdog"
    const val KEY_ROOT_APP_OPS = "wechat_keep_alive_root_app_ops"
    const val KEY_NETWORK_HEARTBEAT = "wechat_keep_alive_network_heartbeat"
    val CONFIG_KEYS = setOf(
        KEY_ENABLE, KEY_FOREGROUND_SERVICE, KEY_WAKE_LOCK,
        KEY_ROOT_DOZE_WHITELIST, KEY_WATCHDOG, KEY_ROOT_APP_OPS, KEY_NETWORK_HEARTBEAT
    )
}

class WeChatKeepAliveSettingsProvider

object WeChatKeepAliveRuntime {
    data class ApplyCall(val hostPackage: String, val modulePackage: String)

    val applyCalls = mutableListOf<ApplyCall>()
    val stopCalls = mutableListOf<Pair<String, String>>()

    fun apply(hostContext: Context?, moduleContext: Context? = null) {
        if (hostContext != null && moduleContext != null) {
            applyCalls += ApplyCall(hostContext.packageName, moduleContext.packageName)
        }
    }

    fun stop(hostPackage: String?, moduleContext: Context? = null) {
        if (hostPackage != null && moduleContext != null) stopCalls += hostPackage to moduleContext.packageName
    }

    fun stop(context: Context?) {
        if (context != null) stopCalls += "<module-only>" to context.packageName
    }

    fun reset() {
        applyCalls.clear()
        stopCalls.clear()
    }
}
