package h.Hchat.hooks.items.keepalive

import h.Hchat.WechatPackage

internal data class WeChatKeepAliveTargetOptions(
    val watchdog: Boolean,
    val networkHeartbeat: Boolean
)

/** Active service targets are package scoped; registrations live only for the bound runtime. */
internal object WeChatKeepAliveTargetState {
    fun serviceOptions(
        enabled: Boolean,
        foregroundService: Boolean,
        watchdog: Boolean,
        networkHeartbeat: Boolean
    ): WeChatKeepAliveTargetOptions? =
        if (enabled && foregroundService && (watchdog || networkHeartbeat)) {
            WeChatKeepAliveTargetOptions(watchdog, networkHeartbeat)
        } else {
            null
        }

    fun update(
        current: Map<String, WeChatKeepAliveTargetOptions>,
        hostPackage: String?,
        options: WeChatKeepAliveTargetOptions?
    ): Map<String, WeChatKeepAliveTargetOptions> {
        if (!WechatPackage.isTarget(hostPackage)) return current.filterKeys(WechatPackage::isTarget)
        return current.filterKeys(WechatPackage::isTarget).toMutableMap().apply {
            if (options == null) remove(hostPackage!!) else put(hostPackage!!, options)
        }
    }

    fun shouldLaunch(hostPackage: String?, processAlive: Boolean): Boolean =
        WechatPackage.isTarget(hostPackage) && !processAlive
}

internal object WeChatKeepAliveCallerPolicy {
    fun isAuthorized(hostPackage: String?, callerPackages: Array<out String>?): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        val packages = callerPackages?.toSet() ?: return false
        // Binder identifies a UID, not a package. Refuse shared UIDs (even if every package
        // is supported) because one host must never control or remove the other.
        return packages.size == 1 && packages.single() == hostPackage
    }
}

/** Safe parsing and restoration rules for the opt-in Root-only keep-alive adjustments. */
internal object WeChatKeepAliveRootState {
    private val modes = setOf("allow", "ignore", "default", "foreground", "errored", "ask", "deny")

    fun isPackageWhitelisted(output: String, hostPackage: String): Boolean =
        output.split(Regex("[,\\s]+")).any { it.trim() == hostPackage }

    fun parseAppOpMode(output: String, operation: String): String? {
        if (output.contains("No operations.", ignoreCase = true)) return "default"
        val lines = output.lineSequence().map(String::trim).toList()
        if (lines.any { it.startsWith("Default mode:", ignoreCase = true) }) return "default"
        val line = lines.firstOrNull { it.startsWith("$operation:") } ?: return null
        val mode = line.substringAfter(':').trim().substringBefore(';').substringBefore(' ').lowercase()
        return mode.takeIf { it in modes }
    }

    fun dozeWhitelistShouldBeRemoved(originallyWhitelisted: Boolean, currentlyWhitelisted: Boolean): Boolean =
        !originallyWhitelisted && currentlyWhitelisted

    fun appOpModeToRestore(currentMode: String, originalMode: String): String? =
        originalMode.takeIf { currentMode == "allow" && originalMode != "allow" }
}
