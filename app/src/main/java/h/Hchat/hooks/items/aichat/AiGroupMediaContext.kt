package h.Hchat.hooks.items.aichat

import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.api.model.WeChatMessageTypes
import org.json.JSONArray
import org.json.JSONObject

internal enum class AiGroupMediaKind(val type: Int, val label: String) {
    IMAGE(WeChatMessageTypes.IMAGE, "图片"), VOICE(WeChatMessageTypes.VOICE, "语音"), VIDEO(WeChatMessageTypes.VIDEO, "视频")
}

internal data class AiRecentMedia(val msgId: Long, val serverId: Long, val time: Long, val lastUsed: Long,
                                  val hint: String, val type: Int, val sender: String, val participant: String)
internal data class AiGroupMediaMatch(val message: WeChatMessage, val origin: String)
internal data class AiGroupMediaChoice(val match: AiGroupMediaMatch? = null, val ambiguous: Boolean = false)
internal data class AiGroupQuoteChoice(val message: WeChatMessage? = null, val unavailable: Boolean = false,
                                       val unresolved: Boolean = false)

/** Local, bounded lookup only. It never downloads or recognises media. */
internal object AiGroupMediaContext {
    const val RECENT_MS = 2 * 60_000L
    const val HISTORY_LIMIT = 100

    fun kind(type: Int): AiGroupMediaKind? = when (WeChatMessageTypes.normalize(type)) {
        WeChatMessageTypes.IMAGE -> AiGroupMediaKind.IMAGE
        WeChatMessageTypes.VOICE -> AiGroupMediaKind.VOICE
        WeChatMessageTypes.VIDEO, 62 -> AiGroupMediaKind.VIDEO
        else -> null
    }
    fun kind(message: WeChatMessage): AiGroupMediaKind? = kind(message.type)
    fun sender(message: WeChatMessage, self: String): String? =
        if (message.isSend()) self.takeIf { it.isNotBlank() } else AiImageContext.sender(message)

    fun canReuse(kind: AiGroupMediaKind, question: String, description: String): Boolean {
        if (description.isBlank()) return false
        if (kind == AiGroupMediaKind.VOICE || question.isBlank()) return true
        val details = if (kind == AiGroupMediaKind.VIDEO)
            question.replace(Regex("视频|录像|影片|短片|(?i)\\b(?:video|clip)\\b"), "") else question
        return AiImageContext.canUseDescription(details, description)
    }

    fun quoted(message: WeChatMessage, lookup: (Long) -> WeChatMessage?): AiGroupQuoteChoice {
        val quote = message.getQuoteMsg() ?: return AiGroupQuoteChoice()
        val expected = kind(quote.type)
        val source = if (quote.svrId > 0) lookup(quote.svrId)?.takeIf {
            it.talker == message.talker && it.msgSvrId == quote.svrId
        } else null
        val actual = source?.let(::kind)
        if (expected != null && (source == null || actual != expected)) return AiGroupQuoteChoice(unavailable = true, unresolved = true)
        // An untyped quote about media with no resolvable source must not silently become an unrelated recent media.
        if (expected == null && quote.type == 0 && source == null &&
            (explicitlyMentioned(quote.title) != null || explicitlyMentioned(quote.content) != null ||
                Regex("(?i)<(?:img|voicemsg|videomsg)(?:\\s|/|>)").containsMatchIn(quote.content)))
            return AiGroupQuoteChoice(unavailable = true, unresolved = true)
        return AiGroupQuoteChoice(message = source?.takeIf { actual != null }, unresolved = source == null)
    }

    fun explicitlyMentioned(text: String): AiGroupMediaKind? {
        val types = buildList {
            if (Regex("(?i)图片|照片|相片|图像|图里|图上|看图|\\b(?:image|picture|photo)\\b").containsMatchIn(text)) add(AiGroupMediaKind.IMAGE)
            if (Regex("(?i)语音|录音|音频|声音|听(?:听|一下|一听|到|见)|\\b(?:voice|audio)\\b").containsMatchIn(text)) add(AiGroupMediaKind.VOICE)
            if (Regex("(?i)视频|录像|影片|短片|\\b(?:video|clip)\\b").containsMatchIn(text)) add(AiGroupMediaKind.VIDEO)
        }
        return types.singleOrNull()
    }

