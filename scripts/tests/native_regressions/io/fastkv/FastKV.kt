package io.fastkv

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FastKV : SharedPreferences {
    private val values = ConcurrentHashMap<String, Boolean>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] ?: defaultValue

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.addIfAbsent(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    fun setBoolean(key: String, value: Boolean) {
        values[key] = value
        listeners.forEach { it.onSharedPreferenceChanged(this, key) }
    }

    val listenerCount: Int get() = listeners.size

    fun close() = Unit

    class Builder(private val path: String, private val name: String) {
        fun build(): FastKV = instances.computeIfAbsent("$path:$name") { FastKV() }

        companion object {
            private val instances = ConcurrentHashMap<String, FastKV>()
        }
    }
}
