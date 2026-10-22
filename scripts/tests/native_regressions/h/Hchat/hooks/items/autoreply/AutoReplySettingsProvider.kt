package h.Hchat.hooks.items.autoreply

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArrayList

class AutoReplySettingsProvider

class AutoReplySettings(context: Context?) {
    fun isEnabled(): Boolean = TestAutoReplySettings.messageRepliesEnabled
    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (key == KEY_AUTO_ACCEPT_ENABLE) TestAutoReplySettings.autoAcceptEnabled else defaultValue
    fun getHostPreferences(): SharedPreferences = TestAutoReplySettings.preferences

    companion object {
        const val KEY_ENABLE = "auto_reply_enable"
        const val KEY_AUTO_ACCEPT_ENABLE = "auto_accept_enable"
    }
}

object TestAutoReplySettings {
    @Volatile var messageRepliesEnabled = true
    @Volatile var autoAcceptEnabled = false
    val preferences = FakePreferences()

    fun configure(messageReplies: Boolean, autoAccept: Boolean) {
        val oldReply = messageRepliesEnabled
        val oldAccept = autoAcceptEnabled
        messageRepliesEnabled = messageReplies
        autoAcceptEnabled = autoAccept
        if (oldReply != messageReplies) preferences.changed(AutoReplySettings.KEY_ENABLE)
        if (oldAccept != autoAccept) preferences.changed(AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE)
    }

    fun reset() = configure(true, false)
}

class FakePreferences : SharedPreferences {
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.addIfAbsent(listener)
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }
    fun changed(key: String) = listeners.forEach { it.onSharedPreferenceChanged(this, key) }
    val listenerCount: Int get() = listeners.size
}
