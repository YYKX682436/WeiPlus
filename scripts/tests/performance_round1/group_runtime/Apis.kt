package h.Hchat.hooks.api.core
import h.Hchat.hooks.api.contact.WeChatChatroomChangeApi
import h.Hchat.hooks.api.model.WeChatChatroom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
object WeChatApis {
    val api = Contact()
    fun contact() = api
    fun chatroomChanges() = api.changes
}
class Contact {
    val changes = WeChatChatroomChangeApi()
    val rooms = Rooms()
    val people = People()
    val self = Account()
    fun chatrooms() = rooms
    fun contacts() = people
    fun account() = self
    fun chatroomChanges() = changes
}
class Account { val reads = AtomicInteger(); @Volatile var identity = "account-a"; fun sessionIdentity(): String { reads.incrementAndGet(); return identity } }
class Rooms {
    val allQueries = AtomicInteger()
    val singleQueries = AtomicInteger()
    @Volatile var entered: CountDownLatch? = null
    @Volatile var blocked: CountDownLatch? = null
    @Volatile var room = WeChatChatroom("g", listOf("a"))
    fun getChatrooms(): List<WeChatChatroom> { allQueries.incrementAndGet(); return listOf(read()) }
    fun getChatroom(id: String): WeChatChatroom? { singleQueries.incrementAndGet(); return if (id == room.chatroomId) read() else null }
    fun getMemberIds(id: String) = getChatroom(id)?.memberIds
    private fun read(): WeChatChatroom { val result = room; entered?.countDown(); blocked?.await(); return result }
}
class People {
    @Volatile var missingNames = false
    @Volatile var entered: CountDownLatch? = null
    @Volatile var blocked: CountDownLatch? = null
    val queries = AtomicInteger()
    fun getGroupMemberRoomDisplayNames(group: String): Map<String, String> {
        queries.incrementAndGet(); entered?.countDown(); blocked?.await(); if (missingNames) return emptyMap(); return WeChatApis.api.rooms.room.memberIds.associateWith { "name-$it" }
    }
}
