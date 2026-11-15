package h.Hchat.hooks.items.quotetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class ChatQuoteTailXmlTest {
    @Test
    fun buildsNativeQuoteAppMsgAndEscapesUnicodeAndXmlCharacters() {
        val xml = ChatQuoteTailXml.build(
            talker = "room&one@chatroom",
            selfWxId = "wxid_<self>",
            title = "如图 😀 & <大图>",
            nickname = "慕晨 & \"朋友\"",
            quoteContent = "点歌 小跳蛙 <试听> & \"现在\" '"
        )!!
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val appMsg = document.getElementsByTagName("appmsg").item(0)
        val reference = document.getElementsByTagName("refermsg").item(0)

        assertEquals("57", appMsg.child("type"))
        assertEquals("如图 😀 & <大图>", appMsg.child("title"))
        assertEquals("1", reference.child("type"))
        assertEquals("0", reference.child("svrid"))
        assertTrue(reference.child("createtime").toLong() > 0L)
        assertEquals("room&one@chatroom", reference.child("fromusr"))
        assertEquals("wxid_<self>", reference.child("chatusr"))
        assertEquals("慕晨 & \"朋友\"", reference.child("displayname"))
        assertEquals("", reference.child("strid"))
        assertEquals("点歌 小跳蛙 <试听> & \"现在\" '", reference.child("content"))
        assertEquals("wxid_<self>", document.documentElement.child("fromusername"))
    }

    @Test
    fun returnsNoMessageWhenRequiredFieldsAreMissing() {
        assertEquals(null, ChatQuoteTailXml.build("", "self", "text", "name", "quote"))
        assertEquals(null, ChatQuoteTailXml.build("talker", "", "text", "name", "quote"))
        assertEquals(null, ChatQuoteTailXml.build("talker", "self", "text", " ", "quote"))
        assertEquals(null, ChatQuoteTailXml.build("talker", "self", "text", "name", " "))
    }

    @Test
    fun featureIsOffByDefaultAndLegacyNicknameKeyIsRetained() {
        assertFalse(ChatQuoteTailSettings.DEFAULT_ENABLE)
        assertEquals("chat_quote_tail_nickname", ChatQuoteTailSettings.KEY_NICKNAME)
        assertEquals("", ChatQuoteTailSettings.DEFAULT_CONTENT)
    }

    @Test
    fun actualAccountNicknameWinsOverContactFallback() {
        assertEquals(
            "账号真实昵称",
            ChatQuoteTailPolicy.resolveNickname("账号真实昵称", "联系人昵称", "wxid_self")
        )
        assertEquals(
            "联系人昵称",
            ChatQuoteTailPolicy.resolveNickname("", "联系人昵称", "wxid_self")
        )
        assertEquals("", ChatQuoteTailPolicy.resolveNickname("", "wxid_self", "wxid_self"))
    }

    @Test
    fun policyPreservesRealQuotesAndGroupMentions() {
        assertTrue(eligible(lastQuoteMessageId = 0L))
        assertFalse(eligible(lastQuoteMessageId = 123L))
        assertFalse(eligible(lastQuoteMessageId = null))
        assertFalse(eligible(text = "@someone 看这里", talker = "room@chatroom"))
        assertFalse(eligible(nickname = ""))
        assertFalse(eligible(quoteContent = ""))
    }

    private fun eligible(
        text: String = "如图",
        talker: String = "friend",
        nickname: String = "慕晨",
        quoteContent: String = "点歌 小跳蛙",
        lastQuoteMessageId: Long? = 0L
    ) = ChatQuoteTailPolicy.shouldIntercept(
        enabled = true,
        text = text,
        talker = talker,
        nickname = nickname,
        quoteContent = quoteContent,
        lastQuoteMessageId = lastQuoteMessageId
    )

    private fun org.w3c.dom.Node.child(name: String): String {
        val nodes = this.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeName == name) return node.textContent
        }
        error("Missing <$name> under <${this.nodeName}>")
    }
}
