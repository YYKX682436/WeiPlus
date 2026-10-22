package h.Hchat.hooks.api.message

class WeChatMessageParseApi {
    fun getXmlParamByTag(source: String, tag: String): String? =
        Regex("(?is)<${Regex.escape(tag)}[^>]*>(.*?)</${Regex.escape(tag)}\\s*>")
            .find(source)?.groupValues?.getOrNull(1)
}
