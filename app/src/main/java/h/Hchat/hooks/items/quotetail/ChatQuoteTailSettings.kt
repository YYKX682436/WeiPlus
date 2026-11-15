package h.Hchat.hooks.items.quotetail

import android.content.Context
import android.content.SharedPreferences
import h.Hchat.preferences.HchatStorage

object ChatQuoteTailSettings {
    const val PREFS_NAME = "Hchat_chat_quote_tail_config"
    const val KEY_ENABLE = "chat_quote_tail_enable"
    // Legacy preference key retained; runtime code intentionally no longer reads it.
    const val KEY_NICKNAME = "chat_quote_tail_nickname"
    const val KEY_CONTENT = "chat_quote_tail_content"

    const val DEFAULT_ENABLE = false
    const val DEFAULT_CONTENT = ""

    fun preferences(context: Context): SharedPreferences =
        HchatStorage.preferences(context, PREFS_NAME)
}
