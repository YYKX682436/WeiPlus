package h.Hchat.hooks.core

import h.Hchat.event.EventBus
import h.Hchat.event.EventHandler
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import java.util.LinkedHashMap

/**
 * 功能模块基类。
 */
abstract class BaseFeature : Feature {
    private val subscriptions = ArrayList<EventBus.Subscription>()
    private val trackedSubscriptions = LinkedHashMap<String, Any>()
    private val registeredProviderIds = ArrayList<String>()
    private var nextTrackedSubscriptionId = 0L
    private var context: FeatureContext? = null

    @Throws(Throwable::class)
    final override fun onInit(context: FeatureContext) {
        this.context = context
        onFeatureInit(context)
    }

    @Throws(Throwable::class)
    final override fun install(context: FeatureContext) {
        this.context = context
        onFeatureInstall(context)
    }

    final override fun onDestroy(context: FeatureContext) {
        try {
            onFeatureDestroy(context)
        } catch (e: Throwable) {
            logError("onDestroy异常", e)
        }
        for (subscription in subscriptions) {
            try {
                context.eventBus().unsubscribe(subscription)
            } catch (_: Throwable) {
            }
        }
        subscriptions.clear()
        val tracked = synchronized(trackedSubscriptions) {
            trackedSubscriptions.values.toList().also { trackedSubscriptions.clear() }
        }
        tracked.forEach(::unsubscribeTracked)
        for (featureId in registeredProviderIds) {
            try {
                context.uiRegistry().unregisterProvider(featureId)
            } catch (_: Throwable) {
            }
        }
        registeredProviderIds.clear()
    }

    @Throws(Throwable::class)
    protected open fun onFeatureInit(context: FeatureContext) {
    }

    @Throws(Throwable::class)
    protected abstract fun onFeatureInstall(context: FeatureContext)

    protected open fun onFeatureDestroy(context: FeatureContext) {
    }

    protected fun context(): FeatureContext? = context

    protected fun registerSettingsProvider(provider: FeatureSettingsProvider?) {
        val currentContext = context ?: return
        if (provider == null) return
        currentContext.uiRegistry().registerProvider(provider)
        registeredProviderIds.add(provider.featureId())
    }

    protected fun <E> subscribe(
        eventType: Class<E>,
        handler: EventHandler<E>
    ): EventBus.Subscription {
        return subscribe(eventType, handler, 0)
    }

    protected fun <E> subscribe(
        eventType: Class<E>,
        handler: EventHandler<E>,
        priority: Int
    ): EventBus.Subscription {
        val currentContext = context ?: return EventBus.Subscription.empty()
        val subscription = currentContext.eventBus().subscribe(eventType, handler, priority)
        subscriptions.add(subscription)
        return subscription
    }

    protected fun <T : Any> trackSubscription(subscription: T?): T? {
        if (subscription != null) {
            synchronized(trackedSubscriptions) {
                trackedSubscriptions["tracked-${nextTrackedSubscriptionId++}"] = subscription
            }
        }
        return subscription
    }

    /** Replaces a feature-owned subscription without retaining the previous lease until destroy. */
    protected fun <T : Any> replaceTrackedSubscription(key: String, subscription: T?): T? {
        val previous = synchronized(trackedSubscriptions) {
            val old = trackedSubscriptions.remove(key)
            if (subscription != null) trackedSubscriptions[key] = subscription
            old
        }
        if (previous != null && previous !== subscription) unsubscribeTracked(previous)
        return subscription
    }

    protected fun releaseTrackedSubscription(key: String) {
        val previous = synchronized(trackedSubscriptions) { trackedSubscriptions.remove(key) }
        if (previous != null) unsubscribeTracked(previous)
    }

    private fun unsubscribeTracked(subscription: Any) {
        try {
            KavaReflector.invoke(
                KavaReflector.findMethod(subscription.javaClass, "unsubscribe"),
                subscription
            )
        } catch (_: Throwable) {
        }
    }

    protected fun globalBoolean(key: String, defValue: Boolean): Boolean {
        return context?.configStore()?.getGlobalBoolean(key, defValue) ?: defValue
    }

    protected fun featureBoolean(key: String, defValue: Boolean): Boolean {
        return context?.configStore()?.getBoolean(featureId(), key, defValue) ?: defValue
    }

    protected fun featureInt(key: String, defValue: Int): Int {
        return context?.configStore()?.getInt(featureId(), key, defValue) ?: defValue
    }

    protected fun featureString(key: String, defValue: String): String {
        return context?.configStore()?.getString(featureId(), key, defValue) ?: defValue
    }

    protected fun logError(message: String, throwable: Throwable?) {
        HLog.e(
            "[Hchat:${featureId()}] $message" +
                (throwable?.let { ": ${it.message}" } ?: ""),
            throwable
        )
    }
}
