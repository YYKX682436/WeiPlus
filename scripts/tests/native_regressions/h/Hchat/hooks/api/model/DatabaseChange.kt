package h.Hchat.hooks.api.model

import android.content.ContentValues

data class DatabaseChange(val table: String, val values: ContentValues?, val operation: String = "insert") {
    fun isInsert(): Boolean = operation.equals("insert", ignoreCase = true)
}
