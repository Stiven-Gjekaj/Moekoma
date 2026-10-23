package io.github.stivengjekaj.moekoma.core

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** The metadata of a comic from its ComicInfo.xml, the format of ComicRack. */
data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: String? = null,
    val writer: String? = null,
    val summary: String? = null,
    val languageISO: String? = null,
    /** "Yes", "YesAndRightToLeft", "No", or "Unknown". */
    val manga: String? = null,
) {
    /** The direction that the comic asks for, or null when it does not ask. */
    val readingMode: ReadingMode?
        get() = if (manga.equals("YesAndRightToLeft", ignoreCase = true)) ReadingMode.RightToLeft else null

    /** "Series 12: Title", "Series 12", or "Title". */
    val displayTitle: String?
        get() {
            val title = title.nonEmpty()
            val series = series.nonEmpty() ?: return title
            val numbered = listOfNotNull(series, number.nonEmpty()).joinToString(" ")
            if (title == null || title.equals(series, ignoreCase = true)) return numbered
            return "$numbered: $title"
        }

    companion object {
        /** Reads ComicInfo.xml. It returns null when the data is not XML. */
        fun parse(input: InputStream): ComicInfo? {
            val handler = Reader()
            return runCatching {
                val factory = SAXParserFactory.newInstance()
                // A file from an archive must not load another file or a URL.
                runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                factory.newSAXParser().parse(InputSource(input), handler)
                handler.info
            }.getOrNull()
        }

        fun parse(data: ByteArray): ComicInfo? = parse(data.inputStream())
    }

    private class Reader : DefaultHandler() {
        var info = ComicInfo()
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            text.setLength(0)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val value = text.toString().trim()
            text.setLength(0)
            if (value.isEmpty()) return
            val name = qName?.ifEmpty { null } ?: localName
            info = when (name) {
                "Title" -> info.copy(title = value)
                "Series" -> info.copy(series = value)
                "Number" -> info.copy(number = value)
                "Volume" -> info.copy(volume = value)
                "Writer" -> info.copy(writer = value)
                "Summary" -> info.copy(summary = value)
                "LanguageISO" -> info.copy(languageISO = value)
                "Manga" -> info.copy(manga = value)
                else -> info
            }
        }
    }
}

internal fun String?.nonEmpty(): String? = this?.trim()?.ifEmpty { null }
