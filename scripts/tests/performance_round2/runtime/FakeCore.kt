package h.Hchat.hooks.core
import android.content.Context
class FeatureContext(private val host:Context) { fun hostContext()=host }
abstract class BaseFeature {
    abstract fun featureId():String
    abstract fun name():String
    protected open fun onFeatureInit(context:FeatureContext) {}
    protected abstract fun onFeatureInstall(context:FeatureContext)
    protected open fun onFeatureDestroy(context:FeatureContext) {}
    fun install(context:FeatureContext) { onFeatureInit(context); onFeatureInstall(context) }
    fun destroy(context:FeatureContext) { onFeatureDestroy(context) }
    protected fun registerSettingsProvider(provider:Any) {}
    protected fun logError(message:String,error:Throwable?) { throw AssertionError(message,error) }
}
object DexInstallScheduler {
    enum class Stage { WARMUP }
    fun schedule(id:String,name:String,stage:Stage,task:()->Boolean) { check(task()) }
}
