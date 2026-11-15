package h.Hchat.hooks.items.aichat

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import h.Hchat.hooks.api.message.WeChatMessageStoreApi
import h.Hchat.hooks.api.runtime.WeChatDatabaseApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatHistoryCursorTest {
    class NativeDb(val db: SQLiteDatabase) {
        fun query(sql: String, args: Array<String>): Cursor = db.rawQuery(sql, args)
    }

    @Test fun sharedTableIsScopedAndEqualTimestampsPageWithoutGaps() {
        val sqlite = SQLiteDatabase.create(null)
        try {
            sqlite.execSQL("CREATE TABLE message(msgId INTEGER,msgSvrId INTEGER,type INTEGER,status INTEGER,isSend INTEGER,createTime INTEGER,talker TEXT,content TEXT,imgPath TEXT,reserved TEXT,transContent TEXT,flag INTEGER,lvbuffer BLOB)")
            for (id in 1..4) {
                val talker = if (id == 2) "other" else "friend"
                sqlite.execSQL("INSERT INTO message(msgId,type,isSend,createTime,talker,content) VALUES (?,1,0,100,?,?)", arrayOf<Any>(id, talker, "message$id"))
            }
            val wrapper = NativeDb(sqlite)
            val api = WeChatDatabaseApi(null) {}
            WeChatDatabaseApi::class.java.getDeclaredField("dbWrapper").apply { isAccessible = true }.set(api, wrapper)
            WeChatDatabaseApi::class.java.getDeclaredField("queryMethod").apply { isAccessible = true }
                .set(api, NativeDb::class.java.getDeclaredMethod("query", String::class.java, Array<String>::class.java))
            @Suppress("UNCHECKED_CAST")
            val cache = WeChatDatabaseApi::class.java.getDeclaredField("messageTableCache").apply { isAccessible = true }.get(api) as MutableMap<String, String>
            cache["friend"] = "message"
            val store = WeChatMessageStoreApi(api) {}
            assertEquals(listOf(4L, 3L, 1L), store.getRecentMessages("friend", 10).map { it.msgId })
            assertEquals(listOf(3L, 4L), store.getMessagesAfter("friend", 100, 1, 10).map { it.msgId })
            assertEquals(listOf(3L, 1L), store.getMessagesBefore("friend", 100, 4, 10).map { it.msgId })
            assertEquals(listOf(4L, 3L, 1L), store.searchMessages("friend", "message", 10).map { it.msgId })
            assertEquals(true, store.hasMessagesAfterState("friend", 100, 3))
            assertEquals(false, store.hasMessagesAfterState("friend", 100, 4))
        } finally { sqlite.close() }
    }
}
