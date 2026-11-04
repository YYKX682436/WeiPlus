package h.Hchat.hooks.items.sportstep

import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class SportStepFeature : BaseFeature() {
    override fun featureId() = ID
    override fun name() = "修改运动步数"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            ID, "修改运动步数", "手动提交步数", FeatureSettingsProvider.CATEGORY_PRACTICAL
        ) {})
    }
    override fun onFeatureInstall(context: FeatureContext) { active = SportStepRuntime(context) }
    override fun onFeatureDestroy(context: FeatureContext) {
        active?.destroy()
        active = null
    }

    companion object {
        const val ID = "sport_step"
        @Volatile internal var active: SportStepRuntime? = null
    }
}
