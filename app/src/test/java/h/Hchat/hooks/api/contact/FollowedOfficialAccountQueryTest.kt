package h.Hchat.hooks.api.contact

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedOfficialAccountQueryTest {
    @Test
    fun `all-candidate SQL includes contact image join and biz relation join`() {
        val sql = WeChatContactApi.buildFollowedOfficialAccountsSql("r.username", 0)
        assertTrue(sql.contains("LEFT JOIN img_flag i ON r.username = i.username"))
        assertTrue(sql.contains("INNER JOIN bizinfo b ON b.username = r.username"))
        assertTrue(sql.contains("(r.type & 1) != 0"))
        assertFalse(sql.contains("r.username IN ("))
    }

    @Test
    fun `selected-account SQL uses one placeholder per username`() {
        val sql = WeChatContactApi.buildFollowedOfficialAccountsSql("r.username", 2)
        assertTrue(sql.contains("r.username IN (?,?)"))
    }
}
