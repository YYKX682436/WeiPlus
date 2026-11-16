package h.Hchat.hooks.items.aichat

import h.Hchat.hooks.api.model.WeChatMessage

/** Message IDs and a matching hint only; the hint is never presented as a picture description. */
internal data class AiRecentImage(val msgId: Long, val serverId: Long, val imageTime: Long,
                                  val lastUsed: Long, val hint: String = "")
internal data class AiImageMatch(val message: WeChatMessage, val origin: String)

internal object AiImageContext {
    const val MAX_AGE_MS = 5 * 60_000L
    const val MAX_TURNS = 6
    const val HISTORY_LIMIT = 80
    const val MAX_SAVED = 3

    fun sender(message: WeChatMessage): String? {
        if (message.isSend() || message.talker.isBlank()) return null
        if (!message.isGroupChat()) return message.talker
        return message.sendTalker().takeIf { it.isNotBlank() && it != message.talker }
    }

    fun includeInRecent(message: WeChatMessage, participant: String?): Boolean =
        !message.isImage() || !message.isGroupChat() || (participant != null && sender(message) == participant)

    /** History is newest first. Explicit quotes are handled separately by the caller. */
    fun choose(talker: String, participant: String, question: String, now: Long,
               history: List<WeChatMessage>, saved: List<AiRecentImage>,
               resolve: (AiRecentImage) -> WeChatMessage?): AiImageMatch? {
        if (participant.isBlank() || question.isBlank() || now <= 0) return null
        data class Candidate(val message: WeChatMessage, val lastUsed: Long, val hint: String, val origin: String)
        val candidates = mutableListOf<Candidate>()
        for (ref in saved) {
            val message = resolve(ref) ?: continue
            val matchesId = (ref.msgId > 0 && ref.msgId == message.msgId) || (ref.serverId > 0 && ref.serverId == message.msgSvrId)
            if (!matchesId || message.talker != talker || !message.isImage()) continue
            if (ref.msgId > 0 && message.msgId > 0 && ref.msgId != message.msgId) continue
            if (ref.serverId > 0 && message.msgSvrId > 0 && ref.serverId != message.msgSvrId) continue
            candidates += Candidate(message, ref.lastUsed, ref.hint, "association")
        }
        for (message in history) {
            if (message.talker != talker || !message.isImage() || sender(message) != participant ||
                (message.msgId <= 0 && message.msgSvrId <= 0)) continue
            if (candidates.any { sameMessage(it.message, message) }) continue
            candidates += Candidate(message, message.createTime, "", "history")
        }
        val oldestScanned = history.lastOrNull()?.createTime ?: 0
        return candidates.mapNotNull { candidate ->
            val image = candidate.message
            if (image.createTime <= 0 || now - image.createTime !in 0..MAX_AGE_MS || candidate.lastUsed !in image.createTime..now) return@mapNotNull null
            // A full page that starts after last use cannot establish the turn limit safely.
            if (history.size >= HISTORY_LIMIT && oldestScanned > candidate.lastUsed) return@mapNotNull null
            val turns = history.count { it.createTime > candidate.lastUsed && it.createTime <= now && sender(it) == participant }
            if (turns > MAX_TURNS) return@mapNotNull null
            val overlap = overlap(question, candidate.hint)
            if (!related(question, now - candidate.lastUsed, turns, overlap)) return@mapNotNull null
            Triple(candidate, overlap, candidate.lastUsed)
        }.maxWithOrNull(compareBy<Triple<Candidate, Int, Long>> { it.second }.thenBy { it.third }
            .thenBy { it.first.message.createTime }.thenBy { it.first.message.msgId })
            ?.first?.let { AiImageMatch(it.message, it.origin) }
    }

    private fun sameMessage(a: WeChatMessage, b: WeChatMessage): Boolean =
        (a.msgId > 0 && a.msgId == b.msgId) || (a.msgSvrId > 0 && a.msgSvrId == b.msgSvrId)

    private fun related(text: String, elapsed: Long, turns: Int, overlap: Int): Boolean {
        val question = text.trim().take(500)
        val visual = Regex("(?i)图片|照片|相片|画面|图里|图上|看图|\\b(?:image|picture|photo)\\b|刚才.{0,12}看|(?:看到|看见).{0,8}(?:吗|没|呢)")
            .containsMatchIn(question)
        if (visual) return true
        // A clearly different topic should not inherit a picture merely because it uses a pronoun.
        if (overlap == 0 && Regex("天气|股票|股价|汇率|新闻|路况|航班|火车|工资|房价|彩票|比赛比分").containsMatchIn(question)) return false
        if (Regex("(?i)它|那只|这只|that one|this one|its colou?r").containsMatchIn(question) && question.length <= 100) return true
        if (overlap > 0) return true
        if (elapsed <= 60_000 && turns <= 2 && question.length <= 30 &&
            Regex("真的吗|真的是|是吗|那样|这样|然后呢|继续|刚才|之前|上次").containsMatchIn(question)) return true
        return elapsed <= 20_000 && turns <= 1 && question.length <= 30 &&
            Regex("(?i)(?:这个|那个|this|that).{0,12}(?:什么|是啥|是什么|what)").containsMatchIn(question)
    }

    /** Reuse a real saved description only when it contains the detail being asked about. */
    fun canUseDescription(question: String, description: String): Boolean {
        if (question.isBlank() || description.isBlank()) return false
        if (Regex("(?i)颜色|什么色|哪种色|colou?r").containsMatchIn(question)) {
            // A color elsewhere in the picture (for example the background) is not evidence about the subject.
            return Regex("(?:毛色|体色|猫的颜色|狗的颜色)(?:是|为|：|:)?[\\s]*[白黑灰红蓝绿黄橙紫粉棕金银]{1,4}色?")
                .containsMatchIn(description)
        }
        if (Regex("多少|几个|几只|几个人|位置|哪里|在哪|文字|写着|品种|尺寸|多大|做什么|正在干|(?i)how many|where|what.*(?:doing|breed)").containsMatchIn(question)) return false
        return overlap(question, description) > 0
    }

    private fun overlap(question: String, hint: String): Int {
        if (hint.isBlank()) return 0
        val stop = "的一了是在有和与这那你我他她它的呢吗啊呀吧嗯很就还都好可爱漂亮看到只什么怎么颜色照片图片画面"
        val chars = question.filter { it in '\u4e00'..'\u9fff' && it !in stop }.toSet()
        val chinese = chars.count { it in hint }
        val englishStop = setOf("this", "that", "what", "which", "with", "from", "about", "does", "look")
        val words = Regex("[A-Za-z]{4,}").findAll(question.lowercase()).map { it.value }.filterNot { it in englishStop }.toSet()
        return chinese + words.count { Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(hint) }
    }
}
