import android.content.Context
import h.Hchat.event.EventBus
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveFeature
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveRuntime
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveSettings
import h.Hchat.preferences.HchatStorage
import io.fastkv.FastKV
import java.io.File

object KeepAliveFeatureRegression {
    fun run() {
        val root = File(System.getProperty("java.io.tmpdir"), "hchat-keepalive-${System.nanoTime()}")
        val module = Context("h.Hchat", File(root, "module"))
        val official = Context("com.tencent.mm", File(root, "official"))
        val modified = Context("com.tencent.mm.wx", File(root, "modified"))
        val officialContext = FeatureContext(official, EventBus(), module)
        val modifiedContext = FeatureContext(modified, EventBus(), module)
        val officialFeature = WeChatKeepAliveFeature()
        val modifiedFeature = WeChatKeepAliveFeature()

        WeChatKeepAliveRuntime.reset()
        officialFeature.install(officialContext)
        modifiedFeature.install(modifiedContext)
        val officialPrefs = HchatStorage.preferences(official, WeChatKeepAliveSettings.PREFS_NAME) as FastKV
        val modifiedPrefs = HchatStorage.preferences(modified, WeChatKeepAliveSettings.PREFS_NAME) as FastKV

        officialPrefs.setBoolean(WeChatKeepAliveSettings.KEY_ENABLE, true)
        officialPrefs.setBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, true)
        check(WeChatKeepAliveRuntime.applyCalls.takeLast(2).all { it.hostPackage == official.packageName }) {
            "Official setting changes did not re-apply only the official host"
        }
        modifiedPrefs.setBoolean(WeChatKeepAliveSettings.KEY_ENABLE, true)
        modifiedPrefs.setBoolean(WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT, true)
        check(WeChatKeepAliveRuntime.applyCalls.takeLast(2).all { it.hostPackage == modified.packageName }) {
            "Modified-WeChat setting changes did not re-apply only the modified host"
        }
        check(WeChatKeepAliveRuntime.applyCalls.all { it.modulePackage == module.packageName }) {
            "The module service context must remain distinct from each host identity"
        }

        officialPrefs.setBoolean(WeChatKeepAliveSettings.KEY_ENABLE, false)
        check(WeChatKeepAliveRuntime.applyCalls.last().hostPackage == official.packageName)
        check(WeChatKeepAliveRuntime.applyCalls.any { it.hostPackage == modified.packageName }) {
            "Disabling one target must not suppress the other target's state updates"
        }
        officialFeature.destroy(officialContext)
        check(WeChatKeepAliveRuntime.stopCalls == listOf(official.packageName to module.packageName)) {
            "Destroy must stop only its own host target"
        }
        check(officialPrefs.listenerCount == 0 && modifiedPrefs.listenerCount == 1) {
            "Settings listeners must be removed per feature instance"
        }
        modifiedFeature.destroy(modifiedContext)
        check(modifiedPrefs.listenerCount == 0)
    }
}
