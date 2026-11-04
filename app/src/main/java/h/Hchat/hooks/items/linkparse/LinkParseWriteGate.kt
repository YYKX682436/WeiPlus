package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.utils.KavaReflector

/** Called only after a successful host storage write. */
internal object LinkParseWriteGate {
    fun observe(result: Int?, id: Long, native: Any?, lookup: (Long) -> WeChatMessage?, accept: (WeChatMessage) -> Unit) {
        if (result == null || result <= 0 || id <= 0 || native == null) return
        val status = KavaReflector.findFieldRecursive(native.javaClass, "field_status")?.get(native) as? Number
        val sent = KavaReflector.findFieldRecursive(native.javaClass, "field_isSend")?.get(native) as? Number
        if (status?.toInt() != 2 || sent?.toInt() != 1) return
        val message = lookup(id) ?: return
        if (!message.isOutgoing() || message.status != 2 || message.msgId != id) return
        accept(message)
    }
}
