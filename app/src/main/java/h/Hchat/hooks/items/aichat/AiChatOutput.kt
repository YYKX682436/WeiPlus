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
    fun finalText(raw: String, maximum: Int = 300): String? {
        val clean = visibleAnswer(raw) ?: return null
        val paragraphs = clean.replace("\r\n", "\n").replace('\r', '\n')
            .split(Regex("\\n\\s*\\n"))
            .map { it.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ") }
            .filter(String::isNotBlank).joinToString("\n\n")
        if (paragraphs.codePointCount(0, paragraphs.length) <= maximum) return paragraphs.takeIf { it.isNotBlank() }
        var end = paragraphs.offsetByCodePoints(0, maximum.coerceAtLeast(1))
        // Do not split combining marks or emoji variation selectors from their base.
        while (end > 0 && end < paragraphs.length) {
            val next = paragraphs.codePointAt(end)
            val previous = paragraphs.codePointBefore(end)
            if (Character.getType(next) != Character.NON_SPACING_MARK.toInt() && next != 0xFE0F && next != 0x200D && next !in 0x1F3FB..0x1F3FF && previous != 0x200D) break
            end = paragraphs.offsetByCodePoints(end, -1)
        }
        // A URL must either be kept intact or omitted, never sent as a broken link.
        urlRanges(paragraphs).firstOrNull { end > it.first && end <= it.last }?.let { end = it.first }
        if (end > 0 && end < paragraphs.length &&
            ((paragraphs[end - 1].isDigit() && (paragraphs[end].isDigit() || paragraphs[end] == '.')) ||
                (paragraphs[end - 1] == '.' && end > 1 && paragraphs[end - 2].isDigit() && paragraphs[end].isDigit()))) {
            while (end > 0 && (paragraphs[end - 1].isDigit() || paragraphs[end - 1] == '.')) end--
        }
        val clipped = paragraphs.substring(0, end).trimEnd()
        val urls = urlRanges(clipped)
        val sentenceEnd = clipped.indices.lastOrNull {
            clipped[it] in "。！？.!?" && urls.none { range -> it in range } &&
                !(clipped[it] == '.' && it > 0 && it + 1 < paragraphs.length && paragraphs[it - 1].isDigit() && paragraphs[it + 1].isDigit())
        } ?: -1
        return (if (sentenceEnd >= clipped.length / 2) clipped.substring(0, sentenceEnd + 1) else clipped).trim().takeIf { it.isNotBlank() }
    }

    /** Split only longer answers at natural boundaries; never split a URL or decimal. */
    fun messages(raw: String): List<String> {
        val text = finalText(raw) ?: return emptyList()
        if (text.codePointCount(0, text.length) <= 110) return listOf(text.replace(Regex("\\n+"), " "))
        val urls = urlRanges(text)
        val result = mutableListOf<String>()
        var start = 0
        while (result.size < 2 && text.codePointCount(start, text.length) > 110) {
            val candidates = mutableListOf<Int>()
            var pos = start
            while (pos < text.length) {
                val c = text[pos]
                val isPeriod = c == '.' && (pos + 1 == text.length || text[pos + 1].isWhitespace()) &&
                    !(pos > 0 && text[pos - 1].isDigit() && pos + 1 < text.length && text[pos + 1].isDigit())
                if ((c in "。！？!?\n" || isPeriod) && urls.none { pos in it }) {
                    var end = pos + 1
                    while (end < text.length && text[end] in "”’\"'）)】]") end++
                    val before = text.codePointCount(start, end)
                    val after = text.codePointCount(end, text.length)
                    if (before in 28..150 && after >= 22) candidates += end
                }
                pos++
            }
            val end = candidates.minByOrNull { kotlin.math.abs(text.codePointCount(start, it) - 90) } ?: break
            text.substring(start, end).trim().takeIf(String::isNotBlank)?.let(result::add)
            start = end
            while (start < text.length && text[start].isWhitespace()) start++
        }
        text.substring(start).trim().replace(Regex("\\n+"), " ").takeIf(String::isNotBlank)?.let(result::add)
        return result
    }

    internal fun sendInOrder(parts: List<String>, send: (String, Int) -> Boolean, sent: (String, Int) -> Unit): Int {
        var count = 0
        for ((index, part) in parts.withIndex()) {
            if (!send(part, index)) break
            sent(part, index)
            count++
        }
        return count
    }

    private fun urlRanges(text: String): List<IntRange> = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        .findAll(text).map { it.range }.toList()

    fun memoryText(text: String): String = text
        .replace(Regex("(?m)^\\s*(?:\\[AI回复]\\s*)+"), "")
        .replace(Regex("(?m)(#\\d+\\s+)(?:\\[AI回复]\\s*)+"), "$1")
}
