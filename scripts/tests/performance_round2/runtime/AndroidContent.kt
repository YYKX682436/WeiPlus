package android.content
import android.app.Application
open class Context {
    open val applicationContext: Context get() = this
    open fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter): Intent? = null
    open fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter, flags: Int): Intent? = null
    open fun unregisterReceiver(receiver: BroadcastReceiver) {}
    companion object { const val RECEIVER_NOT_EXPORTED=4 }
}
open class Intent(val action: String? = null) {
    companion object { const val ACTION_TIME_CHANGED="time"; const val ACTION_TIMEZONE_CHANGED="zone"; const val ACTION_SCREEN_ON="screen" }
}
class IntentFilter { val actions=mutableListOf<String>(); fun addAction(action:String) { actions.add(action) } }
abstract class BroadcastReceiver { abstract fun onReceive(context:Context?,intent:Intent?) }
interface SharedPreferences {
    fun interface OnSharedPreferenceChangeListener { fun onSharedPreferenceChanged(prefs:SharedPreferences?,key:String?) }
    fun getBoolean(key:String, fallback:Boolean):Boolean
    fun getInt(key:String, fallback:Int):Int
    fun getString(key:String, fallback:String?):String?
    fun registerOnSharedPreferenceChangeListener(listener:OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener:OnSharedPreferenceChangeListener)
    fun edit():Editor
    interface Editor { fun putLong(key:String,value:Long):Editor; fun putString(key:String,value:String?):Editor; fun apply() }
}
