package h.Hchat.hooks.items.backgroundassist

/** Existing storage names are retained so users' selected options and Root rollback snapshots survive. */
object BackgroundRunAssistSettings {
    const val PREFS_NAME = "Hchat_wechat_keep_alive_config"
    const val KEY_ENABLE = "wechat_keep_alive_enable"
    const val KEY_WAKE_LOCK = "wechat_keep_alive_wake_lock"
    const val KEY_ROOT_DOZE_WHITELIST = "wechat_keep_alive_root_doze_whitelist"
    const val KEY_ROOT_APP_OPS = "wechat_keep_alive_root_app_ops"

    const val DEFAULT_ENABLE = false
    const val DEFAULT_WAKE_LOCK = false
    const val DEFAULT_ROOT_DOZE_WHITELIST = false
    const val DEFAULT_ROOT_APP_OPS = false

    val CONFIG_KEYS = setOf(KEY_ENABLE, KEY_WAKE_LOCK, KEY_ROOT_DOZE_WHITELIST, KEY_ROOT_APP_OPS)
}
