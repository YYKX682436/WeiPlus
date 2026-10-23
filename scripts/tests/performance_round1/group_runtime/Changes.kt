package h.Hchat.hooks.api.contact
import h.Hchat.hooks.api.model.WeChatChatroom
import java.util.concurrent.CopyOnWriteArrayList
class WeChatChatroomChangeApi {
    class ChatroomChange(val chatroom: WeChatChatroom) {
        fun chatroomId() = chatroom.chatroomId
        fun mayMemberListChanged() = true
        fun mayRoomDataChanged() = true
    }
    val listeners = CopyOnWriteArrayList<(ChatroomChange) -> Unit>()
    inner class Subscription(val handler: (ChatroomChange) -> Unit) { fun unsubscribe() { listeners.remove(handler) } }
    fun subscribe(handler: (ChatroomChange) -> Unit): Subscription { listeners.add(handler); return Subscription(handler) }
    fun emit(room: WeChatChatroom) { listeners.forEach { it(ChatroomChange(room)) } }
}
