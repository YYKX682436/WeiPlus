package android.content

class ContentValues(private val values: Map<String, Any?> = emptyMap()) {
    fun containsKey(key: String): Boolean = values.containsKey(key)
    fun get(key: String): Any? = values[key]
    fun getAsString(key: String): String? = values[key]?.toString()
    fun getAsInteger(key: String): Int? = values[key]?.toString()?.toIntOrNull()
}
