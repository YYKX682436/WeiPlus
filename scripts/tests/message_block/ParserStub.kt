package h.Hchat.hooks.api.message
import h.Hchat.hooks.api.model.WeChatParsedMessage
class WeChatMessageParseApi {
    var result: WeChatParsedMessage? = null
    var throwOnParse: Boolean = false
    fun parseAddMsg(value: Any, selfWxId: String): WeChatParsedMessage? {
        if (throwOnParse) error("parser fixture failure")
        return result
    }
    fun isLikelyAddMsgClass(type: Class<*>): Boolean = false
}
