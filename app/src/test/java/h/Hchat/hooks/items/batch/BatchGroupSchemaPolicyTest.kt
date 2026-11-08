package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchGroupSchemaPolicyTest {
    @Test
    fun `chatroom status is optional and read only from chatroom metadata`() {
        val result = BatchGroupSchemaPolicy.evaluate(
            true,
            setOf("username"),
            true,
            setOf("chatroomname", "memberlist", "roomowner")
        )
        assertTrue(result.success)
        assertTrue(result.hasRoomOwner)
        assertFalse(result.hasChatroomStatus)
    }

    @Test
    fun `query failure and missing required columns remain distinct from an empty group list`() {
        val readFailure = BatchGroupSchemaPolicy.evaluate(
            false, emptySet(), true, setOf("chatroomname", "memberlist")
        )
        assertFalse(readFailure.success)
        assertEquals("rcontact 表结构读取失败", readFailure.error)

        val emptyTablesWithSchema = BatchGroupSchemaPolicy.evaluate(
            true,
            setOf("username"),
            true,
            setOf("chatroomname", "memberlist")
        )
        assertTrue(emptyTablesWithSchema.success)
        assertFalse(emptyTablesWithSchema.hasRoomOwner)

        val missingRequired = BatchGroupSchemaPolicy.evaluate(
            true, setOf("username"), true, setOf("chatroomname")
        )
        assertFalse(missingRequired.success)
        assertEquals("chatroom 缺少必需列 memberlist", missingRequired.error)
    }
}
