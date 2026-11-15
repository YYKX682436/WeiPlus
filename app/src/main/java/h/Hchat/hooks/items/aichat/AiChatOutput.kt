package h.Hchat.hooks.items.aichat

/** Formatting is applied only to module-generated answers, never to incoming user text. */
internal object AiChatOutput {
    private val oldMarker = Regex("^(?:\\s*\\[AI回复]\\s*)+")
    private val thoughtOpen = Regex("(?i)<(think|thinking|analysis)(?:\\s[^>]*)?>")

    fun removeOldMarkers(text: String): String = text.replace(oldMarker, "").trim()

    /** A partial thought is not a final answer and must never be sent. */
    fun visibleAnswer(raw: String): String? {
        var text = raw.replace("\u0000", "")
        while (true) {
            val open = thoughtOpen.find(text) ?: break
            val end = Regex("(?i)</${open.groupValues[1]}\\s*>").find(text, open.range.last + 1) ?: return null
            text = text.removeRange(open.range.first, end.range.last + 1)
        }
        if (Regex("(?i)</(?:think|thinking|analysis)\\s*>").containsMatchIn(text)) return null
        return removeOldMarkers(text).takeIf { it.isNotBlank() }
    }

    /** The cap is in Unicode code points rather than UTF-16 code units. */
    fun finalText(raw: String, maximum: Int = 200): String? {
        val clean = visibleAnswer(raw) ?: return null
        val paragraphs = clean.replace("\r\n", "\n").replace('\r', '\n')
            .split(Regex("\\n\\s*\\n"))
            .map { it.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ") }
            .filter(String::isNotBlank).take(2).joinToString("\n\n")
        if (paragraphs.codePointCount(0, paragraphs.length) <= maximum) return paragraphs.takeIf { it.isNotBlank() }
        var end = paragraphs.offsetByCodePoints(0, maximum.coerceAtLeast(1))
        // Do not split combining marks or emoji variation selectors from their base.
        while (end > 0 && end < paragraphs.length) {
            val next = paragraphs.codePointAt(end)
            val previous = paragraphs.codePointBefore(end)
            if (Character.getType(next) != Character.NON_SPACING_MARK.toInt() && next != 0xFE0F && next != 0x200D && next !in 0x1F3FB..0x1F3FF && previous != 0x200D) break
            end = paragraphs.offsetByCodePoints(end, -1)
        }
        val clipped = paragraphs.substring(0, end).trimEnd()
        val sentenceEnd = clipped.indexOfLast { it in "。！？.!?" }
        return (if (sentenceEnd >= clipped.length / 2) clipped.substring(0, sentenceEnd + 1) else clipped).trim().takeIf { it.isNotBlank() }
    }

    fun memoryText(text: String): String = text
        .replace(Regex("(?m)^\\s*(?:\\[AI回复]\\s*)+"), "")
        .replace(Regex("(?m)(#\\d+\\s+)(?:\\[AI回复]\\s*)+"), "$1")
}
