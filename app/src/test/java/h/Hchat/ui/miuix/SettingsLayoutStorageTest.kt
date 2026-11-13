package h.Hchat.ui.miuix

import android.app.Activity
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ComposeView
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyRule
import h.Hchat.hooks.items.customnotify.CustomNotificationSettings
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitSettings
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelSettings
import h.Hchat.hooks.items.messageforward.MessageForwardSettings
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditSettings
import h.Hchat.hooks.items.quickmoments.QuickMomentsSettings
import h.Hchat.hooks.items.quickterminate.QuickTerminateSettings
import h.Hchat.hooks.items.settings.SettingsEntrySettings
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsLayoutStorageTest {
    private class Provider(private val id: String) : FeatureSettingsProvider {
        override fun featureId() = id
        override fun title() = id
        override fun subtitle() = ""
    }

    @Test
    fun `opening pages with fresh isolated settings does not initialize keys`() = checkNoWrites(seedExistingData = false)

    @Test
    fun `opening pages preserves existing isolated settings and records`() = checkNoWrites(seedExistingData = true)

    private fun checkNoWrites(seedExistingData: Boolean) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val names = listOf(
            MessageForwardSettings.PREFS_NAME, RemoveForwardLimitSettings.PREFS_NAME,
            QuickMomentsSettings.PREFS_NAME, QuickTerminateSettings.PREFS_NAME,
            QuickGroupChatLabelSettings.PREFS_NAME, QuickContactEditSettings.PREFS_NAME,
            SettingsEntrySettings.PREFS_NAME, AutoReplySettings.PREFS_NAME, CustomNotificationSettings.PREFS_NAME,
            ScheduledTaskSettings.PREFS_NAME, KeywordNotificationSettings.PREFS_NAME,
            MessageBlockSettings.PREFS_NAME
        )
        val prefs = names.associateWith { HchatStorage.preferences(activity, it) }
        prefs.values.forEach { it.edit().clear().commit() }
        if (seedExistingData) {
            prefs.getValue(MessageForwardSettings.PREFS_NAME).edit()
                .putBoolean(MessageForwardSettings.KEY_ENABLE, true)
                .putBoolean(MessageForwardSettings.KEY_MULTI_MOMENTS_ENABLE, false)
                .putString("unknown_legacy_key", "preserve")
                .commit()
            prefs.getValue(AutoReplySettings.PREFS_NAME).edit()
                .putBoolean(AutoReplySettings.KEY_ENABLE, false)
                .putString(AutoReplySettings.KEY_EXCLUDED_TALKERS, "")
                .commit()
            AutoReplySettings(activity).saveRules(listOf(AutoReplyRule(id = "rule-1", name = "kept")))
            ScheduledTaskSettings(activity).saveTasks(listOf(ScheduledTaskSettings.newDraft(1000000L).copy(id = "task-1")))
            val legacyTemplate = newMessageBlockTemplate(1).copy(id = "legacy-template", targets = "legacy-contact")
            prefs.getValue(MessageBlockSettings.PREFS_NAME).edit()
                .putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(listOf(legacyTemplate)))
                .commit()
        }
        val before = prefs.mapValues { it.value.all.toMap() }
        assertFalse(prefs.getValue(MessageForwardSettings.PREFS_NAME).contains(MessageForwardSettings.KEY_FAVORITE_FORWARD_ENABLE))

        val rendered = mutableSetOf<Int>()
        try {
            for (stage in 0..6) {
                val owner = EmbeddedComposeOwner()
                val view = ComposeView(activity)
                owner.install(activity.window.decorView)
                owner.install(view)
                owner.installComposition(view)
                owner.attach()
                view.setContent {
                    HchatMiuixTheme(activity) {
                when (stage) {
                    0 -> Column {
                        listOf("forward.menu", "forward.multi", "forward.favorite", "forward.moments", "remove_forward_limit",
                            "quick_moments", "quick_terminate", "quick_group_chat_label", "quick_contact_edit")
                            .forEach { InlineSettingSwitch(activity, it) }
                        SettingsEntryControls(prefs.getValue(SettingsEntrySettings.PREFS_NAME))
                    }
                    1, 2, 3 -> AutoReplyMiuixPage(activity, Provider("auto_reply"), {},
                        when (stage) {
                            2 -> AutoReplyInitialScene.AUTO_ACCEPT
                            3 -> AutoReplyInitialScene.GREET_ACCEPTED
                            else -> AutoReplyInitialScene.MAIN
                        }, emptyList(), {})
                    4 -> CustomNotificationMiuixPage(activity, Provider("custom_notification"), {}, emptyList(), {})
                    5 -> ScheduledTaskMiuixPage(activity, Provider("scheduled_task"), {})
                    else -> MessageBlockMiuixPage(activity, Provider("message_block"), {})
                }
                SideEffect { rendered.add(stage) }
                    }
                }
                try {
                    activity.setContentView(view)
                    view.createComposition()
                    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                } finally {
                    view.disposeComposition()
                    owner.destroy()
                }
            }
            assertEquals((0..6).toSet(), rendered)
            assertEquals(before, prefs.mapValues { it.value.all.toMap() })
        } finally {
            activity.finish()
        }
    }
}
