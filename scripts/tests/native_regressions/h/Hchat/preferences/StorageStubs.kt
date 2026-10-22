package android.content

import java.io.File

interface SharedPreferences {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) = Unit
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) = Unit

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }
}

open class Context(
    open val packageName: String,
    open val dataDir: File,
    open val filesDir: File = File(dataDir, "files")
) {
    open val applicationContext: Context? get() = this
}
