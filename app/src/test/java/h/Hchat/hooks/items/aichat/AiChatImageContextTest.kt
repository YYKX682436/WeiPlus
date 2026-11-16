package h.Hchat.hooks.items.aichat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import h.Hchat.hooks.api.model.WeChatMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatImageContextTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun message(id: Long, time: Long, text: String = "", type: Int = 1,
                        talker: String = "friend", sender: String = "friend", sent: Boolean = false,
                        imagePath: String = ""): WeChatMessage = WeChatMessage(
        id, id + 1000, type, 0, if (sent) 1 else 0, time, talker,
        if (talker.endsWith("@chatroom") && !sent) "$sender:\n$text" else text,
        imagePath, "", "", 0, "", "self")

    @Test fun singleImageAndTextAfterThreeSecondsAreOneBatch() {
        val image = message(1, 100_000, type = 3)
        val question = message(2, 103_000, "现在能看到吗")
        val batch = AiChatBatch()
        batch.add(image, 0)
        assertEquals(4200L, batch.waitMillis(0))
        assertEquals(1200L, batch.waitMillis(3000))
        batch.add(question, 3000)
        assertEquals(1500L, batch.waitMillis(3000))
        assertEquals(0L, batch.waitMillis(4500))
        assertEquals(listOf(image, question), batch.take())
        assertFalse(batch.isNotEmpty())

        val single = AiChatBatch()
        single.add(image, 0)
        assertEquals(0L, single.waitMillis(4200))
        assertEquals(listOf(image), single.take())
    }

    @Test fun groupBatchesNeverMixParticipantsAndHaveAMaximumWait() {
        val room = "room@chatroom"
        val image = message(1, 100_000, type = 3, talker = room, sender = "alice")
        val bob = message(2, 101_000, "看看", talker = room, sender = "bob")
        val nextAlice = message(3, 102_000, "那只猫呢", talker = room, sender = "alice")
        val batch = AiChatBatch()
        batch.add(image, 0); batch.add(bob, 1000); batch.add(nextAlice, 2000)
        assertEquals(listOf(image), batch.take())
        assertEquals(listOf(bob), batch.take())
        assertEquals(listOf(nextAlice), batch.take())
        assertEquals(0L, AiChatRuntime.debounceWait(0, 7400, 7500, 7000))
    }

    @Test fun imageFollowupsSurviveAssistantAndInterveningMessagesButNotUnrelatedTopics() {
        val image = message(10, 100_000, type = 3)
        val answer = message(11, 104_000, "一只灰白色的小猫", sent = true)
        val other = message(12, 110_000, "好可爱", sent = false)
        val assistant = message(13, 112_000, "是呀", sent = true)
        val history = listOf(assistant, other, answer, image)
        val ref = AiRecentImage(image.msgId, image.msgSvrId, image.createTime, 104_000, "一只灰白色的小猫，好可爱")
        fun match(question: String, saved: List<AiRecentImage> = listOf(ref)) =
            AiImageContext.choose("friend", "friend", question, 125_000, history, saved) { image }
        assertEquals("association", match("那只猫呢")?.origin)
        assertEquals(image.msgId, match("它是什么颜色")?.message?.msgId)
        assertEquals(image.msgId, match("你刚才不是看到了吗")?.message?.msgId)
        assertEquals(image.msgId, match("真的是那样吗")?.message?.msgId)
        assertNull(match("明天天气怎么样"))
        assertNull(match("这个套餐多少钱"))
        assertNull(match("你也很可爱"))
        // Reconstruct from local messages even without a saved association, as on upgrade/restart.
        assertEquals("history", match("那只猫呢", emptyList())?.origin)
        assertTrue(AiImageContext.canUseDescription("它是什么颜色", "猫的毛色是灰白色"))
        assertFalse(AiImageContext.canUseDescription("它是什么颜色", "白色背景前的一只猫"))
        assertTrue(AiImageContext.canUseDescription("那只猫呢", "一只灰白色小猫"))
        assertFalse(AiImageContext.canUseDescription("它是什么颜色", "一只猫"))
        assertFalse(AiImageContext.canUseDescription("图片里有几只猫", "一只灰白色小猫"))
    }

    @Test fun associationIsBoundedAndIsolatedByAccountConversationAndGroupSender() {
        val room = "room@chatroom"
        val aliceImage = message(21, 100_000, type = 3, talker = room, sender = "alice")
        val bobImage = message(22, 101_000, type = 3, talker = room, sender = "bob")
        val history = listOf(bobImage, aliceImage)
        fun match(sender: String, now: Long, rows: List<WeChatMessage> = history) =
            AiImageContext.choose(room, sender, "那只猫呢", now, rows, emptyList()) { null }
        assertEquals(aliceImage.msgId, match("alice", 120_000)?.message?.msgId)
        assertEquals(bobImage.msgId, match("bob", 120_000)?.message?.msgId)
        assertNull(match("charlie", 120_000))
        assertTrue(AiImageContext.includeInRecent(aliceImage, "alice"))
        assertFalse(AiImageContext.includeInRecent(bobImage, "alice"))
        assertFalse(AiImageContext.includeInRecent(aliceImage, null))
        assertNull(match("alice", 401_000))
        val turns = (1..7).map { message((30 + it).toLong(), 101_000 + it * 1000L, "闲聊", talker = room, sender = "alice") }.reversed()
        assertNull(match("alice", 120_000, turns + aliceImage))
        val secondAliceImage = message(23, 102_000, type = 3, talker = room, sender = "alice")
        val refs = listOf(aliceImage, secondAliceImage).map { AiRecentImage(it.msgId, it.msgSvrId, it.createTime, 105_000) }
        val latest = AiImageContext.choose(room, "alice", "它是什么颜色", 120_000,
            listOf(secondAliceImage, aliceImage), refs) { ref -> listOf(aliceImage, secondAliceImage).first { it.msgId == ref.msgId } }
        assertEquals(secondAliceImage.msgId, latest?.message?.msgId)

        val memory = AiChatMemory.get(context)
        val account = UUID.randomUUID().toString()
        memory.rememberImage(account, room, "alice", aliceImage, 110_000, "一只猫")
        assertEquals(1, memory.recentImages(account, room, "alice").size)
        assertTrue(memory.recentImages(account, room, "bob").isEmpty())
        assertTrue(memory.recentImages(account, "another@chatroom", "alice").isEmpty())
        assertTrue(memory.recentImages("other", room, "alice").isEmpty())
        assertNull(memory.media(account, room, aliceImage.msgId))
        (1..4).forEach { index ->
            val more = message(50L + index, 110_000L + index * 1000, type = 3, talker = room, sender = "alice")
            memory.rememberImage(account, room, "alice", more, more.createTime, "看到了")
        }
        val limited = memory.recentImages(account, room, "alice")
        assertEquals(AiImageContext.MAX_SAVED, limited.size)
        assertFalse(limited.any { it.msgId == aliceImage.msgId })
        assertEquals(54L, limited.first().msgId)

        val transient = WeChatMessage.fromTransient(room, "alice", "<msg><img/></msg>", 130_000, false, 3, 999, "", "self")
        memory.rememberImage(account, room, "alice", transient, 131_000, "看到了")
        val onlyServerId = memory.recentImages(account, room, "alice").first()
        assertEquals(0L, onlyServerId.msgId); assertEquals(999L, onlyServerId.serverId)
        assertEquals(999L, AiImageContext.choose(room, "alice", "现在能看到吗", 132_000,
            listOf(transient), listOf(onlyServerId)) { transient }?.message?.msgSvrId)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun actualImagePayloadIsResentAfterRestartWithoutInventingADescription() {
        val file = File.createTempFile("ai-followup-image-", ".png", context.cacheDir)
        val account = UUID.randomUUID().toString()
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val image = message(90, 100_000, type = 3, imagePath = file.absolutePath)
            val memory = AiChatMemory.get(context)
            val first = AiChatMedia.image(context, image)
            val inputs = listOf(AiInput("user", JSONArray().put(JSONObject().put("type", "text").put("text", "看看" )).put(first)))
            assertEquals(1, AiChatRuntime.imagePayloadCount(inputs))
            assertEquals(0, AiChatRuntime.imagePayloadCount(listOf(AiInput("user", "收到一张图片"))))
            for (provider in listOf(AiProvider.DEEPSEEK, AiProvider.MIMO)) {
                val config = AiChatConfig(provider = provider, model = if (provider == AiProvider.DEEPSEEK) "deepseek-flash" else "mimo-v2.6-flash")
                val body = AiChatClient.requestBody(config, inputs, 256)
                assertTrue(body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1)
                    .getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
            }
            val natural = AiChatRuntime.mediaAnswer("看到了", listOf(image.msgId)) { id, text -> memory.saveMedia(account, "friend", id, text) }
            assertEquals("看到了", natural)
            assertNull(memory.media(account, "friend", image.msgId))
            memory.rememberImage(account, "friend", "friend", image, 104_000, natural)
            memory.close() // SQLiteOpenHelper reopens the persisted database.
            val saved = AiChatMemory.get(context).recentImages(account, "friend", "friend")
            val match = AiImageContext.choose("friend", "friend", "那只猫呢", 125_000,
                listOf(message(91, 110_000, "谢谢"), image), saved) { image }
            assertEquals(image.msgId, match?.message?.msgId)
            val next = listOf(AiInput("user", JSONArray().put(JSONObject().put("type", "text").put("text", "那只猫呢"))
                .put(AiChatMedia.image(context, match!!.message))))
            assertEquals(1, AiChatRuntime.imagePayloadCount(next))
            assertNull(AiChatMemory.get(context).media(account, "friend", image.msgId))
        } finally { file.delete() }
    }

    @Test fun databaseUpgradeAddsAssociationWithoutChangingExistingData() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE media(msg_id INTEGER, description TEXT)")
            db.execSQL("INSERT INTO media VALUES (1, 'saved')")
            db.execSQL("CREATE TABLE usage(id INTEGER, input INTEGER)")
            db.execSQL("INSERT INTO usage VALUES (1, 42)")
            AiChatMemory.get(context).onUpgrade(db, 3, 4)
            db.rawQuery("SELECT description FROM media", null).use { assertTrue(it.moveToFirst()); assertEquals("saved", it.getString(0)) }
            db.rawQuery("SELECT input FROM usage", null).use { assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0)) }
            db.rawQuery("SELECT COUNT(*) FROM recent_images", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun mockHttpReceivesImageForInitialAndFollowupAndReportsUsageOnceEach() {
        val captured = mutableListOf<JSONObject>()
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val worker = thread(isDaemon = true) {
                listOf("看到了", "灰白色").forEach { answer ->
                    server.accept().use { socket ->
                        val stream = socket.getInputStream()
                        val header = ByteArrayOutputStream()
                        while (!header.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
                            val value = stream.read()
                            if (value < 0 || header.size() > 16_384) break
                            header.write(value)
                        }
                        val length = header.toString("ISO-8859-1").lineSequence()
                            .first { it.startsWith("Content-Length:", ignoreCase = true) }.substringAfter(':').trim().toInt()
                        val request = JSONObject(String(stream.readNBytes(length), Charsets.UTF_8))
                        synchronized(captured) { captured += request }
                        val response = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", answer))))
                            .put("usage", JSONObject().put("prompt_tokens", 20).put("completion_tokens", 4)).toString().toByteArray()
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.getOutputStream().write(response)
                    }
                }
            }
            val config = AiChatConfig(provider = AiProvider.CUSTOM, apiKey = "mock", model = "mock-vision",
                baseUrl = "http://127.0.0.1:${server.localPort}", image = true)
            val imagePart = JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,/9g="))
            val usage = mutableListOf<AiUsage?>()
            for (question in listOf("收到一张图片", "那只猫呢")) {
                val input = JSONArray().put(JSONObject().put("type", "text").put("text", question)).put(imagePart)
                val answer = AiChatClient.complete(config, listOf(AiInput("user", input)), onUsage = { usage += it })
                assertTrue(answer.text in listOf("看到了", "灰白色"))
            }
            worker.join(5000)
            assertFalse(worker.isAlive)
            assertEquals(2, captured.size)
            assertEquals(2, usage.size)
            assertTrue(usage.all { it?.input == 20L && it.output == 4L })
            captured.forEach { request ->
                val parts = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
                assertEquals("data:image/jpeg;base64,/9g=", parts.getJSONObject(1).getJSONObject("image_url").getString("url"))
            }
        }
    }
}
