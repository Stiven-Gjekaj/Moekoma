package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** The chapters of a novel or of an online series in the folder of its book. Each importer of text writes it. */
@Serializable
data class Novel(
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val language: String? = null,
    /** True when the pages go from the right to the left, as in a Japanese novel. */
    val rightToLeft: Boolean = false,
    val chapters: List<Chapter>,
) {
    @Serializable
    data class Chapter(
        /** The title from the table of contents, or null when the book does not give one. */
        val title: String? = null,
        /** The path of the XHTML or HTML file, from the folder of the book. */
        val path: String,
        /** The ID of the chapter in its online source. Its file comes when the user opens it. */
        val remote: String? = null,
    )

    fun save(folder: File) {
        File(folder, FILE_NAME).writeText(json.encodeToString(serializer(), this))
    }

    companion object {
        const val FILE_NAME = "novel.json"
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; explicitNulls = false }

        fun load(folder: File): Novel = json.decodeFromString(serializer(), File(folder, FILE_NAME).readText())
    }
}

/** Writes text as simple XHTML, so that each importer of text gives the same kind of chapter. */
object Xhtml {
    fun escape(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    fun document(title: String?, body: String, language: String? = null): String {
        val lang = language?.let { " xml:lang=\"${escape(it)}\" lang=\"${escape(it)}\"" } ?: ""
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml"$lang>
            |<head><meta charset="UTF-8"/><title>${escape(title ?: "")}</title></head>
            |<body>
            |$body
            |</body>
            |</html>
        """.trimMargin()
    }

    /** The paragraphs of plain text. A blank line ends a paragraph. With no blank line, each line is a paragraph. */
    fun paragraphs(text: String): String {
        val normal = text.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = if ("\n\n" in normal) normal.split("\n\n").map { it.replace('\n', ' ') } else normal.split("\n")
        return blocks.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") { "<p>${escape(it)}</p>" }
    }
}

/** Reads text in the encoding that it has. */
object TextDecoding {
    fun decode(data: ByteArray): String {
        if (data.startsWith(0xEF, 0xBB, 0xBF)) return String(data, 3, data.size - 3, Charsets.UTF_8)
        if (data.startsWith(0xFF, 0xFE) || data.startsWith(0xFE, 0xFF)) return String(data, Charsets.UTF_16)
        strict(data, Charsets.UTF_8)?.let { return it }
        // Many old web novels are in Shift JIS or GB18030. GB18030 reads almost any bytes,
        // so Shift JIS comes first when it gives kana. GB text in Shift JIS gives half-width kana.
        strict(data, Charset.forName("Shift_JIS"))?.let { text ->
            val kana = text.count { it.code in 0x3040..0x30FF }
            val halfWidth = text.count { it.code in 0xFF61..0xFF9F }
            if (kana > 0 && halfWidth * 20 < text.length) return text
        }
        // Text with no byte above 0x7F is UTF-8 already, so only text with such bytes is here.
        // A western text in Windows-1252 has few of them, and GB18030 makes pairs of them.
        val high = data.count { it.toInt() and 0xFF >= 0x80 }
        if (high * 10 > data.size) strict(data, Charset.forName("GB18030"))?.let { return it }
        return String(data, Charset.forName("windows-1252"))
    }

    private fun strict(data: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(vararg bytes: Int) =
        size >= bytes.size && bytes.indices.all { this[it].toInt() and 0xFF == bytes[it] }
}
