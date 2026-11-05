package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class LinkParseFeature : BaseFeature() {
    private var runtime: LinkParseRuntime? = null
    override fun featureId() = ID
    override fun name() = "链接解析"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(LinkParseSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = LinkParseRuntime(context).also { it.install { subscription -> trackSubscription(subscription) } }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
    companion object { const val ID = "link_parse" }
}

private class LinkParseSettingsProvider : SimpleFeatureSettingsProvider(
    LinkParseFeature.ID, "链接解析", "解析公开作品视频并发送小红书和抖音原图图集", FeatureSettingsProvider.CATEGORY_PRACTICAL
)
