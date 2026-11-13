package h.Hchat.ui.miuix

import android.net.Uri
import android.content.Context
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.model.WeChatMessageTypes
import h.Hchat.hooks.items.autoreply.AutoReplyRule
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyStep
import h.Hchat.hooks.items.customnotify.CustomNotificationSettings
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationSettings
import h.Hchat.preferences.HchatStorage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemovedFeaturesUpgradeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val removedPrefs = listOf(
        "Hchat_text_voice_config", "Hchat_text_speech_config", "Hchat_audio_transform_config",
        "Hchat_voice_preview_config", "Hchat_conversation_tabs", "floating_shortcut_menu"
    )

    private fun entry(type: String, value: Any) = JSONObject().put("type", type).put("value", value)
    private fun backup(prefs: JSONObject) = JSONObject().put("format", "HchatConfigBackup")
        .put("version", 1).put("prefs", prefs)
    private fun tempFile(name: String) = File(context.cacheDir, name).apply { delete() }

    @Test fun oldStepsKeepTheirIdsModesAndContentAcrossRuleRoundTrip() {
        val steps = listOf(
            AutoReplyStep(id = "before", mode = AutoReplySettings.REPLY_TEXT, content = "before"),
            AutoReplyStep(id = "legacy11", mode = 11, content = "", delayMs = 123),
            AutoReplyStep(id = "legacy12", mode = 12, content = "old data", randomDelay = true),
            AutoReplyStep(id = "favorite", mode = 13, content = "favorite-id"),
            AutoReplyStep(id = "after", mode = AutoReplySettings.REPLY_ZHILIA_AI, content = "")
        )
        val rule = AutoReplyRule(id = "rule", name = "preserved", steps = steps)
        val decoded = AutoReplySettings.parseRules(AutoReplySettings.encodeRules(listOf(rule)))
        assertEquals(steps, decoded.single().steps)
        assertEquals(13, AutoReplySettings.REPLY_FAVORITE)
        assertTrue(AutoReplySettings.isRemovedReplyMode(11))
        assertTrue(AutoReplySettings.isRemovedReplyMode(12))
        assertFalse(AutoReplySettings.isAiReplyMode(11))
        assertFalse(AutoReplySettings.isAiReplyMode(12))
        assertTrue(AutoReplySettings.isAiReplyMode(AutoReplySettings.REPLY_ZHILIA_AI))
    }

    @Test fun deletingLegacyStepsNeverCreatesAnUnrequestedTextReply() {
        val removed = AutoReplyStep(id = "legacy", mode = 11, content = "")
        val text = AutoReplyStep(id = "keep", mode = AutoReplySettings.REPLY_TEXT, content = "keep")
        val session = AutoReplySequenceSession(listOf(removed, text), true)
        assertTrue(session.remove(removed.id))
        assertEquals(listOf(text), session.stepsForDraft())
        assertTrue(session.remove(text.id))
        assertTrue(session.stepsForDraft().isEmpty())
    }

    @Test fun intentionallyEmptyLegacyRepliesPersistWithoutChangingNormalDefaults() {
        val auto = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        auto.edit().clear().commit()
        val settings = AutoReplySettings(context)
        assertTrue(settings.autoAcceptSteps().isNotEmpty())
        assertTrue(settings.greetAcceptedSteps().isNotEmpty())
        settings.saveAutoAcceptSteps(emptyList())
        assertTrue(settings.autoAcceptSteps().isNotEmpty())

        settings.saveAutoAcceptSteps(emptyList(), explicitEmpty = true)
        settings.saveGreetAcceptedSteps(emptyList(), explicitEmpty = true)
        val emptyRule = AutoReplyRule(id = "empty-rule", steps = emptyList())
        settings.saveRules(listOf(emptyRule), explicitlyEmptyRuleId = emptyRule.id)
        assertEquals(AutoReplySettings.encodeRules(listOf(emptyRule)), auto.getString(AutoReplySettings.KEY_RULES, null))
        assertTrue(settings.autoAcceptSteps().isEmpty())
        assertTrue(settings.greetAcceptedSteps().isEmpty())
        assertTrue(settings.isRuleExplicitlyEmpty(emptyRule.id))
        settings.saveRules(settings.rules())
        assertTrue(settings.isRuleExplicitlyEmpty(emptyRule.id))

        val file = tempFile("explicit-empty-config.json")
        try {
            assertTrue(HchatConfigBackup.exportToUri(context, Uri.fromFile(file)) > 0)
            auto.edit().clear().commit()
            assertTrue(HchatConfigBackup.importFromUri(context, Uri.fromFile(file)) > 0)
            val restored = AutoReplySettings(context)
            assertTrue(restored.autoAcceptSteps().isEmpty())
            assertTrue(restored.greetAcceptedSteps().isEmpty())
            assertTrue(restored.isRuleExplicitlyEmpty(emptyRule.id))
            assertTrue(restored.rules().single().steps.isEmpty())

            restored.saveAutoAcceptSteps(listOf(AutoReplyStep(id = "new", content = "hello")))
            assertFalse(restored.isAutoAcceptExplicitlyEmpty())
            assertTrue(restored.isGreetAcceptedExplicitlyEmpty())
            restored.saveRules(listOf(emptyRule.copy(steps = listOf(AutoReplyStep(content = "hello")))))
            assertFalse(restored.isRuleExplicitlyEmpty(emptyRule.id))
            assertTrue(restored.isGreetAcceptedExplicitlyEmpty())
        } finally { file.delete() }
    }

    @Test fun removedReplyStepsAreSkippedByTheRuntime() {
        val runtime = h.Hchat.hooks.items.autoreply.AutoReplyRuntime
        val messageType = runtime.javaClass.declaredClasses.single { it.simpleName == "MessageContext" }
        val constructor = messageType.declaredConstructors.single { it.parameterCount == 8 }.apply { isAccessible = true }
        val message = constructor.newInstance("target", "sender", "hello", 1L, false, false, false, false)
        val execute = runtime.javaClass.getDeclaredMethod("executeSteps", Context::class.java, String::class.java,
            List::class.java, messageType, java.lang.Long.TYPE, java.lang.Boolean.TYPE).apply { isAccessible = true }
        val removed = listOf(AutoReplyStep(mode = 11, content = "", delayMs = 600000),
            AutoReplyStep(mode = 12, content = "old", delayMs = 600000))
        val startedAt = System.nanoTime()
        assertEquals(false, execute.invoke(runtime, context, "target", removed, message, 1200L, false))
        assertTrue("Removed-only steps must skip the initial delay", (System.nanoTime() - startedAt) / 1_000_000L < 500L)
    }

    @Test fun removedOnlyRulesDoNotConsumeReplyCounts() {
        val runtime = h.Hchat.hooks.items.autoreply.AutoReplyRuntime
        val prefs = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        prefs.edit().clear().commit()
        val talker = "removed-only-count-target"
        val sender = "removed-only-count-sender"
        val rule = AutoReplyRule(
            id = "removed-only-count-rule",
            matchType = AutoReplySettings.MATCH_ANY,
            maxReplyCount = 1,
            steps = listOf(AutoReplyStep(mode = 11), AutoReplyStep(mode = 12))
        )
        val settings = AutoReplySettings(context).apply { saveRules(listOf(rule)) }
        val rawMessage = WeChatMessage.fromTransient(talker, sender, "hello", 1L, false, WeChatMessageTypes.TEXT, 1L)
        val observed = WeChatMessageObserveApi.ObservedMessage.transientMessage(
            "test", WeChatMessageObserveApi.Kind.TEXT, talker, sender, "hello", "", "", false, false, rawMessage
        )
        val countsField = runtime.javaClass.getDeclaredField("replyCounts").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val counts = countsField.get(runtime) as MutableMap<String, Int>
        val countKey = "${rule.id}|$talker|$sender"
        counts.remove(countKey)
        val handle = runtime.javaClass.getDeclaredMethod(
            "handleAutoReply", Context::class.java, AutoReplySettings::class.java, WeChatMessageObserveApi.ObservedMessage::class.java
        ).apply { isAccessible = true }
        try {
            handle.invoke(runtime, context, settings, observed)
            assertFalse("A rule with only removed steps must not increment its reply count", counts.containsKey(countKey))
        } finally {
            counts.remove(countKey)
            prefs.edit().clear().commit()
        }
    }

    @Test fun removedStepsKeepExecutableOrderAndDoNotAddAnExtraStepGap() {
        val runtime = h.Hchat.hooks.items.autoreply.AutoReplyRuntime
        val first = AutoReplyStep(id = "first", mode = AutoReplySettings.REPLY_TEXT, content = "first")
        val second = AutoReplyStep(id = "second", mode = AutoReplySettings.REPLY_TEXT, content = "second")
        val steps = listOf(
            AutoReplyStep(id = "old-before", mode = 11), first,
            AutoReplyStep(id = "old-middle", mode = 12), second,
            AutoReplyStep(id = "old-after", mode = 11)
        )
        assertEquals(listOf(first, second), runtime.filterRemovedReplySteps(steps))

        val messageType = runtime.javaClass.declaredClasses.single { it.simpleName == "MessageContext" }
        val constructor = messageType.declaredConstructors.single { it.parameterCount == 8 }.apply { isAccessible = true }
        val message = constructor.newInstance("target", "sender", "hello", 1L, false, false, false, false)
        val execute = runtime.javaClass.getDeclaredMethod("executeSteps", Context::class.java, String::class.java,
            List::class.java, messageType, java.lang.Long.TYPE, java.lang.Boolean.TYPE).apply { isAccessible = true }
        val priorMessageApi = WeChatApis.message().sender()
        WeChatApis.init(null)
        try {
            val startedAt = System.nanoTime()
            assertEquals(false, execute.invoke(runtime, context, "target", steps, message, 0L, false))
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            assertTrue("Two executable steps should incur one 300 ms gap, not gaps around removed steps", elapsedMs < 500L)
        } finally {
            WeChatApis.init(priorMessageApi)
        }
    }

    @Test fun oldBackupIgnoresRemovedDataAndRestoresRetainedSettings() {
        val auto = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        val custom = HchatStorage.preferences(context, CustomNotificationSettings.PREFS_NAME)
        val keyword = HchatStorage.preferences(context, KeywordNotificationSettings.PREFS_NAME)
        auto.edit().clear().putString("xiaozhi_console_token", "local historical value")
            .putString(AutoReplySettings.KEY_RULES, "old rules").commit()
        custom.edit().clear().commit()
        keyword.edit().clear().commit()
        removedPrefs.forEach { HchatStorage.preferences(context, it).edit().clear().putString("sentinel", "local").commit() }
        val steps = listOf(AutoReplyStep(id = "legacy", mode = 11, content = ""),
            AutoReplyStep(id = "keep", mode = AutoReplySettings.REPLY_TEXT, content = "hello"))
        val rules = AutoReplySettings.encodeRules(listOf(AutoReplyRule(id = "r", steps = steps)))
        val friendSteps = listOf(AutoReplyStep(id = "old-voice", mode = 12, content = ""),
            AutoReplyStep(id = "greeting", mode = AutoReplySettings.REPLY_TEXT, content = "hello friend"))
        val configs = JSONObject()
            .put("one", JSONObject().put("apiKey", "test-key").put("modelName", "model-one"))
            .put("two", JSONObject().put("apiKey", "test-key-two").put("modelName", "model-two"))
        val incoming = JSONObject().put(AutoReplySettings.PREFS_NAME, JSONObject()
            .put("xiaozhi_console_token", entry("string", "ignored"))
            .put(AutoReplySettings.KEY_ENABLE, entry("boolean", true))
            .put(AutoReplySettings.KEY_RULES, entry("string", rules))
            .put(AutoReplySettings.KEY_AUTO_ACCEPT_STEPS, entry("string", AutoReplySettings.encodeSteps(friendSteps)))
            .put(AutoReplySettings.KEY_GREET_ACCEPTED_STEPS, entry("string", AutoReplySettings.encodeSteps(friendSteps)))
            .put(AutoReplySettings.KEY_ZHILIA_CONFIGS, entry("string", configs.toString()))
            .put(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, entry("string", "two")))
            .put(CustomNotificationSettings.PREFS_NAME, JSONObject().put("custom_marker", entry("string", "custom")))
            .put(KeywordNotificationSettings.PREFS_NAME, JSONObject().put("keyword_marker", entry("string", "keyword")))
        removedPrefs.forEach { incoming.put(it, JSONObject().put("sentinel", entry("string", "replaced"))) }
        val file = tempFile("old-config.json")
        try {
            file.writeText(backup(incoming).toString())
            assertEquals(8, HchatConfigBackup.importFromUri(context, Uri.fromFile(file)))
            assertEquals("local historical value", auto.getString("xiaozhi_console_token", null))
            assertTrue(AutoReplySettings(context).isEnabled())
            assertEquals(steps, AutoReplySettings(context).rules().single().steps)
            assertEquals(friendSteps, AutoReplySettings(context).autoAcceptSteps())
            assertEquals(friendSteps, AutoReplySettings(context).greetAcceptedSteps())
            assertEquals(setOf("one", "two"), AutoReplySettings(context).zhiliaConfigs().map { it.name }.toSet())
            assertEquals("model-two", AutoReplySettings(context).activeZhiliaConfig().model)
            assertEquals("custom", custom.getString("custom_marker", null))
            assertEquals("keyword", keyword.getString("keyword_marker", null))
            removedPrefs.forEach { assertEquals("local", HchatStorage.preferences(context, it).getString("sentinel", null)) }

            assertTrue(HchatConfigBackup.exportToUri(context, Uri.fromFile(file)) > 0)
            val exported = JSONObject(file.readText()).getJSONObject("prefs")
            removedPrefs.forEach { assertFalse(exported.has(it)) }
            assertFalse(exported.getJSONObject(AutoReplySettings.PREFS_NAME).has("xiaozhi_console_token"))
            assertTrue(exported.getJSONObject(AutoReplySettings.PREFS_NAME).has(AutoReplySettings.KEY_RULES))
        } finally { file.delete() }
    }

    @Test fun oldBackupContainingOnlyRemovedKeysDoesNotClearAutoReply() {
        val auto = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        auto.edit().clear().putBoolean(AutoReplySettings.KEY_ENABLE, true)
            .putString(AutoReplySettings.KEY_RULES, "keep").commit()
        val file = tempFile("obsolete-only.json")
        try {
            file.writeText(backup(JSONObject().put(AutoReplySettings.PREFS_NAME,
                JSONObject().put("xiaozhi_serve_url", entry("string", "ignored")))).toString())
            assertEquals(0, HchatConfigBackup.importFromUri(context, Uri.fromFile(file)))
            assertTrue(auto.getBoolean(AutoReplySettings.KEY_ENABLE, false))
            assertEquals("keep", auto.getString(AutoReplySettings.KEY_RULES, null))
        } finally { file.delete() }
    }
    @Test fun emptyRetainedBackupEntriesKeepExistingClearSemantics() {
        val auto = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        val custom = HchatStorage.preferences(context, CustomNotificationSettings.PREFS_NAME)
        auto.edit().clear().putBoolean(AutoReplySettings.KEY_ENABLE, true)
            .putString("xiaozhi_serve_url", "local historical value").commit()
        custom.edit().clear().putString("marker", "old").commit()
        val file = tempFile("empty-sections.json")
        try {
            file.writeText(backup(JSONObject().put(AutoReplySettings.PREFS_NAME, JSONObject())
                .put(CustomNotificationSettings.PREFS_NAME, JSONObject())).toString())
            assertEquals(0, HchatConfigBackup.importFromUri(context, Uri.fromFile(file)))
            assertFalse(auto.contains(AutoReplySettings.KEY_ENABLE))
            assertEquals("local historical value", auto.getString("xiaozhi_serve_url", null))
            assertFalse(custom.contains("marker"))
        } finally { file.delete() }
    }

    @Test fun registeredFeaturesKeepTheRetainedEntries() {
        val ids = h.Hchat.hooks.core.FeatureRegistry.createDefaultManager().features().map { it.featureId() }.toSet()
        assertEquals(83, ids.size)
        assertTrue(ids.containsAll(listOf("auto_reply", "conversation_group", "voice_forward", "custom_notification", "keyword_notification")))
        assertTrue(ids.intersect(setOf("text_voice", "text_speech", "audio_transform", "voice_preview", "conversation_tabs", "floating_shortcut_menu")).isEmpty())
    }

}