    /** Explicit media words and short contextual references are signals, not proof of a specific object. */
    private fun relevant(question: String, elapsed: Long, hint: String): Boolean {
        if (Regex("天气|股票|股价|汇率|新闻|路况|航班|火车|工资|房价|彩票|比赛比分").containsMatchIn(question)) return false
        if (explicitlyMentioned(question) != null) return true
        if (AiImageContext.related(question, elapsed, AiImageContext.overlap(question, hint))) return true
        return question.length <= 100 && Regex("看看|看一下|看一眼|描述一下|分析一下|看清|这(?:是|个|条|张|段).{0,12}(?:啥|什么|怎么样)|那(?:是|个|条|张|段).{0,12}(?:啥|什么|怎么样)|说了什么|讲了什么|是什么颜色").containsMatchIn(question)
    }

    private fun same(a: WeChatMessage, b: WeChatMessage): Boolean =
        (a.msgId > 0 && a.msgId == b.msgId) || (a.msgSvrId > 0 && a.msgSvrId == b.msgSvrId)

    fun choose(talker: String, self: String, question: String, now: Long, history: List<WeChatMessage>,
               saved: List<AiRecentMedia>, mentioned: Set<String> = emptySet(),
               resolve: (AiRecentMedia) -> WeChatMessage?): AiGroupMediaChoice {
        if (!WeChatMessage.isGroupTalker(talker) || question.isBlank() || now <= 0) return AiGroupMediaChoice()
        data class Candidate(val message: WeChatMessage, val lastUsed: Long, val hint: String, val origin: String)
        val candidates = mutableListOf<Candidate>()
        val desired = explicitlyMentioned(question)
        for (message in history.take(HISTORY_LIMIT)) {
            val type = kind(message) ?: continue
            if (message.talker != talker || sender(message, self) == null || (message.msgId <= 0 && message.msgSvrId <= 0) ||
                message.createTime <= 0 || now - message.createTime !in 0..RECENT_MS || (desired != null && type != desired)) continue
            if (candidates.none { same(it.message, message) }) candidates += Candidate(message, message.createTime, "", "history")
        }
        for (ref in saved) {
            if (ref.lastUsed <= 0 || now - ref.lastUsed !in 0..AiImageContext.MAX_AGE_MS) continue
            val message = resolve(ref) ?: continue
            val type = kind(message) ?: continue
            if (message.talker != talker || sender(message, self) == null || kind(ref.type) != type ||
                (desired != null && desired != type) ||
                !((ref.msgId > 0 && ref.msgId == message.msgId) || (ref.serverId > 0 && ref.serverId == message.msgSvrId)) ||
                (ref.msgId > 0 && message.msgId > 0 && ref.msgId != message.msgId) ||
                (ref.serverId > 0 && message.msgSvrId > 0 && ref.serverId != message.msgSvrId)) continue
            val index = candidates.indexOfFirst { same(it.message, message) }
            val candidate = Candidate(message, ref.lastUsed, ref.hint, "association")
            if (index < 0) candidates += candidate
            else if (ref.lastUsed >= candidates[index].lastUsed) candidates[index] = candidate
        }
        var relevant = candidates.filter { relevant(question, now - it.lastUsed, it.hint) }
        if (relevant.isEmpty()) return AiGroupMediaChoice()
        val named = mentioned - self - "notify@all" - "announcement@all"
        if (named.isNotEmpty()) {
            val bySender = relevant.filter { sender(it.message, self) in named }
            if (bySender.isNotEmpty()) relevant = bySender
        }
        if (relevant.size > 1) {
            val hintMatches = relevant.filter { AiImageContext.overlap(question, it.hint) > 0 }
            if (hintMatches.size == 1) relevant = hintMatches
            else {
                val recent = relevant.filter { now - it.message.createTime in 0..RECENT_MS }
                if (recent.size == 1 && hintMatches.isEmpty()) relevant = recent
            }
        }
        if (relevant.size != 1) return AiGroupMediaChoice(ambiguous = true)
        val chosen = relevant.single()
        return AiGroupMediaChoice(AiGroupMediaMatch(chosen.message, chosen.origin))
    }

    /** Keep the media author's identity separate from the later question author's identity. */
    fun input(sender: String, label: String, payload: JSONObject? = null): AiInput {
        val content: Any = if (payload == null) label else JSONArray()
            .put(JSONObject().put("type", "text").put("text", label)).put(payload)
        return AiInput("user", content, AiChatHistory.participant(sender))
    }
}
