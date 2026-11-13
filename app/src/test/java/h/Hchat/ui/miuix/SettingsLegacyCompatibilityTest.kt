package h.Hchat.ui.miuix

import android.content.Context
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsLegacyCompatibilityTest {
    @Test
    fun disabledLegacyRedPacketReplyRemainsEditableWithoutEnablingOrWritingIt() {
        val sp = RuntimeEnvironment.getApplication().getSharedPreferences("red-packet-legacy", Context.MODE_PRIVATE)
        sp.edit().clear()
            .putBoolean(RedPacketSettings.KEY_REPLY_ENABLE, false)
            .putInt(RedPacketSettings.KEY_REPLY_TYPE, RedPacketRuleConfig.REPLY_TEXT)
            .putString(RedPacketSettings.KEY_REPLY_TEMPLATES, "旧回复")
            .commit()
        val before = sp.all.toMap()

        assertEquals("旧回复", loadGlobalRedPacketReplySteps(sp, includeDisabledLegacy = true).single().content)
        assertEquals("旧回复", loadGlobalGroupRedPacketReplySteps(sp, includeDisabledLegacy = true).single().content)
        assertTrue(loadGlobalRedPacketReplySteps(sp).isEmpty())
        assertFalse(sp.getBoolean(RedPacketSettings.KEY_REPLY_ENABLE, true))
        assertEquals(before, sp.all)

        sp.edit().putString(RedPacketSettings.KEY_REPLY_GROUP_ITEMS, "[]").commit()
        assertTrue(loadGlobalGroupRedPacketReplySteps(sp, includeDisabledLegacy = true).isEmpty())
        sp.edit().putString(RedPacketSettings.KEY_REPLY_ITEMS, "[]").commit()
        assertTrue(loadGlobalRedPacketReplySteps(sp, includeDisabledLegacy = true).isEmpty())
        sp.edit().clear().commit()
        assertTrue(loadGlobalRedPacketReplySteps(sp, includeDisabledLegacy = true).isEmpty())
    }

    @Test
    fun firstTemplateEditMigratesLegacyBindingsAtomically() {
        val sp = RuntimeEnvironment.getApplication().getSharedPreferences("message-block-template-edit", Context.MODE_PRIVATE)
        val template = newMessageBlockTemplate(1).copy(id = "legacy-id", targets = "alice,bob")
        sp.edit().clear().putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(listOf(template))).commit()
        val before = sp.all.toMap()
        val initial = initialMessageBlockState(sp)

        assertEquals(before, sp.all)
        assertTrue(initial.shouldPersist)
        assertEquals(setOf("alice", "bob"), initial.bindings.map { it.targetId }.toSet())
        val edited = initial.templates.single().copy(name = "edited")
        persistMessageBlockTemplates(sp, listOf(edited), initial.bindings, initial.shouldPersist)

        val saved = initialMessageBlockState(sp)
        assertFalse(saved.shouldPersist)
        assertEquals("edited", saved.templates.single().name)
        assertEquals("legacy-id", saved.templates.single().id)
        assertEquals(setOf("alice", "bob"), saved.bindings.map { it.targetId }.toSet())
        assertTrue(saved.bindings.all { "legacy-id" in it.templateIds })
    }

    @Test
    fun firstBindingEditPreservesLegacyTemplatesAndOtherBindings() {
        val sp = RuntimeEnvironment.getApplication().getSharedPreferences("message-block-binding-edit", Context.MODE_PRIVATE)
        val template = newMessageBlockTemplate(1).copy(id = "legacy-id", targets = "alice,bob")
        sp.edit().clear().putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(listOf(template))).commit()
        val initial = initialMessageBlockState(sp)
        val edited = initial.bindings.map { if (it.targetId == "alice") it.copy(enabled = false) else it }
        persistMessageBlockBindings(sp, initial.templates, edited, initial.shouldPersist)

        val saved = initialMessageBlockState(sp)
        assertFalse(saved.shouldPersist)
        assertEquals("legacy-id", saved.templates.single().id)
        assertEquals(setOf("alice", "bob"), saved.bindings.map { it.targetId }.toSet())
        assertFalse(saved.bindings.single { it.targetId == "alice" }.enabled)
        assertTrue(saved.bindings.single { it.targetId == "bob" }.enabled)
    }
}
