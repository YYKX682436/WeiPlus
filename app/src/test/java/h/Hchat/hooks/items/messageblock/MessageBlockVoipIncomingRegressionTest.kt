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
    fun ilinkCallbacksMapOnlyVoiceVideoAndLeaveMultiTalkUnknown() {
        assertEquals(1000052, MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(1))
        assertEquals(1000053, MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(2))
        assertEquals(0, MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(0))
        assertEquals(0, MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(3))
        assertEquals(0, MessageBlockVoipCallDecision.messageTypeForIlinkRoomType(4))

        assertEquals(1000052, MessageBlockVoipCallDecision.messageTypeForIlinkRecordType(0))
        assertEquals(1000053, MessageBlockVoipCallDecision.messageTypeForIlinkRecordType(1))
        assertEquals(1000052, MessageBlockVoipCallDecision.messageTypeForIlinkRecordType(2))
        assertEquals(0, MessageBlockVoipCallDecision.messageTypeForIlinkRecordType(3))
    }

    @Test
    fun allBlockedIncomingVoiceVideoCallResultsAreSuppressedButOutgoingAndGroupPass() {
        listOf(0, 1, 2).forEach { roomType ->
            assertTrue(MessageBlockVoipCallDecision.isIncomingPrivateVoipRecord(false, roomType))
            assertFalse(MessageBlockVoipCallDecision.isIncomingPrivateVoipRecord(true, roomType))
        }
        assertFalse(MessageBlockVoipCallDecision.isIncomingPrivateVoipRecord(false, 3))
        assertFalse(MessageBlockVoipCallDecision.isIncomingPrivateVoipRecord(false, 4))
    }

    @Test
    fun onlyIncomingVoipRowsAreEligibleForGenericNotificationSuppression() {
        assertTrue(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(50, 50, 0))
        assertTrue(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(50, 50, 2))
        assertTrue(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(1000053, 1000053, 0))
        assertFalse(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(50, 50, 1))
        assertFalse(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(1000053, 1000053, 2))
        assertFalse(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(50, 1, 0))
        assertFalse(MessageBlockVoipCallDecision.isIncomingVoipMessageNotification(50, 1000052, 0))
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
    fun matchedInvitesAreStatelesslyIgnoredWhileOutgoingCallsPassThrough() {
        repeat(2) {
            assertTrue(shouldIgnore(incoming = true, roomType = 1, matched = true))
        }
        assertFalse(shouldIgnore(incoming = false, roomType = 1, matched = true))
        assertFalse(shouldIgnore(incoming = true, roomType = 2, matched = true))
    }

    @Test
    fun ilinkPathRequiresExactIncomingInviteAndBothPossibleVoipTypesToMatch() {
        assertTrue(MessageBlockVoipCallDecision.isIlinkInviteShape("ilinkvoip", true, true))
        assertFalse(MessageBlockVoipCallDecision.isIlinkInviteShape("other", true, true))
        assertFalse(MessageBlockVoipCallDecision.isIlinkInviteShape("ilinkvoip", false, true))
        assertFalse(MessageBlockVoipCallDecision.isIlinkInviteShape("ilinkvoip", true, false))

        repeat(2) {
            assertTrue(
                MessageBlockVoipCallDecision.shouldIgnoreMessageType(
                    true, true, true, true, true, 1000052, true
                )
            )
            assertTrue(
                MessageBlockVoipCallDecision.shouldIgnoreMessageType(
                    true, true, true, true, true, 1000053, true
                )
            )
        }
        assertFalse(
            MessageBlockVoipCallDecision.shouldIgnoreMessageType(
                true, true, true, true, true, 1000052, false
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.shouldIgnoreMessageType(
                true, true, true, true, false, 1000052, true
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.shouldIgnoreMessageType(
                true, true, true, true, true, 50, true
            )
        )
        assertTrue(
            MessageBlockVoipCallDecision.shouldIgnoreUnknownVoipVariant(
                true, true, true, true, true, true, true
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.shouldIgnoreUnknownVoipVariant(
                true, true, true, true, true, true, false
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.shouldIgnoreUnknownVoipVariant(
                true, true, false, true, true, true, true
            )
        )
    }

    @Test
    fun receiverIdentityFallbackIsOnlyAllowedWhenAccountApiIsUnavailable() {
        assertTrue(
            MessageBlockVoipCallDecision.isReceiverIdentityTrusted(
                true,
                true,
                true,
                false
            )
        )
        assertTrue(
            MessageBlockVoipCallDecision.isReceiverIdentityTrusted(
                true,
                false,
                false,
                true
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.isReceiverIdentityTrusted(
                true,
                false,
                false,
                false
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.isReceiverIdentityTrusted(
                true,
                true,
                false,
                true
            )
        )
        assertFalse(
            MessageBlockVoipCallDecision.isReceiverIdentityTrusted(
                false,
                false,
                false,
                true
            )
        )
    }

    @Test
    fun nonmatchesParserFailuresAndUntrustedInputsFailOpen() {
        assertFalse(shouldIgnore(incoming = true, roomType = 1, matched = false))
        assertFalse(shouldIgnore(incoming = true, roomType = 1, matched = true, generationActive = false))
        assertFalse(shouldIgnore(incoming = true, roomType = 1, matched = true, settingsEnabled = false))
        // A parser failure leaves the invite unparsed and the Hooker proceeds with WeChat's chain.
        assertFalse(shouldIgnore(incoming = true, roomType = 1, matched = true, inviteParsed = false))
        assertFalse(shouldIgnore(incoming = true, roomType = 1, matched = true, privateCaller = false))
    }

    @Test
    fun groupOrUnidentifiedCallersDoNotEnterPrivateContactRules() {
        enable()
        savePrivateRule(setOf(MessageBlockSettings.TYPE_VOIP))

        assertFalse(feature.evaluateIncomingVoip(context, "group@chatroom", 1000052, self).privateCaller)
        assertFalse(feature.evaluateIncomingVoip(context, caller, 1000052, "").privateCaller)
        assertFalse(feature.evaluateIncomingVoip(context, caller, 1000052, caller).privateCaller)
    }

    @Test
    fun verifiedIncomingVoipMessageCanReuseRulesWithoutSelfIdButStillRejectGroup() {
        enable()
        savePrivateRule(setOf(MessageBlockSettings.TYPE_VOIP))

        val verified = feature.evaluateIncomingVoip(context, caller, 1000052, "", true)
        assertTrue(verified.privateCaller)
        assertTrue(verified.ruleMatched)
        assertFalse(
            feature.evaluateIncomingVoip(context, "group@chatroom", 1000052, "", true).privateCaller
        )
        assertFalse(feature.evaluateIncomingVoip(context, caller, 1000052, "", false).privateCaller)
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

    private fun shouldIgnore(
        incoming: Boolean,
        roomType: Int,
        matched: Boolean,
        generationActive: Boolean = true,
        settingsEnabled: Boolean = true,
        inviteParsed: Boolean = true,
        privateCaller: Boolean = true
    ) = MessageBlockVoipCallDecision.shouldIgnore(
        generationActive,
        settingsEnabled,
        incoming,
        inviteParsed,
        privateCaller,
        roomType,
        matched
    )
}
