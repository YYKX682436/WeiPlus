package h.Hchat.hooks.items.linkparse

import android.content.ContentValues
import android.content.Context
import h.Hchat.hooks.api.message.WeChatMessageChangeApi
import h.Hchat.hooks.api.model.DatabaseChange
import h.Hchat.hooks.api.model.WeChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkParseDatabaseRegressionTest {
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
