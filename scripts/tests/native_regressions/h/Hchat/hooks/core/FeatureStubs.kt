package h.Hchat.hooks.core

import android.content.Context
import h.Hchat.event.EventBus
import h.Hchat.event.EventHandler

class FeatureContext(
    private val host: Context,
    private val bus: EventBus,
    private val module: Context = Context("h.Hchat", host.dataDir)
) {
    fun hostContext(): Context = host
    fun moduleContext(): Context = module
    fun eventBus(): EventBus = bus
}

abstract class BaseFeature {
    private var currentContext: FeatureContext? = null
    private val eventSubscriptions = mutableListOf<EventBus.Subscription>()
    private val tracked = mutableListOf<Any>()

    fun install(context: FeatureContext) {
        currentContext = context
        onFeatureInit(context)
        onFeatureInstall(context)
    }

    fun destroy(context: FeatureContext) {
        onFeatureDestroy(context)
        eventSubscriptions.forEach(context.eventBus()::unsubscribe)
        eventSubscriptions.clear()
        tracked.forEach { runCatching { it.javaClass.getMethod("unsubscribe").invoke(it) } }
        tracked.clear()
    }

    protected fun context(): FeatureContext? = currentContext
    protected fun registerSettingsProvider(provider: Any?) = Unit
    abstract fun featureId(): String
    open fun name(): String = featureId()

    protected fun <E : Any> subscribe(type: Class<E>, handler: EventHandler<E>): EventBus.Subscription =
        context()?.eventBus()?.subscribe(type, handler)?.also(eventSubscriptions::add)
            ?: EventBus.Subscription.empty()

    protected fun <T : Any> trackSubscription(subscription: T?): T? {
        if (subscription != null) tracked += subscription
        return subscription
    }

    protected abstract fun onFeatureInstall(context: FeatureContext)
    protected open fun onFeatureInit(context: FeatureContext) = Unit
    protected open fun onFeatureDestroy(context: FeatureContext) = Unit
}

object DexInstallScheduler {
    enum class Stage { WARMUP }
    fun schedule(key: String, label: String, stage: Stage, installer: () -> Boolean) { installer() }
    fun cancel(key: String) = Unit
}
