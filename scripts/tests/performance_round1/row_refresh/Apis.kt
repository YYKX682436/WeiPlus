package h.Hchat.hooks.api.core
import android.app.Activity
import java.lang.ref.WeakReference
class ActivityTracker {
    var current = WeakReference<Activity>(null)
    fun currentActivity() = current.get()
    fun updateCurrentActivity(activity: Activity) { current = WeakReference(activity) }
}
object WeChatApis { val tracker = ActivityTracker(); fun currentActivity() = tracker }
