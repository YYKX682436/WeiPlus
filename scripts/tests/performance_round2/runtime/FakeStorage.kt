package h.Hchat.preferences
import android.content.*
class FakePrefs : SharedPreferences {
    val values=mutableMapOf<String,Any>(); val listeners=mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    fun set(key:String,value:Any) { values[key]=value; listeners.toList().forEach { it.onSharedPreferenceChanged(this,key) } }
    override fun getBoolean(key:String,fallback:Boolean) = values[key] as? Boolean ?: fallback
    override fun getInt(key:String,fallback:Int) = values[key] as? Int ?: fallback
    override fun getString(key:String,fallback:String?) = values[key] as? String ?: fallback
    override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(listener) }
    override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(listener) }
    override fun edit():SharedPreferences.Editor = object:SharedPreferences.Editor {
        override fun putLong(key:String,value:Long):SharedPreferences.Editor { values[key]=value; return this }
        override fun putString(key:String,value:String?):SharedPreferences.Editor { if(value!=null) values[key]=value; return this }
        override fun apply() {}
    }
}
object HchatStorage { val prefs=FakePrefs(); @JvmStatic fun preferences(context:Context,name:String):SharedPreferences=prefs }
