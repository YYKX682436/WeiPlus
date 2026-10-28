package h.Hchat.hooks.api.core
import h.Hchat.hooks.api.message.WeChatMessageParseApi
object WeChatApis {
    var parser: WeChatMessageParseApi? = null
    fun messageParser(): WeChatMessageParseApi? = parser
    fun account(): Account? = null
}
class Account { fun selfWxId(): String = "self" }
