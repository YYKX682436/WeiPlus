package h.Hchat.hooks.items.linkparse

import android.content.Context
import h.Hchat.preferences.HchatStorage

internal class LinkParseSettings(context: Context) {
    private val prefs = HchatStorage.preferences(context, PREFS_NAME)
    fun enabled() = prefs.getBoolean(KEY_ENABLE, false)
    fun enabled(platform: WorkLinkMatcher.Platform) = prefs.getBoolean(when (platform) {
        WorkLinkMatcher.Platform.DOUYIN -> KEY_DOUYIN
        WorkLinkMatcher.Platform.XIAOHONGSHU -> KEY_XHS
        WorkLinkMatcher.Platform.KUAISHOU -> KEY_KUAISHOU
    }, false)
    fun groupMode() = prefs.getString(KEY_GROUP_MODE, GROUP_OFF).orEmpty()
    fun allows(platform: WorkLinkMatcher.Platform, group: Boolean, atMe: Boolean): Boolean =
        enabled() && enabled(platform) && (!group || when (groupMode()) {
            GROUP_AT -> atMe
            GROUP_AUTO -> true
            else -> false
        })

    companion object {
        const val PREFS_NAME = "Hchat_link_parse_config"
        const val KEY_ENABLE = "enable"
        const val KEY_DOUYIN = "douyin"
        const val KEY_XHS = "xiaohongshu"
        const val KEY_KUAISHOU = "kuaishou"
        const val KEY_GROUP_MODE = "group_mode"
        const val GROUP_OFF = "off"
        const val GROUP_AT = "at_me"
        const val GROUP_AUTO = "auto"
    }
}
