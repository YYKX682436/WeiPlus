package h.Hchat.hooks.items.sendbutton

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.event.Events
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the single shared send-button Hook used by native features. */
class NativeSendButtonFeature : BaseFeature() {
    @Volatile
    private var chatPageSubscription: Any? = null
    private val chatPageRetryAttempted = AtomicBoolean(false)

    override fun featureId(): String = ID

    override fun name(): String = "原生发送按钮"

    override fun onFeatureInstall(context: FeatureContext) {
        observeChatPage(context)
        scheduleInstall(context)
        subscribe(Events.DexReady::class.java) {
            observeChatPage(context)
            scheduleInstall(context)
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        chatPageSubscription = null
        chatPageRetryAttempted.set(false)
        NativeSendButtonHook.resetForGeneration()
    }

    @Synchronized
    private fun observeChatPage(context: FeatureContext) {
        if (chatPageSubscription != null) return
        val chatPage = WeChatApis.chatPage() ?: return
        chatPageSubscription = trackSubscription(chatPage.subscribe { event ->
            if (event.isEnter && chatPageRetryAttempted.compareAndSet(false, true)) {
                // ChatFooter's click listener may not be available until the first chat opens.
                scheduleInstall(context)
            }
        })
    }

    private fun scheduleInstall(context: FeatureContext) {
        DexInstallScheduler.schedule(
            ID,
            name(),
            stage = DexInstallScheduler.Stage.WARMUP
        ) {
            NativeSendButtonHook.install(context)
        }
    }

    companion object {
        const val ID = "shared_native_send_button"
    }
}
