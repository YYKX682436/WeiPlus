package io.fastkv

import android.content.SharedPreferences

class FastKV : SharedPreferences {
    fun close() = Unit

    class Builder(path: String, name: String) {
        fun build(): FastKV = FastKV()
    }
}
