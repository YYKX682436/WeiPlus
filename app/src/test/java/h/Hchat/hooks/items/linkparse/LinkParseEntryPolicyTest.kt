package h.Hchat.hooks.items.linkparse

import org.junit.Assert.*
import org.junit.Test

class LinkParseEntryPolicyTest {
    @Test fun ownDatabaseInsertCannotRepeatButtonHandling() {
        assertFalse(LinkParseEntryPolicy.acceptDatabase(true))
        assertTrue(LinkParseEntryPolicy.acceptDatabase(false))
    }
}
