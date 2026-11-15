package h.Hchat.hooks.items.quotetail

internal object ChatQuoteTailXml {
    fun build(
        talker: String,
        selfWxId: String,
        title: String,
        nickname: String,
        quoteContent: String
    ): String? {
        if (talker.isBlank() || selfWxId.isBlank() || title.isBlank()) return null
        if (nickname.isBlank() || quoteContent.isBlank()) return null
        val createTimeSeconds = System.currentTimeMillis() / 1000L

        return "<?xml version=\"1.0\"?>" +
            "<msg>" +
            "<appmsg appid=\"\" sdkver=\"0\">" +
            "<title>${escapeXml(title)}</title>" +
            "<type>57</type>" +
            "<appattach><cdnthumbaeskey /><aeskey /></appattach>" +
            "<refermsg>" +
            "<type>1</type>" +
            "<svrid>0</svrid>" +
            "<fromusr>${escapeXml(talker)}</fromusr>" +
            "<chatusr>${escapeXml(selfWxId)}</chatusr>" +
            "<displayname>${escapeXml(nickname)}</displayname>" +
            "<content>${escapeXml(quoteContent)}</content>" +
            "<msgsource></msgsource>" +
            "<strid></strid>" +
            "<createtime>$createTimeSeconds</createtime>" +
            "</refermsg>" +
            "</appmsg>" +
            "<fromusername>${escapeXml(selfWxId)}</fromusername>" +
            "<scene>0</scene>" +
            "<appinfo><version>1</version><appname></appname></appinfo>" +
            "<commenturl></commenturl>" +
            "</msg>"
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
