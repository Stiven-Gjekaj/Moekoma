package io.github.stivengjekaj.moekoma.core

/**
 * Reads the title and the author of a PDF from its Info dictionary. The renderer of Android does not
 * give them. A PDF that keeps its Info dictionary in a compressed object stream gives nothing, and the
 * app uses the name of the file.
 */
object PdfMetadata {
    data class Info(val title: String?, val author: String?)

    fun read(data: ByteArray): Info {
        val text = String(data, Charsets.ISO_8859_1)
        return Info(field(text, "Title"), field(text, "Author"))
    }

    /** The last value of the key, because an update of a PDF adds a new Info dictionary at the end. */
    private fun field(text: String, key: String): String? {
        var found: String? = null
        var start = 0
        while (true) {
            val at = text.indexOf("/$key", start)
            if (at < 0) break
            start = at + key.length + 1
            // "/TitleX" is another key.
            if (start < text.length && (text[start].isLetterOrDigit())) continue
            var i = start
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break
            val value = when (text[i]) {
                '(' -> literal(text, i)
                '<' -> if (i + 1 < text.length && text[i + 1] != '<') hex(text, i) else null
                else -> null
            }
            if (value != null) found = value
        }
        return found?.trim()?.ifEmpty { null }
    }

    /** A string such as (Night \(1\) Notes), with its escapes and its nested parentheses. */
    private fun literal(text: String, open: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        var depth = 0
        var i = open
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '(' && depth++ == 0 -> {}
                ch == '(' -> bytes.write(ch.code)
                ch == ')' && --depth == 0 -> return decode(bytes.toByteArray())
                ch == ')' -> bytes.write(ch.code)
                ch == '\\' && i + 1 < text.length -> {
                    i++
                    when (val next = text[i]) {
                        'n' -> bytes.write('\n'.code)
                        'r' -> bytes.write('\r'.code)
                        't' -> bytes.write('\t'.code)
                        'b' -> bytes.write(8)
                        'f' -> bytes.write(12)
                        in '0'..'7' -> {
                            var value = next - '0'
                            var count = 1
                            while (count < 3 && i + 1 < text.length && text[i + 1] in '0'..'7') {
                                i++
                                value = value * 8 + (text[i] - '0')
                                count++
                            }
                            bytes.write(value and 0xFF)
                        }
                        '\n', '\r' -> {}
                        else -> bytes.write(next.code)
                    }
                }
                else -> bytes.write(ch.code)
            }
            i++
        }
        return null
    }

    /** A string such as <FEFF004E>. */
    private fun hex(text: String, open: Int): String? {
        val close = text.indexOf('>', open)
        if (close < 0) return null
        val digits = text.substring(open + 1, close).filter { !it.isWhitespace() }
        val padded = if (digits.length % 2 == 1) digits + "0" else digits
        val bytes = runCatching { ByteArray(padded.length / 2) { padded.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull() ?: return null
        return decode(bytes)
    }

    /** A text string of a PDF is UTF-16 with a byte order mark, or PDFDocEncoding, which is close to Latin-1. */
    private fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.ISO_8859_1)
    }
}
