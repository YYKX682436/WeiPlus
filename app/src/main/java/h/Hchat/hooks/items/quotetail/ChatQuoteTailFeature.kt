package h.Hchat.hooks.items.quotetail

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.OutgoingTextDecoratorRegistry
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.sendbutton.NativeSendButtonHook

class ChatQuoteTailFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "聊天引用小尾巴"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ChatQuoteTailSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = ChatQuoteTailSettings.preferences(context.hostContext())
        trackSubscription(
            NativeSendButtonHook.registerHandler(ID) { text ->
                val talker = WeChatApis.chatPage()?.currentTalker().orEmpty()
                val quoteContent = prefs.getString(
                    ChatQuoteTailSettings.KEY_CONTENT,
                    ChatQuoteTailSettings.DEFAULT_CONTENT
                ).orEmpty()
                val enabled = prefs.getBoolean(
                    ChatQuoteTailSettings.KEY_ENABLE,
                    ChatQuoteTailSettings.DEFAULT_ENABLE
                )
                if (!enabled) return@registerHandler false

                val account = WeChatApis.account() ?: return@registerHandler false
                val selfWxId = account.selfWxId().orEmpty()
                if (selfWxId.isBlank()) return@registerHandler false
                val accountName = account.selfName().orEmpty()
                val contactName = if (accountName.isNotBlank() && accountName != selfWxId) {
                    ""
                } else {
                    WeChatApis.contacts()?.getContact(selfWxId)?.nickname.orEmpty()
                }
                val nickname = ChatQuoteTailPolicy.resolveNickname(accountName, contactName, selfWxId)
                if (nickname.isBlank()) return@registerHandler false

                val lastQuoteMessageId = NativeSendButtonHook.currentQuoteMessageId()
                if (!ChatQuoteTailPolicy.shouldIntercept(
                        enabled = enabled,
                        text = text,
                        talker = talker,
                        nickname = nickname,
                        quoteContent = quoteContent,
                        lastQuoteMessageId = lastQuoteMessageId
                    )
                ) {
                    return@registerHandler false
                }

                val formattedTitle = OutgoingTextDecoratorRegistry.decorateText(talker, text)
                val xml = ChatQuoteTailXml.build(
                    talker = talker,
                    selfWxId = selfWxId,
                    title = formattedTitle,
                    nickname = nickname,
                    quoteContent = quoteContent
                ) ?: return@registerHandler false

                val messageApi = WeChatApis.messages() ?: return@registerHandler false
                if (!messageApi.canSendXml()) context.dexFinder().resolveSendXmlApi()
                if (!messageApi.canSendXml()) return@registerHandler false
                val submitted = messageApi.sendXml(talker, xml)
                submitted
            }
        )
    }

    companion object {
        const val ID = "chat_quote_tail"
    }
}
