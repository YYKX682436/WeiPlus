package h.Hchat.hooks.items.musicorder

import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext

class QQMusicOrderFeature : BaseFeature() {
    private var runtime: QQMusicOrderRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "QQ点歌"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(QQMusicOrderSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = QQMusicOrderRuntime(context.hostContext(), ::logError).also { musicRuntime ->
            musicRuntime.install { subscription -> trackSubscription(subscription) }
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object {
        const val ID = "qq_music_order"
    }
}
