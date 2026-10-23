package android.app
import android.view.View
class Window(val decorView: View)
open class Activity(val window: Window) {
    var focused = true
    var isFinishing = false
    var isDestroyed = false
    fun hasWindowFocus() = focused
}
class ActivityRecord(@JvmField val activity: Activity)
class ActivityThread {
    @JvmField val mActivities = LinkedHashMap<String, ActivityRecord>()
    companion object {
        val instance = ActivityThread()
        @JvmStatic fun currentActivityThread() = instance
    }
}
