package h.Hchat.hooks.items.messageblock

import h.Hchat.preferences.HchatStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageBlockVoipIncomingRegressionTest {
    private val context by lazy { RuntimeEnvironment.getApplication() }
    private val prefs by lazy { HchatStorage.preferences(context, MessageBlockSettings.PREFS_NAME) }
    private val feature = MessageBlockFeature()
    private val caller = "wxid.blocked"
    private val self = "wxid.self"

    @Before
    fun resetSettings() {
        prefs.edit().clear().commit()
    }

    @Test
    fun mapsVideoAndVoiceRoomTypesAndMatchesVoipRulesForBoth() {
        enable()
        savePrivateRule(setOf(MessageBlockSettings.TYPE_VOIP))

        assertEquals(1000053, MessageBlockVoipCallDecision.messageTypeForRoomType(0))
        assertEquals(1000052, MessageBlockVoipCallDecision.messageTypeForRoomType(1))
        assertEquals(0, MessageBlockVoipCallDecision.messageTypeForRoomType(2))
        assertTrue(evaluate(1000053).ruleMatched)
        assertTrue(evaluate(1000052).ruleMatched)
    }

    @Test
    fun disabledFeatureAndTextOnlyRuleDoNotMatchCalls() {
        savePrivateRule(setOf(MessageBlockSettings.TYPE_VOIP))
        val disabled = evaluate(1000052)
        assertFalse(disabled.featureEnabled)
        assertFalse(disabled.ruleMatched)

        enable()
        savePrivateRule(setOf(MessageBlockSettings.TYPE_TEXT))
        assertFalse(evaluate(1000053).ruleMatched)
    }

    @Test
    fun exclusionBindingOverridesMatchingDefaultPrivateVoipRule() {
        enable()
        savePrivateRule(setOf(MessageBlockSettings.TYPE_VOIP))
        val exclude = MessageBlockBinding(
            id = MessageBlockSettings.bindingKey(MessageBlockSettings.TARGET_CONTACT, caller),
            targetType = MessageBlockSettings.TARGET_CONTACT,
            targetId = caller,
            label = "excluded caller",
            enabled = true,
            action = MessageBlockSettings.ACTION_EXCLUDE,
            templateIds = emptySet(),
            customRules = true
        )
        prefs.edit()
            .putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(listOf(exclude)))
            .commit()

        assertFalse(evaluate(1000052).ruleMatched)
    }

    @Test
    fun quickBlockAllStillMatchesPrivateVoiceAndVideoInvites() {
        MessageBlockSettings(context).setQuickBlockedTalkers(mapOf(caller to "blocked caller"), true)

        assertTrue(evaluate(1000052).featureEnabled)
        assertTrue(evaluate(1000052).ruleMatched)
        assertTrue(evaluate(1000053).ruleMatched)
    }

    @Test
    fun outgoingCallsUnknownRoomTypesAndFailedRejectsPassThrough() {
        assertFalse(shouldReject(incoming = false, roomType = 1, matched = true, rejectAvailable = true))
        assertFalse(shouldReject(incoming = true, roomType = 2, matched = true, rejectAvailable = true))
        assertFalse(shouldReject(incoming = true, roomType = 1, matched = true, rejectAvailable = false))
        assertFalse(shouldReject(incoming = true, roomType = 1, matched = true, rejectAvailable = true, generationActive = false))
    }

    @Test
    fun groupOrUnidentifiedCallersDoNotEnterPrivateContactRules() {
        enable()
        savePrivateRule(setOf(MessageBlockSettings.TYPE_VOIP))

        assertFalse(feature.evaluateIncomingVoip(context, "group@chatroom", 1000052, self).privateCaller)
        assertFalse(feature.evaluateIncomingVoip(context, caller, 1000052, "").privateCaller)
        assertFalse(feature.evaluateIncomingVoip(context, caller, 1000052, caller).privateCaller)
    }

    private fun enable() {
        prefs.edit().putBoolean(MessageBlockSettings.KEY_ENABLE, true).commit()
    }

    private fun savePrivateRule(types: Set<String>) {
        MessageBlockSettings(context).saveDefaultPrivateRule(
            MessageBlockDefaultRule(
                group = false,
                label = "test private rule",
                enabled = true,
                customRules = true,
                types = types
            )
        )
    }

    private fun evaluate(type: Int): MessageBlockIncomingVoipEvaluation =
        feature.evaluateIncomingVoip(context, caller, type, self)

    private fun shouldReject(
        incoming: Boolean,
        roomType: Int,
        matched: Boolean,
        rejectAvailable: Boolean,
        generationActive: Boolean = true
    ) = MessageBlockVoipCallDecision.shouldReject(
        generationActive,
        true,
        incoming,
        true,
        roomType,
        matched,
        rejectAvailable
    )
}
