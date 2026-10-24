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
    val extra = java.util.concurrent.ConcurrentHashMap<String, WeChatChatroom>()
    val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val queried = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun getChatrooms(): List<WeChatChatroom> { allQueries.incrementAndGet(); return listOf(read()) + extra.values }
    fun getChatroom(id: String): WeChatChatroom? { singleQueries.incrementAndGet();queried.add(id);if(id in failed) error("Injected room read failure");return if (id == room.chatroomId) read() else extra[id] }
    fun getMemberIds(id: String) = getChatroom(id)?.memberIds
    private fun read(): WeChatChatroom { val result = room; entered?.countDown(); blocked?.await(); return result }
}
class People {
    @Volatile var missingNames = false
    @Volatile var entered: CountDownLatch? = null
    @Volatile var blocked: CountDownLatch? = null
    val queries = AtomicInteger()
    val results = java.util.concurrent.ConcurrentHashMap<String, Map<String,String>>()
    fun getGroupMemberRoomDisplayNames(group: String): Map<String, String> {
        queries.incrementAndGet(); val result=results[group] ?: if(missingNames) emptyMap() else (if(group==WeChatApis.api.rooms.room.chatroomId) WeChatApis.api.rooms.room else WeChatApis.api.rooms.extra[group])?.memberIds.orEmpty().associateWith { "name-$it" }; val signal=entered;val wait=blocked;signal?.countDown();wait?.await();return result
    }
}
