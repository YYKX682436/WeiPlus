package h.Hchat.hooks.core

/** Ordinary features install their guarded Hook shell; only exceptional early features require enablement. */
internal object FeatureInstallDecision {
    fun shouldInstall(
        enabledOnly: Boolean,
        isEnabled: () -> Boolean,
        onEnabledCheckError: (Throwable) -> Unit
    ): Boolean {
        if (!enabledOnly) return true
        return try {
            isEnabled()
        } catch (error: Throwable) {
            onEnabledCheckError(error)
            false
        }
    }
}
