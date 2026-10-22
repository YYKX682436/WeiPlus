package h.Hchat.hooks.core

/**
 * 所有功能模块的统一接口。
 */
interface Feature {
    enum class InstallationPolicy {
        /** Install the cheap Hook shell and guard feature work at its live callback. */
        PREINSTALL_HOOKS,
        /** Reserve for features whose Hook itself must be absent while disabled. */
        ENABLED_ONLY
    }

    fun featureId(): String

    fun name(): String = featureId()

    fun isEnabled(context: FeatureContext): Boolean {
        return context.configStore().getGlobalBoolean(featureId() + "_enabled", true)
    }

    fun installationPolicy(): InstallationPolicy = InstallationPolicy.PREINSTALL_HOOKS

    @Throws(Throwable::class)
    fun onInit(context: FeatureContext) {
    }

    @Throws(Throwable::class)
    fun install(context: FeatureContext)

    fun onConfigChanged(context: FeatureContext, key: String?) {
    }

    fun onDestroy(context: FeatureContext) {
    }
}
