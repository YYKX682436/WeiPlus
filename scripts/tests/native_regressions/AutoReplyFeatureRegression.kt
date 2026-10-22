import android.content.Context
import android.os.Handler
import h.Hchat.event.EventBus
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.model.WeChatMessageTypes
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.autoreply.AutoReplyFeature
import h.Hchat.hooks.items.autoreply.AutoReplyFriendRequestHook
import h.Hchat.hooks.items.autoreply.AutoReplyRuntime
import h.Hchat.hooks.items.autoreply.TestAutoReplySettings
import java.io.File

object AutoReplyFeatureRegression {
    fun run() {
        normalObservationAndRepeatedReady()
        missingObserverFallbackRecoveryAndRecreation()
    }

    private fun normalObservationAndRepeatedReady() {
        TestAutoReplySettings.reset()
        AutoReplyFriendRequestHook.uninstall()
        AutoReplyRuntime.reset()
        val observer = WeChatMessageObserveApi(isAvailable = true)
        WeChatApis.messageObserveApi = observer
        WeChatApis.databaseChangesApi = null
        val bus = EventBus().apply { postSticky(Events.DexReady()) }
        val context = featureContext(bus, "normal")
        val feature = AutoReplyFeature()
        feature.install(context)
        check(observer.subscribeCalls == 1 && observer.subscriberCount == 1)
        check(bus.subscriberCount(Events.MessageReceived::class.java) == 0) {
            "Fallback listener must be removed after the observer subscribes"
        }

        observer.emit(observedMessage(7001L, 1_800_000_001_000L, "normal observer"))
        check(AutoReplyRuntime.handledMessages.single().getContent() == "normal observer")
        bus.postSticky(Events.DexReady())
        bus.postSticky(Events.DexReady())
        check(observer.subscribeCalls == 1) { "Repeated DexReady created duplicate message subscriptions" }

        feature.destroy(context)
        check(observer.subscriberCount == 0) { "Destroy did not release the observer subscription" }
        check(bus.subscriberCount(Events.DexReady::class.java) == 0)
    }

    private fun missingObserverFallbackRecoveryAndRecreation() {
        TestAutoReplySettings.reset()
        AutoReplyFriendRequestHook.uninstall()
        AutoReplyRuntime.reset()
        WeChatApis.messageObserveApi = null
        WeChatApis.databaseChangesApi = null
        val bus = EventBus().apply { postSticky(Events.DexReady()) }
        val context = featureContext(bus, "recovery")
        val feature = AutoReplyFeature()
        feature.install(context)
        check(bus.subscriberCount(Events.MessageReceived::class.java) == 1)

        val event = Events.MessageReceived(
            outgoing = false,
            content = "fallback ordinary message",
            msgType = "1",
            sender = "wxid_alice",
            talker = "wxid_alice",
            createTimeSeconds = 1_800_000_001L,
            msgSvrId = 8001L,
            source = "add_msg"
        )
        bus.post(event)
        check(AutoReplyRuntime.handledMessages.size == 1) {
            "Fallback must preserve ordinary auto-reply message delivery, not just friend requests"
        }
        check(AutoReplyRuntime.handledMessages.single().getContent() == "fallback ordinary message")
        check(Handler.pendingForTests() == 1) { "Missing observer should schedule one bounded retry" }

        val observer = WeChatMessageObserveApi(isAvailable = true)
        WeChatApis.messageObserveApi = observer
        check(Handler.runNextForTests()) { "Scheduled retry was not runnable" }
        check(observer.subscribeCalls == 1 && observer.subscriberCount == 1)
        check(bus.subscriberCount(Events.MessageReceived::class.java) == 0)
        observer.emit(observedMessage(8001L, 1_800_000_001_000L, "fallback ordinary message"))
        check(AutoReplyRuntime.handledMessages.size == 1) { "Transition replayed a fallback message" }
        observer.emit(observedMessage(8002L, 1_800_000_002_000L, "after observer recovery"))
        check(AutoReplyRuntime.handledMessages.size == 2)
        TestAutoReplySettings.configure(messageReplies = false, autoAccept = false)
        observer.emit(observedMessage(8003L, 1_800_000_003_000L, "while disabled"))
        check(AutoReplyRuntime.handledMessages.size == 2) { "Disabling autoreply must take effect without recreating the observer" }
        TestAutoReplySettings.configure(messageReplies = true, autoAccept = false)
        observer.emit(observedMessage(8004L, 1_800_000_004_000L, "after live enable"))
        check(AutoReplyRuntime.handledMessages.size == 3) { "Enabling autoreply must take effect immediately" }
        bus.postSticky(Events.DexReady())
        bus.postSticky(Events.DexReady())
        check(observer.subscribeCalls == 1) { "Observer recovery subscribed more than once" }

        feature.destroy(context)
        check(observer.subscriberCount == 0)
        check(TestAutoReplySettings.preferences.listenerCount == 0)
        check(TestAutoReplySettings.preferences.listenerCount == 0) {
            "Destroy retained the actual settings preference listener"
        }
        check(bus.subscriberCount(Events.MessageReceived::class.java) == 0)
        check(Handler.pendingForTests() == 0) { "Destroy retained a retry callback" }

        feature.install(context)
        check(observer.subscribeCalls == 2 && observer.subscriberCount == 1) {
            "Destroy and recreate did not install exactly one fresh observer subscription"
        }
        feature.destroy(context)
        check(observer.subscriberCount == 0)
    }

    private fun featureContext(bus: EventBus, name: String): FeatureContext {
        val dir = File(System.getProperty("java.io.tmpdir"), "hchat-autoreply-$name-${System.nanoTime()}")
        return FeatureContext(Context("com.tencent.mm", dir), bus)
    }

    private fun observedMessage(
        serverId: Long,
        createTime: Long,
        content: String
    ): WeChatMessageObserveApi.ObservedMessage {
        val message = WeChatMessage.fromTransient(
            "wxid_alice", "wxid_alice", content, createTime, false,
            WeChatMessageTypes.TEXT, serverId, "", "wxid_self"
        )
        return WeChatMessageObserveApi.ObservedMessage.transientMessage(
            "add_msg", WeChatMessageObserveApi.Kind.TEXT, "wxid_alice", "wxid_alice",
            content, "", "", false, false, message
        )
    }
}
