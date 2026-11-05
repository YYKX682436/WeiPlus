package h.Hchat.hooks.core

import android.app.*
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tencent.mm.ui.chatting.viewitems.ChatMessageHolder
import com.tencent.mm.ui.mvvm.ContactHolder
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.utils.KavaReflector

private class Adapter {
    var changes = 0
    fun getItemCount() = 7
    fun notifyItemRangeChanged(start: Int, size: Int) {
        check(start == 0 && size == 7)
        changes++
    }
}

private class InlineChat(adapter: Any) : RecyclerView(adapter) {
    init { children.add(View().apply { tag = ChatMessageHolder() }) }
}

private class ContactList(adapter: Any) : RecyclerView(adapter) {
    init { children.add(View().apply { tag = ContactHolder() }) }
}

private fun checkChatOnly(chat: Adapter, contacts: Adapter, chatBefore: Int, contactsBefore: Int) {
    check(chat.changes == chatBefore + 1) { "native chat adapter should refresh once" }
    check(contacts.changes == contactsBefore) { "contact adapter must not refresh for a chat-only request" }
}

fun main() {
    val chatAdapter = Adapter()
    val contactAdapter = Adapter()
    val chat = InlineChat(chatAdapter)
    val contacts = ContactList(contactAdapter)
    val decor = ViewGroup().apply { children.add(chat); children.add(contacts) }
    val activity = Activity(Window(decor))
    ActivityThread.instance.mActivities["live"] = ActivityRecord(activity)
    WeChatApis.tracker.updateCurrentActivity(activity)

    ChatRowRefresh.request()
    check(Handler.queue.size == 1) { "shared request keeps its original asynchronous dispatch" }
    check(chatAdapter.changes == 0 && contactAdapter.changes == 0)
    Handler.drain()
    check(chatAdapter.changes == 1 && contactAdapter.changes == 1)
    check(chat.layouts == 1 && contacts.layouts == 1)
    check(WeChatApis.tracker.currentActivity() === activity)
    println("PASS: shared request still refreshes all visible RecyclerViews for avatar/swipe-quote callers")

    val reads = KavaReflector.threadReads
    var chatBefore = chatAdapter.changes
    var contactsBefore = contactAdapter.changes
    ChatRowRefresh.requestChatMessageRows()
    check(Handler.queue.size == 1) { "chat refresh must use one queued task" }
    Handler.drain()
    checkChatOnly(chatAdapter, contactAdapter, chatBefore, contactsBefore)
    chatBefore = chatAdapter.changes
    contactsBefore = contactAdapter.changes
    println("PASS: immediate bubble refresh selects the native message-holder RecyclerView only")

    ChatRowRefresh.requestChatMessageRowsAfterTransition()
    ChatRowRefresh.requestChatMessageRowsAfterTransition()
    check(Handler.delayed.size == 1) { "repeated page entry coalesces to one delayed chat task" }
    Handler.finishTransition()
    checkChatOnly(chatAdapter, contactAdapter, chatBefore, contactsBefore)
    check(KavaReflector.threadReads == reads) { "focused Activity continues to use the weak tracker" }
    chatBefore = chatAdapter.changes
    contactsBefore = contactAdapter.changes
    println("PASS: page-entry request coalesces and revalidates the visible chat target at execution")

    ChatRowRefresh.requestChatMessageRowsAfterTransition()
    Handler.promoteDelayedToQueue()
    check(Handler.delayed.isEmpty() && Handler.queue.size == 1)
    ChatRowRefresh.cancelChatMessageRows()
    check(Handler.queue.isEmpty()) { "cancel must remove a chat refresh already promoted to the main queue" }
    Handler.drain()
    check(chatAdapter.changes == chatBefore && contactAdapter.changes == contactsBefore)
    ChatRowRefresh.requestChatMessageRows()
    check(Handler.queue.size == 1)
    ChatRowRefresh.cancelChatMessageRows()
    Handler.drain()
    check(chatAdapter.changes == chatBefore && contactAdapter.changes == contactsBefore)
    println("PASS: exit/disable/destroy cancellation removes delayed and already-queued chat tasks")

    val contactsOnlyAdapter = Adapter()
    val contactsOnly = ContactList(contactsOnlyAdapter)
    val contactActivity = Activity(Window(ViewGroup().apply { children.add(contactsOnly) }))
    activity.focused = false
    contactActivity.focused = true
    WeChatApis.tracker.updateCurrentActivity(contactActivity)
    ChatRowRefresh.requestChatMessageRowsAfterTransition()
    Handler.finishTransition()
    check(chatAdapter.changes == chatBefore && contactAdapter.changes == contactsBefore)
    check(contactsOnlyAdapter.changes == 0) { "switching to the contact picker before the deadline must not refresh it" }

    contactActivity.focused = false
    activity.focused = true
    WeChatApis.tracker.updateCurrentActivity(activity)
    ChatRowRefresh.requestChatMessageRowsAfterTransition()
    Handler.finishTransition()
    checkChatOnly(chatAdapter, contactAdapter, chatBefore, contactsBefore)
    println("PASS: a quick switch to contacts skips the stale task; returning to the active inline chat refreshes chat rows")
}
