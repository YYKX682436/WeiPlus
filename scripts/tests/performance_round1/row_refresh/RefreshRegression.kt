package h.Hchat.hooks.core
import android.app.*
import android.os.Handler
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.utils.KavaReflector
private class Adapter {
    var changes = 0
    fun getItemCount() = 7
    fun notifyItemRangeChanged(start: Int, size: Int) { check(start == 0 && size == 7); changes++ }
}
private class InlineChat(adapter: Any) : RecyclerView(adapter)
fun main() {
    val adapter = Adapter(); val chat = InlineChat(adapter)
    val decor = ViewGroup().apply { children.add(chat) }; val activity = Activity(Window(decor))
    ActivityThread.instance.mActivities["live"] = ActivityRecord(activity)
    ChatRowRefresh.request(); Handler.drain()
    check(adapter.changes == 1 && chat.layouts == 1 && WeChatApis.tracker.currentActivity() === activity)
    println("PASS: inline chat refresh recovers an Activity missed by late lifecycle installation and rebinds its wrapper rows")
    val reads = KavaReflector.threadReads
    ChatRowRefresh.requestAfterTransition(); ChatRowRefresh.requestAfterTransition()
    check(Handler.delayed.size == 1 && adapter.changes == 1)
    Handler.finishTransition()
    check(adapter.changes == 2 && KavaReflector.threadReads == reads)
    println("PASS: page entry coalesces one post-transition refresh and reuses the weak Activity tracker")
    activity.focused = false
    ChatRowRefresh.request(); Handler.drain()
    check(adapter.changes == 2)
    activity.focused = true; chat.isShown = false
    ChatRowRefresh.request(); Handler.drain()
    check(adapter.changes == 2)
    println("PASS: unfocused Activity and hidden chat rows are not refreshed")
}
