package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.utils.KavaReflector
import io.github.libxposed.api.XposedInterface

/** Called only after a successful host storage write. */
internal object LinkParseWriteGate {
    fun intercept(
        chain: XposedInterface.Chain,
        enabled: () -> Boolean,
        lookup: (Long) -> WeChatMessage?,
        accept: (WeChatMessage) -> Unit,
        onError: () -> Unit
    ): Any? {
        val args = chain.args
        val result = chain.proceed()
        try {
            if (enabled()) {
                val id = (args.getOrNull(0) as? Number)?.toLong() ?: 0L
                observe((result as? Number)?.toInt(), id, args.getOrNull(1), lookup, accept)
            }
        } catch (_: Throwable) { runCatching { onError() } }
        return result
    }

    fun observe(result: Int?, id: Long, native: Any?, lookup: (Long) -> WeChatMessage?, accept: (WeChatMessage) -> Unit) {
        if (result == null || result <= 0 || id <= 0 || native == null) return
        val status = KavaReflector.findFieldRecursive(native.javaClass, "field_status")?.get(native) as? Number
        val sent = KavaReflector.findFieldRecursive(native.javaClass, "field_isSend")?.get(native) as? Number
        if (status?.toInt() != 2 || sent?.toInt() != 1) return
        val talker = KavaReflector.findFieldRecursive(native.javaClass, "field_talker")?.get(native) as? String
        if (talker.isNullOrBlank()) return
        val message = lookup(id) ?: return
        if (!message.isOutgoing() || message.status != 2 || message.msgId != id || message.talker != talker) return
        accept(message)
    }
}
