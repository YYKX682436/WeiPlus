package h.Hchat.hooks.api.runtime

import android.database.Cursor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class WeChatDatabaseApiCursorColumnsTest {
    @Test
    fun `indexed column names recover metadata when wrapper returns null array`() {
        val cursor = cursorProxy(columnNames = null, count = 2)
        assertEquals(2, cursor.columnCount)
        assertEquals("username", cursor.getColumnName(0))
        assertArrayEquals(arrayOf("username", "verifyFlag"), WeChatDatabaseApi.cursorColumnNames(cursor))
    }

    @Test
    fun `normal column name array remains preferred`() {
        val cursor = cursorProxy(columnNames = arrayOf("cachedName"), count = 2)
        assertArrayEquals(arrayOf("cachedName"), WeChatDatabaseApi.cursorColumnNames(cursor))
    }

    @Test
    fun `empty cursor without names remains unavailable`() {
        assertNull(WeChatDatabaseApi.cursorColumnNames(cursorProxy(columnNames = null, count = 0)))
    }

    private fun cursorProxy(columnNames: Array<String>?, count: Int): Cursor =
        Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getColumnNames" -> columnNames
                "getColumnCount" -> count
                "getColumnName" -> arrayOf("username", "verifyFlag").getOrNull(args?.firstOrNull() as? Int ?: -1)
                else -> null
            }
        } as Cursor
}
