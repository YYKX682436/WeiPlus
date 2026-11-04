package h.Hchat.hooks.items.sportstep

import android.content.Context
import h.Hchat.preferences.HchatStorage

/** Values belong to one package and one currently identified WeChat account. */
internal class SportStepSettings(private val context: Context) {
    private val prefs = HchatStorage.preferences(context, "Hchat_sport_step")

    fun manualDay(account: String): String? = key(account)?.let { prefs.getString("$it.manual_day", null) }

    fun recordManualDay(account: String, day: String): Boolean {
        val key = key(account) ?: return false
        if (day.isBlank()) return false
        return prefs.edit().putString("$key.manual_day", day).commit()
    }

    private fun key(account: String): String? = StepScopeKey.of(context.packageName, account)
}
