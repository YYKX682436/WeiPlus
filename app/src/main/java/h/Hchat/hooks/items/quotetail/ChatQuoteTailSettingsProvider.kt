package h.Hchat.hooks.items.quotetail

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ChatQuoteTailSettingsProvider : SimpleFeatureSettingsProvider(
    ChatQuoteTailFeature.ID,
    "聊天引用小尾巴",
    "发送文字时附带自定义的原生引用消息",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
