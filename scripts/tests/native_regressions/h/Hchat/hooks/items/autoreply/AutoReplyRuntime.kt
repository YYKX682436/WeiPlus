package h.Hchat.hooks.items.autoreply

import android.content.Context
import h.Hchat.hooks.api.message.WeChatMessageObserveApi

object AutoReplyRuntime {
    val handledMessages = mutableListOf<WeChatMessageObserveApi.ObservedMessage>()
    val handledFriends = mutableListOf<Triple<String, String, Int>>()

    fun handleMessage(context: Context, message: WeChatMessageObserveApi.ObservedMessage) {
        handledMessages += message
    }

    fun handleNewFriend(context: Context, wxid: String, ticket: String, scene: Int) {
        handledFriends += Triple(wxid, ticket, scene)
    }

    fun reset() {
        handledMessages.clear()
        handledFriends.clear()
    }
}
