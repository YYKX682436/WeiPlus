package h.Hchat.hooks.items.aichat

import android.database.sqlite.SQLiteDatabase
import h.Hchat.hooks.api.model.WeChatMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiGroupMediaContextTest {
    private val room = "room@chatroom"
    private val self = "wxid_self"
    private fun msg(id: Long, time: Long, type: Int, sender: String = "alice", talker: String = room,
                    body: String = "", sent: Boolean = false): WeChatMessage = WeChatMessage(
        id, id + 1000, type, 0, if (sent) 1 else 0, time, talker,
        if (!sent && talker.endsWith("@chatroom")) "$sender:\n$body" else body, "", "", "", 0, "", self)

    private fun choose(question: String, now: Long, history: List<WeChatMessage>,
                       saved: List<AiRecentMedia> = emptyList(), mentioned: Set<String> = emptySet()): AiGroupMediaChoice =
        AiGroupMediaContext.choose(room, self, question, now, history, saved, mentioned) { ref ->
            history.firstOrNull { (ref.msgId > 0 && it.msgId == ref.msgId) || (ref.serverId > 0 && it.msgSvrId == ref.serverId) }
        }

    @Test fun otherMemberCanAskAboutAnySingleRecentMedia() {
        for ((type, question) in listOf(3 to "这张图片是什么", 34 to "这段语音说了什么", 43 to "这个视频是什么", 62 to "看看这个视频")) {
            val media = msg(type.toLong(), 100_000, type)
            val result = choose(question, 110_000, listOf(media))
            assertEquals(media.msgId, result.match?.message?.msgId)
            assertEquals("alice", AiGroupMediaContext.sender(result.match!!.message, self))
            assertEquals("history", result.match!!.origin)
            assertFalse(result.ambiguous)
        }
        assertNotNull(choose("这是什么", 110_000, listOf(msg(7, 100_000, 3))).match)
        assertNull(choose("明天天气怎么样", 110_000, listOf(msg(8, 100_000, 3))).match)
        assertNull(choose("这张图片是什么", 220_001, listOf(msg(9, 100_000, 3))).match)
        assertNull(choose("这张图片是什么", 110_000, listOf(msg(9, 100_000, 3, talker = "other@chatroom"))).match)
    }

    @Test fun typeFilterAndAmbiguityNeverUploadMultipleCandidates() {
        val image = msg(1, 100_000, 3)
        val voice = msg(2, 101_000, 34, sender = "bob")
        val secondImage = msg(3, 102_000, 3, sender = "carol")
        assertEquals(image.msgId, choose("图片是什么", 110_000, listOf(voice, image)).match?.message?.msgId)
        assertEquals(voice.msgId, choose("语音说什么", 110_000, listOf(voice, image)).match?.message?.msgId)
        assertTrue(choose("看看这个", 110_000, listOf(voice, image)).ambiguous)
        val ambiguous = choose("图片是什么", 110_000, listOf(secondImage, image))
        assertTrue(ambiguous.ambiguous); assertNull(ambiguous.match)
        val named = choose("图片是什么", 110_000, listOf(secondImage, image), mentioned = setOf("alice", self))
        assertEquals(image.msgId, named.match?.message?.msgId)
        // At most 100 rows are examined and old or foreign messages cannot be candidates.
        val rows = (1..100).map { msg(100L + it, 109_000, 1, body = "闲聊") } + image
        assertNull(choose("图片是什么", 110_000, rows).match)
    }

    @Test fun cachedVisualDetailsAreReusedOnlyWhenTheyAnswerTheQuestion() {
        assertFalse(AiGroupMediaContext.canReuse(AiGroupMediaKind.IMAGE, "它是什么颜色", "一只猫"))
        assertTrue(AiGroupMediaContext.canReuse(AiGroupMediaKind.IMAGE, "它是什么颜色", "猫的毛色是灰白色"))
        assertFalse(AiGroupMediaContext.canReuse(AiGroupMediaKind.VIDEO, "视频里有几个人", "一段视频"))
        assertTrue(AiGroupMediaContext.canReuse(AiGroupMediaKind.VOICE, "语音说了什么", "明天下午见"))
    }

    private fun quote(id: Long, type: Int, author: String, atSelf: Boolean, title: String = "看看这个"): WeChatMessage {
        val xml = "<msg><appmsg><title>$title</title><type>57</type><refermsg><type>$type</type><svrid>$id</svrid><fromusr>$room</fromusr><chatusr>$author</chatusr><content></content></refermsg></appmsg></msg>"
        val source = if (atSelf) "<msgsource><atuserlist>$self</atuserlist></msgsource>" else ""
        return WeChatMessage.fromTransient(room, "bob", xml, 200_000, false, 49, 9999, source, self)
    }

    @Test fun quotesTakePriorityAndMissingOrMismatchedIdsFailClosed() {
        for (type in listOf(3, 34, 43, 62)) {
            val old = msg(type.toLong(), 10_000, type)
            val q = quote(old.msgSvrId, type, "alice", true)
            assertTrue(AiChatRuntime.triggers(q, self, AiChatTarget(room)))
            assertEquals(old.msgId, AiGroupMediaContext.quoted(q) { old }.message?.msgId)
            assertTrue(AiGroupMediaContext.quoted(q) { null }.unavailable)
            assertTrue(AiGroupMediaContext.quoted(quote(0, type, "alice", true)) { old }.unavailable)
            assertTrue(AiGroupMediaContext.quoted(q) { msg(333, 10_000, type) }.unavailable)
        }
        val wrongType = msg(15, 10_000, 34)
        assertTrue(AiGroupMediaContext.quoted(quote(wrongType.msgSvrId, 3, "alice", true)) { wrongType }.unavailable)
        assertTrue(AiGroupMediaContext.quoted(quote(0, 0, "alice", true, "这张图片")) { null }.unavailable)
        assertTrue(AiGroupMediaContext.quoted(quote(0, 0, "alice", true, "看看这个")) { null }.unresolved)
        assertFalse(AiChatRuntime.triggers(quote(1, 3, "alice", false), self, AiChatTarget(room)))
        assertTrue(AiChatRuntime.triggers(quote(0, 49, self, false), self, AiChatTarget(room)))
        assertNull(AiGroupMediaContext.quoted(quote(0, 49, self, false)) { null }.message)
        assertFalse(AiChatRuntime.triggers(msg(3, 100_000, 3), self, AiChatTarget(room)))
        assertFalse(AiChatRuntime.triggers(msg(4, 100_000, 34), self, AiChatTarget(room)))
        assertFalse(AiChatRuntime.triggers(msg(5, 100_000, 43), self, AiChatTarget(room)))
    }

    @Test fun establishedAssociationsSurviveReopenForTwoHoursAndRemainScoped() {
        val account = UUID.randomUUID().toString()
        val media = msg(200, 100_000, 3)
        val memory = AiChatMemory.get(RuntimeEnvironment.getApplication())
        memory.rememberMedia(account, room, "bob", media, 110_000, "一只灰猫", "alice")
        memory.close()
        val reopened = AiChatMemory.get(RuntimeEnvironment.getApplication())
        val refs = reopened.recentGroupMedia(account, room)
        assertEquals(1, refs.size); assertEquals("alice", refs[0].sender); assertEquals("bob", refs[0].participant)
        assertEquals(media.msgId, choose("那只猫呢", 110_000 + 90 * 60_000L, listOf(media), refs).match?.message?.msgId)
        assertNull(choose("那只猫呢", 110_000 + AiImageContext.MAX_AGE_MS + 1, listOf(media), refs).match)
        assertNull(choose("明天天气怎么样", 120_000, listOf(media), refs).match)
        assertTrue(reopened.recentGroupMedia(account, "other@chatroom").isEmpty())
        assertTrue(reopened.recentGroupMedia("other-account", room).isEmpty())
        val voice = msg(201, 120_000, 34, sender = "carol")
        reopened.rememberMedia(account, room, "bob", voice, 130_000, "她说下午见", "carol")
        assertEquals(2, reopened.recentGroupMedia(account, room).size)
        assertEquals(1, reopened.recentImages(account, room, "bob").size)
        assertEquals(voice.msgId, choose("刚才语音说了什么", 140_000, listOf(voice), reopened.recentGroupMedia(account, room)).match?.message?.msgId)
    }

    @Test fun migrationPreservesOldImagesMemoryAndUsage() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE recent_images(account TEXT,talker TEXT,participant TEXT,msg_id INTEGER,server_id INTEGER,image_time INTEGER,last_used INTEGER,match_hint TEXT)")
            db.execSQL("INSERT INTO recent_images VALUES('self','room','bob',1,2,3,4,'hint')")
            db.execSQL("CREATE TABLE media(msg_id INTEGER,description TEXT)")
            db.execSQL("INSERT INTO media VALUES(1,'saved')")
            db.execSQL("CREATE TABLE usage(id INTEGER,input INTEGER)")
            db.execSQL("INSERT INTO usage VALUES(1,42)")
            AiChatMemory.get(RuntimeEnvironment.getApplication()).onUpgrade(db, 4, 5)
            db.rawQuery("SELECT msg_id,media_type,media_sender,match_hint FROM recent_images", null).use {
                assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)); assertEquals(3, it.getInt(1)); assertEquals("", it.getString(2)); assertEquals("hint", it.getString(3))
            }
            db.rawQuery("SELECT description FROM media", null).use { assertTrue(it.moveToFirst()); assertEquals("saved", it.getString(0)) }
            db.rawQuery("SELECT input FROM usage", null).use { assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0)) }
        }
    }

    @Test fun mediaAuthorAndQuestionAuthorAreSeparateInActualRequest() {
        val image = JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,/9g="))
        val mediaInput = AiGroupMediaContext.input("alice", "群成员 Alice 发送的图片", image)
        val question = AiInput("user", "这是什么？", AiChatHistory.participant("bob"))
        val request = listOf(mediaInput, question)
        assertEquals(AiChatHistory.participant("alice"), mediaInput.name)
        assertEquals(AiChatHistory.participant("bob"), question.name)
        assertEquals(1, AiChatRuntime.imagePayloadCount(request))
        val body = AiChatClient.requestBody(AiChatConfig(provider = AiProvider.DEEPSEEK, model = "deepseek-flash"), request, 256)
        val messages: JSONArray = body.getJSONArray("messages")
        assertEquals(mediaInput.name, messages.getJSONObject(0).getString("name"))
        assertEquals(question.name, messages.getJSONObject(1).getString("name"))
        assertEquals("image_url", messages.getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type"))
    }
}
