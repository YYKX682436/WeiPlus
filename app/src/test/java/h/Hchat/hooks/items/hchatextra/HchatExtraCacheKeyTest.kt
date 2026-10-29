package h.Hchat.hooks.items.hchatextra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HchatExtraCacheKeyTest {
    @Test
    fun longRuntimeIdentityUsesBoundedIdentityScopedKeys() {
        val runtimeKey = buildString {
            append("com.tencent.mm|8.0.80|3220|")
            repeat(12) { append("patch-metadata-$it/").append("x".repeat(40)).append('|') }
            append("stable-loader")
        }
        val oldKey = "${runtimeKey}_chatting_data_adapter"

        assertTrue(oldKey.toByteArray(Charsets.UTF_8).size > 255)

        val key = HchatExtraCacheKey.forLookup(runtimeKey, "chatting_data_adapter")!!
        assertTrue(key.toByteArray(Charsets.UTF_8).size < 256)
        assertEquals(key, HchatExtraCacheKey.forLookup(runtimeKey, "chatting_data_adapter"))
        assertNotEquals(key, HchatExtraCacheKey.forLookup("$runtimeKey|replacement", "chatting_data_adapter"))
        assertNotEquals(key, HchatExtraCacheKey.forLookup(runtimeKey, "other_lookup"))
    }

    @Test
    fun blankRuntimeIdentityDoesNotProduceReusableCacheKey() {
        assertNull(HchatExtraCacheKey.forLookup("", "chatting_data_adapter"))
        assertNull(HchatExtraCacheKey.forLookup("  ", "chatting_data_adapter"))
    }
}
