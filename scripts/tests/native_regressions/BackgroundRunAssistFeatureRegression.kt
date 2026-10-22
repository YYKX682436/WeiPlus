import android.content.Context
import h.Hchat.event.EventBus
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistFeature
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistRuntime
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistSettings
import h.Hchat.preferences.HchatStorage
import io.fastkv.FastKV
import java.io.File

object BackgroundRunAssistFeatureRegression {
    fun run() {
        val root = File(System.getProperty("java.io.tmpdir"), "hchat-background-assist-${System.nanoTime()}")
        val official = Context("com.tencent.mm", File(root, "official"))
        val modified = Context("com.tencent.mm.wx", File(root, "modified"))
        val officialContext = FeatureContext(official, EventBus())
        val modifiedContext = FeatureContext(modified, EventBus())
        val officialFeature = BackgroundRunAssistFeature()
        val modifiedFeature = BackgroundRunAssistFeature()

        BackgroundRunAssistRuntime.reset()
        officialFeature.install(officialContext)
        modifiedFeature.install(modifiedContext)
        val officialPrefs = HchatStorage.preferences(official, BackgroundRunAssistSettings.PREFS_NAME) as FastKV
        val modifiedPrefs = HchatStorage.preferences(modified, BackgroundRunAssistSettings.PREFS_NAME) as FastKV

        officialPrefs.setBoolean(BackgroundRunAssistSettings.KEY_ENABLE, true)
        officialPrefs.setBoolean(BackgroundRunAssistSettings.KEY_WAKE_LOCK, true)
        check(BackgroundRunAssistRuntime.applyCalls.takeLast(2).all { it.hostPackage == official.packageName }) {
            "Official settings must be applied only to the official host"
        }
        modifiedPrefs.setBoolean(BackgroundRunAssistSettings.KEY_ENABLE, true)
        modifiedPrefs.setBoolean(BackgroundRunAssistSettings.KEY_ROOT_DOZE_WHITELIST, true)
        check(BackgroundRunAssistRuntime.applyCalls.takeLast(2).all { it.hostPackage == modified.packageName }) {
            "Modified-WeChat settings must be applied only to the modified host"
        }
        officialPrefs.setBoolean(BackgroundRunAssistSettings.KEY_ENABLE, false)
        check(BackgroundRunAssistRuntime.applyCalls.last().hostPackage == official.packageName)
        check(BackgroundRunAssistRuntime.applyCalls.any { it.hostPackage == modified.packageName })

        officialFeature.destroy(officialContext)
        check(BackgroundRunAssistRuntime.stopCalls == listOf(official.packageName)) {
            "Destroy must release only the official host resources"
        }
        check(officialPrefs.listenerCount == 0 && modifiedPrefs.listenerCount == 1) {
            "Settings listeners must be removed per host feature instance"
        }
        modifiedFeature.destroy(modifiedContext)
        check(modifiedPrefs.listenerCount == 0)
        check(BackgroundRunAssistRuntime.stopCalls == listOf(official.packageName, modified.packageName))
    }
}
