package h.Hchat.hooks.api.core

import h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApi

object WeChatApis {
    var databaseChangesApi: WeChatDatabaseListenerApi? = null
    var messageObserveApi: h.Hchat.hooks.api.message.WeChatMessageObserveApi? = null
    private val parser = h.Hchat.hooks.api.message.WeChatMessageParseApi()
    fun databaseChanges(): WeChatDatabaseListenerApi? = databaseChangesApi
    fun message(): MessageGroup = MessageGroup(messageObserveApi)
    fun messageParser() = parser
}

class MessageGroup(private val observer: h.Hchat.hooks.api.message.WeChatMessageObserveApi?) {
    fun observe(): h.Hchat.hooks.api.message.WeChatMessageObserveApi? = observer
}
