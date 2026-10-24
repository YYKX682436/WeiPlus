package h.Hchat.hooks.items.groupmonitor
import android.content.Context
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.WeChatChatroom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun waitUntil(condition: () -> Boolean) { val end = System.nanoTime()+TimeUnit.SECONDS.toNanos(3); while(!condition()) {check(System.nanoTime()<end);Thread.sleep(5)} }
fun main() {
    val api=WeChatApis.api
    val runtime=GroupMonitorSnapshots.get(Context("com.tencent.mm"))
    runtime.configure("off",GroupSnapshotState.Demand()) { _, _, _, _ -> error("Off callback") }
    runtime.preload();runtime.lease()
    check(api.self.reads.get()==0)
    runtime.remove("off")
    println("PASS: all-off installation and DexReady do not query even the account identity")
    val all=GroupSnapshotState.Scope(all=true)
    val demand=GroupSnapshotState.Demand(all, all)
    var leave: GroupSnapshotState.Snapshot?=null
    var rename: GroupSnapshotState.Snapshot?=null
    runtime.configure("leave", demand) { _, old, _, _ -> leave=old }
    runtime.configure("rename", demand) { _, old, _, _ -> rename=old }
    waitUntil { runtime.memberName("g", "a") == "name-a" }
    check(api.rooms.allQueries.get()==1 && api.people.queries.get()==1)
    runtime.preload();Thread.sleep(50)
    check(api.rooms.allQueries.get()==1)
    api.rooms.room=WeChatChatroom("g",listOf("a","b"));api.changes.emit(api.rooms.room)
    check(leave === rename && leave?.members == setOf("a"))
    check(api.people.queries.get()==2)
    api.changes.emit(WeChatChatroom("g", emptyList()))
    check(leave?.members == setOf("a") && api.people.queries.get()==2)
    api.changes.emit(api.rooms.room)
    check(leave?.members == setOf("a", "b"))
    println("PASS: empty host reads neither wipe a valid baseline nor report that everyone left")
    println("PASS: installed consumers and DexReady share a single preload, store and per-event nickname query")
    api.people.missingNames=true
    api.changes.emit(WeChatChatroom("g", listOf("a", "b"), "A\u0001B"))
    check(runtime.memberName("g", "a")=="A" && runtime.memberName("g", "b")=="B")
    api.changes.emit(WeChatChatroom("g", listOf("a"), ""))
    check(runtime.memberName("g", "a")=="A")
    api.people.missingNames=false
    api.changes.emit(api.rooms.room)
    println("PASS: partial nickname queries preserve the room-record display-name fallback")
    val oldDispatch=api.changes.listeners.toList()
    runtime.remove("leave");runtime.remove("rename")
    val queries=api.rooms.allQueries.get()
    oldDispatch.forEach { it(h.Hchat.hooks.api.contact.WeChatChatroomChangeApi.ChatroomChange(api.rooms.room)) }
    runtime.preload();Thread.sleep(50)
    check(api.changes.listeners.isEmpty() && runtime.memberName("g","a")==null && api.rooms.allQueries.get()==queries)
    println("PASS: all-off unsubscribes/releases state; captured old dispatch cannot resurrect queries")

    val specific=GroupSnapshotState.Scope(groups=setOf("g"))
    val scope=GroupSnapshotState.Demand(specific,specific)
    val entered=CountDownLatch(1);val blocked=CountDownLatch(1)
    api.rooms.entered=entered;api.rooms.blocked=blocked
    runtime.configure("leave",scope) { _, _, _, _ -> error("Historical event") }
    oldDispatch.forEach { it(h.Hchat.hooks.api.contact.WeChatChatroomChangeApi.ChatroomChange(api.rooms.room)) }
    check(entered.await(3,TimeUnit.SECONDS))
    val lease=runtime.lease()
    runtime.configure("leave",GroupSnapshotState.Demand()) { _, _, _, _ -> error("Disabled event") }
    blocked.countDown();Thread.sleep(80)
    check(runtime.memberName("g","a")==null && !runtime.current("leave","g",lease))
    check(api.rooms.allQueries.get()==queries)
    println("PASS: specific mode does not enumerate all groups; disabling during a blocked read rejects its result")
    api.rooms.entered=null;api.rooms.blocked=null
    api.rooms.room=WeChatChatroom("g",listOf("new-baseline"))
    var previous: GroupSnapshotState.Snapshot?=null
    runtime.configure("leave",scope) { _, old, _, _ -> previous=old }
    waitUntil { runtime.memberName("g","new-baseline")!=null }
    api.changes.emit(api.rooms.room)
    check(previous?.members==setOf("new-baseline"))
    val accountLease=runtime.lease()
    val eventEntered=CountDownLatch(1);val eventBlocked=CountDownLatch(1)
    api.people.entered=eventEntered;api.people.blocked=eventBlocked
    var staleCallback=false
    runtime.configure("leave",scope) { _, _, _, _ -> staleCallback=true }
    val eventThread=Thread { api.changes.emit(WeChatChatroom("g",listOf("new-baseline", "old-account-event"))) }
    eventThread.start();check(eventEntered.await(3,TimeUnit.SECONDS))
    api.self.identity="account-b"
    eventBlocked.countDown();eventThread.join(3000)
    check(!eventThread.isAlive && !staleCallback && !runtime.current("leave","g",accountLease))
    api.people.entered=null;api.people.blocked=null
    runtime.remove("leave")
    check(runtime.memberName("g","new-baseline")==null)
    println("PASS: account changes during a blocked event query reject its snapshot and notifications")
    val other=GroupMonitorSnapshots.get(Context("com.tencent.mm.wx"))
    check(other !== runtime && other.memberName("g","a")==null)
    println("PASS: re-enable starts with current baseline; host/account changes isolate state and cancel old work")
}
