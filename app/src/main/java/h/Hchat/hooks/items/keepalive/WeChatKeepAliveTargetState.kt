package h.Hchat.hooks.items.keepalive

import h.Hchat.WechatPackage

internal data class WeChatKeepAliveTargetOptions(
    val watchdog: Boolean,
    val networkHeartbeat: Boolean
)

/** Persistent service targets are package scoped; the module process is never a host identity. */
internal object WeChatKeepAliveTargetState {
    fun serviceOptions(
        enabled: Boolean,
        foregroundService: Boolean,
        watchdog: Boolean,
        networkHeartbeat: Boolean
    ): WeChatKeepAliveTargetOptions? =
        if (enabled && foregroundService) WeChatKeepAliveTargetOptions(watchdog, networkHeartbeat) else null

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

    fun encode(targets: Map<String, WeChatKeepAliveTargetOptions>): Set<String> =
        targets.entries
            .filter { WechatPackage.isTarget(it.key) }
            .mapTo(linkedSetOf()) { (hostPackage, options) ->
                "$hostPackage\t${options.watchdog}\t${options.networkHeartbeat}"
            }

    fun decode(records: Set<String>?): Map<String, WeChatKeepAliveTargetOptions> {
        val result = linkedMapOf<String, WeChatKeepAliveTargetOptions>()
        records.orEmpty().forEach { record ->
            val fields = record.split('\t')
            val hostPackage = fields.getOrNull(0)
            if (fields.size != 3 || !WechatPackage.isTarget(hostPackage)) return@forEach
            val watchdog = fields[1].toBooleanStrictOrNull() ?: return@forEach
            val heartbeat = fields[2].toBooleanStrictOrNull() ?: return@forEach
            result[hostPackage!!] = WeChatKeepAliveTargetOptions(watchdog, heartbeat)
        }
        return result
    }

    fun shouldLaunch(hostPackage: String?, processAlive: Boolean): Boolean =
        WechatPackage.isTarget(hostPackage) && !processAlive
}
