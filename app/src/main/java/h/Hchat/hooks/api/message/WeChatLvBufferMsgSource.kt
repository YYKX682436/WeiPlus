package h.Hchat.hooks.api.message

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Bounded, read-only extraction of the legacy message source XML from WeChat lvbuffer. */
object WeChatLvBufferMsgSource {
    data class Resolved(val xml: String, val origin: String)

    @JvmStatic
    fun resolve(column: String?, lvbuffer: Any?): Resolved {
        if (!column.isNullOrBlank()) return Resolved(column, "column")
        val xml = extractXml(lvbuffer as? ByteArray)
        return Resolved(xml, if (xml.isNotEmpty()) "lvbuffer" else "none")
    }

    private const val MAX_BUFFER_BYTES = 64 * 1024
    private const val MAX_SCAN_BYTES = 16 * 1024
    private const val MAX_SOURCE_BYTES = 3072
    private val openTag = "<msgsource".toByteArray(StandardCharsets.US_ASCII)
    private val closeTag = "</msgsource>".toByteArray(StandardCharsets.US_ASCII)

    @JvmStatic
    fun extractXml(buffer: ByteArray?): String {
        if (buffer == null || buffer.size < openTag.size + closeTag.size || buffer.size > MAX_BUFFER_BYTES) {
            return ""
        }
        if (buffer.first() == '{'.code.toByte()) {
            val raw = extractStructuredRaw(buffer) ?: return ""
            return findXmlSource(raw.bytes, raw.offset, raw.offset + raw.length) ?: ""
        }
        return findXmlSource(buffer, 0, minOf(buffer.size, MAX_SCAN_BYTES)) ?: ""
    }

    /** Returns the decoded source field from either a structured record or a bounded raw XML buffer. */
    @JvmStatic
    fun extractRawSource(buffer: ByteArray?): String {
        if (buffer == null || buffer.isEmpty() || buffer.size > MAX_BUFFER_BYTES) return ""
        val structured = extractStructuredRaw(buffer) ?: return if (buffer.first() == '{'.code.toByte()) "" else extractXml(buffer)
        return decodeUtf8(structured.bytes, structured.offset, structured.offset + structured.length).orEmpty()
    }

    private data class Slice(val bytes: ByteArray, val offset: Int, val length: Int)

    private fun extractStructuredRaw(bytes: ByteArray): Slice? {
        // WeChat's LVBuffer: '{' + u16 string length + string + 4-byte field + u16 source length + source + '}'.
        if (bytes.size < 10 || bytes.first() != '{'.code.toByte() || bytes.last() != '}'.code.toByte()) return null
        return try {
            val input = ByteBuffer.wrap(bytes).apply { position(1) }
            val firstLength = readLength(input) ?: return null
            if (input.remaining() < firstLength) return null
            input.position(input.position() + firstLength)
            if (input.remaining() < 4) return null
            input.position(input.position() + 4)
            val sourceLength = readLength(input) ?: return null
            if (sourceLength == 0 || sourceLength > MAX_SOURCE_BYTES || input.remaining() < sourceLength + 1) return null
            val start = input.position()
            Slice(bytes, start, sourceLength)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun readLength(input: ByteBuffer): Int? {
        if (input.remaining() < 2) return null
        return (input.short.toInt() and 0xffff).takeIf { it <= MAX_SOURCE_BYTES }
    }

    private fun findXmlSource(bytes: ByteArray, start: Int, end: Int): String? {
        val limit = minOf(end, start + MAX_SCAN_BYTES)
        var open = start
        while (open <= limit - openTag.size && !matchesIgnoreCase(bytes, open, openTag)) open++
        if (open > limit - openTag.size) return null
        var openingEnd = open
        while (openingEnd < limit && bytes[openingEnd] != '>'.code.toByte()) openingEnd++
        if (openingEnd >= limit) return null
        val close = indexOfIgnoreCase(bytes, closeTag, openingEnd + 1, minOf(limit, open + MAX_SOURCE_BYTES))
        if (close < 0) return null
        val finish = close + closeTag.size
        if (finish - open > MAX_SOURCE_BYTES) return null
        return decodeUtf8(bytes, open, finish)
    }

    private fun matchesIgnoreCase(bytes: ByteArray, offset: Int, pattern: ByteArray): Boolean {
        for (i in pattern.indices) {
            val value = bytes[offset + i].toInt() and 0xff
            val normalized = if (value in 'A'.code..'Z'.code) value + ('a'.code - 'A'.code) else value
            if (normalized != (pattern[i].toInt() and 0xff)) return false
        }
        return true
    }

    private fun indexOfIgnoreCase(bytes: ByteArray, pattern: ByteArray, start: Int, end: Int): Int {
        var index = start
        while (index <= end - pattern.size) {
            if (matchesIgnoreCase(bytes, index, pattern)) return index
            index++
        }
        return -1
    }

    private fun decodeUtf8(bytes: ByteArray, start: Int, end: Int): String? = try {
        val chars = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, end - start))
        chars.toString()
    } catch (_: Exception) {
        null
    }
}
