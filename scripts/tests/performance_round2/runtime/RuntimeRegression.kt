import android.app.Application
import android.content.Intent
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.moments.*
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.preferences.HchatStorage
fun main() {
    val app=Application(); val context=FeatureContext(app); val feature=MomentsAutoRefreshFeature(); val prefs=HchatStorage.prefs
    feature.install(context)
    check(app.receiver==null && app.callback==null && prefs.listeners.size==1) { "disabled feature installed observers" }
    prefs.set(MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS,300)
    repeat(10) {
        prefs.set(MomentsAutoRefreshSettings.KEY_ENABLE,true)
        check(app.receiver!=null && app.callback!=null) { "enabled observers missing" }
        prefs.set(MomentsAutoRefreshSettings.KEY_START_TIME,"23:00")
        prefs.set(MomentsAutoRefreshSettings.KEY_END_TIME,"02:00")
        prefs.set(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE,false)
        app.receiver?.onReceive(app,Intent(Intent.ACTION_TIME_CHANGED))
        app.callback?.onActivityResumed(android.app.Activity())
        prefs.set(MomentsAutoRefreshSettings.KEY_ENABLE,false)
        check(app.receiver==null && app.callback==null) { "disabled observers leaked" }
    }
    prefs.set(MomentsAutoRefreshSettings.KEY_ENABLE,true)
    Thread.sleep(1300)
    check(WeChatApis.requests.get()==1) { "first enabled refresh missing or duplicated" }
    check(prefs.values[MomentsAutoRefreshSettings.KEY_LAST_RESULT]=="刷新请求已提交") { "result not recorded" }
    prefs.set(MomentsAutoRefreshSettings.KEY_ENABLE,false)
    feature.destroy(context)
    check(app.receiver==null && app.callback==null && prefs.listeners.isEmpty()) { "destroy leaked observers" }
    val count=WeChatApis.requests.get(); Thread.sleep(200); check(WeChatApis.requests.get()==count)
    println("PASS: production runtime responds to config changes and registers observers only while enabled")
}
