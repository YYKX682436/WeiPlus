package h.Hchat.hooks.core

object HookRegistry {
    @JvmStatic fun get(): HookRegistry = this
    fun installationGeneration(): Long = 1L
}
