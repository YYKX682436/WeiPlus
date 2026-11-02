package android.app
import android.content.*
import android.os.Bundle
open class Activity
open class Application : Context() {
    var receiver:BroadcastReceiver?=null
    var callback:ActivityLifecycleCallbacks?=null
    override fun registerReceiver(receiver:BroadcastReceiver,filter:IntentFilter,flags:Int):Intent? { check(this.receiver==null); this.receiver=receiver; return null }
    override fun registerReceiver(receiver:BroadcastReceiver,filter:IntentFilter):Intent? = registerReceiver(receiver,filter,0)
    override fun unregisterReceiver(receiver:BroadcastReceiver) { check(this.receiver===receiver); this.receiver=null }
    fun registerActivityLifecycleCallbacks(callback:ActivityLifecycleCallbacks) { check(this.callback==null); this.callback=callback }
    fun unregisterActivityLifecycleCallbacks(callback:ActivityLifecycleCallbacks) { check(this.callback===callback); this.callback=null }
    interface ActivityLifecycleCallbacks {
        fun onActivityResumed(activity:Activity); fun onActivityCreated(activity:Activity,state:Bundle?)
        fun onActivityStarted(activity:Activity); fun onActivityPaused(activity:Activity); fun onActivityStopped(activity:Activity)
        fun onActivitySaveInstanceState(activity:Activity,state:Bundle); fun onActivityDestroyed(activity:Activity)
    }
}
