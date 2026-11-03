import h.Hchat.hooks.core.Feature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.FeatureManager
import h.Hchat.hooks.core.ApiBus
import h.Hchat.hooks.core.ApiSubscription

private object ApiRegistry { var current: ApiBus? = null }

private class FlakyCore : Feature {
    var initCalls = 0
    var installCalls = 0
    override fun featureId() = "wechat_api"
    override fun isRequiredCore() = true
    override fun onInit(context: FeatureContext) {
        initCalls++
        if (initCalls == 1) error("injected one-time core initialization failure")
    }
    override fun install(context: FeatureContext) { installCalls++ }
}

private class Downstream : Feature {
    var initCalls = 0
    var installCalls = 0
    override fun featureId() = "downstream"
    override fun onInit(context: FeatureContext) { initCalls++ }
    override fun install(context: FeatureContext) { installCalls++ }
}

private class PublicApiCore : Feature {
    override fun featureId() = "wechat_api"
    override fun isRequiredCore() = true
    override fun onInit(context: FeatureContext) { ApiRegistry.current = context.publicApi }
    override fun install(context: FeatureContext) { ApiRegistry.current = context.publicApi }
}

private class ApiSubscriber : Feature {
    var subscription: ApiSubscription? = null
    var callbackCount = 0
    var initCalls = 0
    var installCalls = 0
    override fun featureId() = "api_subscriber"
    override fun onInit(context: FeatureContext) {
        initCalls++
        subscription = ApiRegistry.current!!.subscribe { callbackCount++ }
    }
    override fun install(context: FeatureContext) { installCalls++ }
    override fun onDestroy(context: FeatureContext) { subscription?.unsubscribe() }
}

fun main() {
    val core = FlakyCore()
    val downstream = Downstream()
    val manager = FeatureManager().register(core).register(downstream)
    val context = FeatureContext()

    manager.installAll(context)
    check(core.initCalls == 1) { "first pass must inject the requested core failure" }
    check(downstream.initCalls == 0 && downstream.installCalls == 0) {
        "a failed core API must stop downstream initialization"
    }

    manager.installAll(context)
    check(core.initCalls == 2 && core.installCalls == 1) {
        "the same manager must recover after a transient core initialization failure"
    }
    check(downstream.initCalls == 1 && downstream.installCalls == 1) {
        "downstream features install once after core readiness"
    }

    manager.installAll(context)
    check(core.installCalls == 1 && downstream.installCalls == 1) {
        "repeated readiness callbacks must not duplicate successful installs"
    }

    val oldApi = ApiBus()
    val newApi = ApiBus()
    val apiCore = PublicApiCore()
    val subscriber = ApiSubscriber()
    val apiManager = FeatureManager().register(apiCore).register(subscriber)
    check(apiManager.installAll(FeatureContext(oldApi))) { "initial public API installation failed" }
    val oldSubscription = subscriber.subscription!!
    check(oldSubscription.active && oldApi.listeners.size == 1)
    check(apiManager.revalidateRuntime(FeatureContext(newApi), AnyClassLoader, true)) {
        "public API generation replacement did not complete"
    }
    val rebound = subscriber.subscription!!
    check(!oldSubscription.active && oldApi.listeners.isEmpty()) {
        "the prior API generation must release business subscriptions"
    }
    check(rebound !== oldSubscription && rebound.active && newApi.listeners.size == 1) {
        "business subscriptions must bind to the replacement API generation"
    }
    oldApi.post()
    newApi.post()
    check(subscriber.callbackCount == 1) { "one public API event must invoke exactly one current callback" }
    check(subscriber.initCalls == 2 && subscriber.installCalls == 2) {
        "business feature state must be restored once after the public API is replaced"
    }
    println("PASS: core init failure blocks downstream, retries once, and successful installs deduplicate")
}

private object AnyClassLoader : ClassLoader()
