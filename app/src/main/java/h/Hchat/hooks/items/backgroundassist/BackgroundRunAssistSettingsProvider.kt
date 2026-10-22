package h.Hchat.hooks.items.backgroundassist

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class BackgroundRunAssistSettingsProvider : SimpleFeatureSettingsProvider(
    BackgroundRunAssistFeature.ID,
    "微信后台运行辅助",
    "有限时 WakeLock 与可恢复的 Root 系统选项",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
