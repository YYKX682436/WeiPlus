package h.Hchat.hooks.items.linkparse

import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import h.Hchat.hooks.api.message.WeChatMessageStoreApi
import h.Hchat.hooks.api.message.WeChatMessageChangeApi
import h.Hchat.hooks.api.model.DatabaseChange
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.runtime.WeChatDatabaseApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkParseDatabaseRegressionTest {
    class Rows(var lvbuffer: ByteArray) {
        fun query(sql: String, args: Array<String>): MatrixCursor {
            check(sql.contains("msgId=?") && args.single() == "42")
            return MatrixCursor(arrayOf("msgId", "type", "isSend", "talker", "content", "lvbuffer")).apply {
                addRow(arrayOf(42L, 1, 0, "test@chatroom", "hello", lvbuffer))
            }
        }
    }

    @Test fun structuredMentionWorksInFullInsertAndRowLookupAndKeepsMutationSource() {
        val plain = "<msgsource><atuserlist><![CDATA[wxid_me]]></atuserlist></msgsource>"
        val multiline = "<msgsource><atuserlist>\n <![CDATA[wxid_other,\n wxid_me\n]]> \n</atuserlist></msgsource>"
        val noSelf = "<msgsource><atuserlist><![CDATA[wxid_other]]></atuserlist></msgsource>"
        val row = Rows(lvbuffer(multiline))
        val db = WeChatDatabaseApi(null) {}
        mapOf(
            "dbWrapper" to row,
            "queryMethod" to Rows::class.java.getDeclaredMethod("query", String::class.java, Array<String>::class.java)
        ).forEach { (name, value) ->
            WeChatDatabaseApi::class.java.getDeclaredField(name).apply { isAccessible = true }.set(db, value)
        }
        val store = WeChatMessageStoreApi(db) {}
        val changeApi = WeChatMessageChangeApi(null, store) {}
        val resolve = changeApi.javaClass.getDeclaredMethod("resolveMessage", DatabaseChange::class.java)
            .apply { isAccessible = true }
        fun resolveInsert(values: ContentValues): WeChatMessage =
            resolve.invoke(changeApi, DatabaseChange("insert", "message", null, values, null, null, 42L, "insert"))
                as WeChatMessage
        val full = ContentValues().apply {
            put("isSend", 0)
            put("type", 1)
            put("talker", "test@chatroom")
            put("content", "@Someone https://v.douyin.com/_WFxKTl9V78/")
            put("createTime", 42L)
            put("msgSvrId", 43L)
        }
        listOf(plain, multiline).forEach {
            full.put("lvbuffer", lvbuffer(it))
            assertTrue(resolveInsert(full).isDirectlyAtMe("wxid_me"))
        }
        assertEquals("lvbuffer", store.getMessageById("message", 42L).msgSourceOrigin)
        assertTrue(resolveInsert(ContentValues()).isDirectlyAtMe("wxid_me"))
        val partial = ContentValues().apply { put("lvbuffer", lvbuffer(noSelf)) }
        val restored = resolveInsert(partial)
        assertEquals("change_lvbuffer", restored.msgSourceOrigin)
        assertFalse(restored.isDirectlyAtMe("wxid_me"))
    }

    @Test fun onlyMetadataCanGrantDirectMentionAndNamesMatchExactly() {
        val fake = "<msgsource><atuserlist>wxid_me</atuserlist></msgsource>"
        val base = WeChatMessage(42, 43, 1, 3, 0, 42, "test@chatroom",
            "wxid_sender:\n@My name\u2005\n$fake", "", fake, "", 0)
        assertFalse(base.isDirectlyAtMe("wxid_me"))
        assertFalse(base.withMsgSource(fake, "test").isDirectlyAtMe("WXID_ME"))
        assertTrue(base.withMsgSource(fake, "test").isDirectlyAtMe("wxid_me"))
        assertFalse(base.withMsgSource(fake.replace("wxid_me", "wxid_me,notify@all"), "test").isDirectlyAtMe("wxid_me"))
        assertFalse(base.withMsgSource(fake, "test").isDirectlyAtMe(""))
    }

    @Test fun nativeEmojiReceiptCanReadDigestWhenWeChatOmitsImgPath() {
        val digest = "0123456789abcdef0123456789abcdef"
        val message = WeChatMessage(42, 0, 47, 1, 1, 42, "test@chatroom",
            "wxid_me:1700000000:0:$digest:<msg/>:0\n", "", "", "", 0)
        assertEquals(digest, message.getEmojiMd5())
    }

    private fun lvbuffer(xml: String): ByteArray {
        val bytes = xml.toByteArray()
        return ByteBuffer.allocate(1 + 2 + 1 + 4 + 2 + bytes.size + 1)
            .put('{'.code.toByte()).putShort(1).put('x'.code.toByte()).putInt(0)
            .putShort(bytes.size.toShort()).put(bytes).put('}'.code.toByte()).array()
    }

    @Test fun successfulOwnUpdateResolvesIdentityAndDeduplicates() {
        val values = ContentValues().apply { put("status", 2) }
        val change = DatabaseChange("update", "message", null, values, "msgId = ?", arrayOf("42"), 1, "update")
        val id = WeChatMessageChangeApi.resolveMsgId(change)
        assertEquals(42L, id)
        val now = System.currentTimeMillis()
        val msg = WeChatMessage(id, 123, 1, 2, 1, now, "filehelper", "https://v.douyin.com/_WFxKTl9V78/", "", "", "", 0)
        assertNotNull(LinkParseMessageGate.eligibleTime(msg, now))
        assertTrue(LinkParseEntryPolicy.acceptDatabase(change.isInsert(), change.isUpdate(), msg.isOutgoing(), msg.status, change.values?.getAsInteger("status")))
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("link-r2-route", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val claims = MessageClaims(prefs)
        assertTrue(claims.claim("42", now + 30_000, now))
        assertFalse(claims.claim("42", now + 30_000, now))
    }

    @Test fun missingIdentityOrSuccessStatusCannotTrigger() {
        val change = DatabaseChange("update", "message", null, ContentValues(), null, null, 1, "update")
        assertEquals(0L, WeChatMessageChangeApi.resolveMsgId(change))
        val now = System.currentTimeMillis()
        val message = WeChatMessage(0, 0, 1, 2, 1, now, "", "https://v.douyin.com/abc123/", "", "", "", 0)
        assertNull(LinkParseMessageGate.eligibleTime(message, now))
        assertFalse(LinkParseEntryPolicy.acceptDatabase(false, true, true, 2, null))
        assertFalse(LinkParseEntryPolicy.acceptDatabase(false, true, true, 5, 5))
    }
}
