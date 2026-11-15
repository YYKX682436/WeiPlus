package h.Hchat.hooks.items.autoreply

import android.content.Context
import android.os.SystemClock
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.preferences.HchatStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoReplyAiCoordinationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun actualRuleDecisionIsSharedAndCountOrCooldownDoesNotOwnNewMessage() {
        val prefs = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        prefs.edit().clear().putBoolean(AutoReplySettings.KEY_ENABLE, true).commit()
        val talker = "friend_${UUID.randomUUID()}"
        val rule = AutoReplyRule(id = UUID.randomUUID().toString(), matchType = AutoReplySettings.MATCH_ANY,
            maxReplyCount = 1, steps = listOf(AutoReplyStep(content = "回复")))
        val settings = AutoReplySettings(context)
        settings.saveRules(listOf(rule))
        fun message(id: Long): WeChatMessageObserveApi.ObservedMessage {
            val native = WeChatMessage.fromTransient(talker, talker, "hi", id * 1000, false, 1, id, "", "")
            return WeChatMessageObserveApi.ObservedMessage.transientMessage("message_db", WeChatMessageObserveApi.Kind.TEXT,
                talker, talker, "hi", "", "", false, false, native)
        }
        AutoReplyRuntime.setActive(true)
        try {
            val first = AutoReplyRuntime.decision(context, message(1))
            val duplicate = AutoReplyRuntime.decision(context, message(1))
            assertSame(first, duplicate)
            assertTrue(first.get(5, TimeUnit.SECONDS))
            assertFalse(AutoReplyRuntime.decision(context, message(2)).get(5, TimeUnit.SECONDS))

            val cooling = rule.copy(id = UUID.randomUUID().toString(), maxReplyCount = 0, cooldownSeconds = 60)
            settings.saveRules(listOf(cooling))
            val field = AutoReplyRuntime::class.java.getDeclaredField("lastReplyTimes").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") val times = field.get(AutoReplyRuntime) as MutableMap<String, Long>
            times["${cooling.id}|$talker"] = SystemClock.elapsedRealtime()
            assertFalse(AutoReplyRuntime.decision(context, message(3)).get(5, TimeUnit.SECONDS))
            times.remove("${cooling.id}|$talker")
        } finally {
            AutoReplyRuntime.setActive(false)
            prefs.edit().clear().commit()
        }
    }

    @Test fun aSlowRuleInOneConversationDoesNotBlockAnotherConversation() {
        val prefs = HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME)
        prefs.edit().clear().putBoolean(AutoReplySettings.KEY_ENABLE, true).commit()
        val prefix = UUID.randomUUID().toString()
        AutoReplySettings(context).saveRules(listOf(AutoReplyRule(id = prefix, matchType = AutoReplySettings.MATCH_ANY,
            steps = listOf(AutoReplyStep(content = "回复", delayMs = 1800)))))
        fun message(talker: String, id: Long): WeChatMessageObserveApi.ObservedMessage {
            val native = WeChatMessage.fromTransient(talker, talker, "hi", id * 1000, false, 1, id, "", "")
            return WeChatMessageObserveApi.ObservedMessage.transientMessage("message_db", WeChatMessageObserveApi.Kind.TEXT,
                talker, talker, "hi", "", "", false, false, native)
        }
        AutoReplyRuntime.setActive(true)
        try {
            assertTrue(AutoReplyRuntime.decision(context, message("${prefix}a", 11)).get(5, TimeUnit.SECONDS))
            val same = AutoReplyRuntime.decision(context, message("${prefix}a", 12))
            val other = AutoReplyRuntime.decision(context, message("${prefix}b", 13))
            assertTrue(other.get(1000, TimeUnit.MILLISECONDS))
            assertFalse("Same-conversation processing must remain sequential", same.isDone)
            assertTrue(same.get(5, TimeUnit.SECONDS))
        } finally {
            AutoReplyRuntime.setActive(false)
            prefs.edit().clear().commit()
        }
    }
}
