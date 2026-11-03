import h.Hchat.hooks.core.Feature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.FeatureManager

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
    println("PASS: core init failure blocks downstream, retries once, and successful installs deduplicate")
}
