package android.content

import java.io.File

interface SharedPreferences

open class Context(
    open val packageName: String,
    open val dataDir: File,
    open val filesDir: File = File(dataDir, "files")
) {
    open val applicationContext: Context? get() = this
}
