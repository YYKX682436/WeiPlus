package h.Hchat.hooks.items.inputhint

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class InputHintSettingsProvider : SimpleFeatureSettingsProvider(
    InputHintFeature.ID,
    "输入框提示",
    "自定义聊天输入框提示内容，并可恢复旧版输入框",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
