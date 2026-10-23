package io.github.stivengjekaj.moekoma.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/** The import of a Kindle book failed. */
sealed class MobiException(message: String) : ImportException(message) {
    class NotMobi : MobiException("The file is not a MOBI or AZW3 book.")

    /** The book has DRM, so the text cannot be read. */
    class Encrypted : MobiException("The Kindle book has DRM. Moekoma reads only books with no DRM.")

    /** The book uses the HUFF/CDIC compression of the Kindle store. */
    class UnsupportedCompression(val kind: Int) : MobiException("The Kindle book uses the compression of the Kindle store, which Moekoma cannot read.")

    class NoText : MobiException("The Kindle book has no text.")
}

/**
 * Reads a MOBI, AZW, or AZW3 book with no DRM into the folder of a book.
 * A MOBI 6 book is one HTML text with page breaks. A KF8 book, such as an AZW3, keeps its text
 * as skeletons and fragments, and the importer joins them again. The importer writes each part
 * as a chapter in text/, the images in images/, the styles in styles/, and novel.json.
 * This is the importer of Asabunko.
 */
object MobiImporter {
    fun importBook(data: ByteArray, fileName: String, folder: File): ImportedNovel = guarded(folder) {
        folder.mkdirs()
        read(data, ComicImporter.title(fileName)).write(folder)
    }

    /** A book that the importer read, before it writes its files. */
    class Book(
        val title: String,
        val author: String?,
        val summary: String?,
        val language: String?,
        /** The HTML of each part, with links to images/ and styles/. */
        val parts: List<String>,
        /** The image files, by their name in images/. */
        val images: List<Pair<String, ByteArray>>,
        /** The style sheets, by their name in styles/. */
        val styles: List<Pair<String, ByteArray>>,
        val cover: String?,
    ) {
        fun write(folder: File): ImportedNovel {
            for ((directory, files) in listOf("images" to images, "styles" to styles)) {
                if (files.isEmpty()) continue
                val place = File(folder, directory).apply { mkdirs() }
                for ((name, bytes) in files) File(place, name).writeBytes(bytes)
            }
            File(folder, "text").mkdirs()
            val chapters = parts.mapIndexed { index, part ->
                val name = "text/%05d.html".format(index + 1)
                File(folder, name).writeText(part)
                Novel.Chapter(title = heading(part), path = name)
            }
            val novel = Novel(title = title, author = author, summary = summary, language = language, chapters = chapters)
            novel.save(folder)
            return ImportedNovel(novel, cover?.let { File(folder, "images/$it") })
        }
    }

    fun read(data: ByteArray, fallbackTitle: String): Book {
        val db = PalmDb(data)
        if (db.records.size <= 1) throw MobiException.NotMobi()
        val first = MobiHeader(db.record(0))
        // A joint file has a MOBI 6 book and a KF8 book. The KF8 book is newer and better.
        if (first.version >= 8) return kf8(db, first, 0, fallbackTitle)
        val boundary = first.exth.boundary
        if (boundary != null && boundary > 0 && boundary < db.records.size) {
            val book = runCatching {
                val header = MobiHeader(db.record(boundary))
                if (header.version >= 8) kf8(db, header, boundary, fallbackTitle) else null
            }.getOrNull()
            if (book != null) return book
        }
        return mobi6(db, first, fallbackTitle)
    }

    // MOBI 6

    private fun mobi6(db: PalmDb, header: MobiHeader, fallbackTitle: String): Book {
        val raw = text(db, header, 0)
        var html = decodeText(raw, header.encoding)
        val images = resources(db, header, 0)
        // <img recindex="00003"> points to the third image record.
        html = replace("""recindex\s*=\s*["']?0*(\d+)["']?""", html) { groups ->
            val image = groups[1].toIntOrNull()?.let { images.byNumber[it] } ?: return@replace ""
            "src=\"../images/$image\""
        }
        // A filepos link points to a byte of the full text. The parts are files now, so the link goes.
        html = replace("""filepos\s*=\s*["']?\d+["']?""", html) { "href=\"#\"" }
        val pieces = replace("""<mbp:pagebreak\s*/?>""", body(html)) { "\u0001" }.split("\u0001").map { it.trim() }
            .filter { strippedText(it).isNotEmpty() || "<img" in it }
        if (pieces.isEmpty()) throw MobiException.NoText()
        val parts = pieces.map { piece -> document(heading("<body>$piece"), piece) }
        return Book(
            header.exth.title ?: header.fullName ?: fallbackTitle, header.exth.author, header.exth.summary,
            header.exth.language, parts, images.files, emptyList(), images.cover,
        )
    }

    // KF8

    private fun kf8(db: PalmDb, header: MobiHeader, start: Int, fallbackTitle: String): Book {
        val raw = text(db, header, start)
        // The flows: the first is the text, the others are style sheets and images in SVG.
        var flows = listOf(raw)
        val fdst = header.fdstIndex?.let { runCatching { db.record(start + it) }.getOrNull() }
        if (fdst != null && fdst.size >= 12 && String(fdst, 0, 4, Charsets.ISO_8859_1) == "FDST") {
            val count = fdst.u32(8).toInt()
            val found = (0 until count).mapNotNull { index ->
                if (16 + index * 8 + 4 > fdst.size) return@mapNotNull null
                val from = fdst.u32(12 + index * 8).toInt()
                val to = fdst.u32(16 + index * 8).toInt()
                if (from in 0..to && to <= raw.size) raw.copyOfRange(from, to) else null
            }
            if (found.isNotEmpty()) flows = found
        }
        var pieces = listOf(flows[0])
        val skelIndex = header.skeletonIndex
        val fragIndex = header.fragmentIndex
        if (skelIndex != null && fragIndex != null) {
            val skeletons = MobiIndex.read(db, start + skelIndex).map { entry ->
                val place = entry.tags[6] ?: emptyList()
                Skeleton(entry.tags[1]?.firstOrNull() ?: 0, place.firstOrNull() ?: 0, place.getOrElse(1) { 0 })
            }
            val fragments = MobiIndex.read(db, start + fragIndex).map { entry ->
                val place = entry.tags[6] ?: emptyList()
                Fragment(entry.name.toIntOrNull() ?: 0, place.firstOrNull() ?: 0, place.getOrElse(1) { 0 })
            }
            if (skeletons.isNotEmpty()) pieces = assemble(flows[0], skeletons, fragments)
        }

        val images = resources(db, header, start)
        val styles = flows.drop(1).mapIndexed { offset, flow ->
            val index = offset + 1
            val text = String(flow, Charsets.UTF_8)
            val kind = if (text.trim().startsWith("<svg")) "svg" else "css"
            "flow%04d.%s".format(index, kind) to links(text, images, emptyList()).toByteArray()
        }
        val styleNames = styles.map { it.first }
        val svgs = styles.filter { it.first.endsWith(".svg") }.associate { it.first to String(it.second, Charsets.UTF_8) }
        val parts = pieces.map { inlineSvg(links(decodeText(it, header.encoding), images, styleNames), svgs) }
            .filter { html -> strippedText(body(html)).isNotEmpty() || "<img" in html || "<svg" in html }
        if (parts.isEmpty()) throw MobiException.NoText()
        return Book(
            header.exth.title ?: header.fullName ?: fallbackTitle, header.exth.author, header.exth.summary,
            header.exth.language, parts, images.files, styles, images.cover,
        )
    }

    data class Skeleton(val fragments: Int, val start: Int, val length: Int)

    /** [insert] is the place in the full text where the fragment goes. */
    data class Fragment(val insert: Int, val start: Int, val length: Int)

    /** Puts each fragment into its skeleton, as the KF8 format needs. Each skeleton is one part of the book. */
    fun assemble(text: ByteArray, skeletons: List<Skeleton>, fragments: List<Fragment>): List<ByteArray> {
        val parts = mutableListOf<ByteArray>()
        var next = 0
        for (skeleton in skeletons) {
            if (skeleton.start < 0 || skeleton.start + skeleton.length > text.size) continue
            var base = skeleton.start + skeleton.length
            var part = text.copyOfRange(skeleton.start, base)
            repeat(skeleton.fragments) {
                if (next >= fragments.size) return@repeat
                val fragment = fragments[next++]
                if (base + fragment.length > text.size) return@repeat
                val piece = text.copyOfRange(base, base + fragment.length)
                val at = (fragment.insert - skeleton.start).coerceIn(0, part.size)
                part = part.copyOfRange(0, at) + piece + part.copyOfRange(at, part.size)
                base += fragment.length
            }
            parts += part
        }
        return parts
    }

    /** Changes the links of KF8 to the files of the folder: kindle:embed to images/, kindle:flow to styles/. */
    fun links(html: String, images: Resources, styles: List<String>): String {
        var out = replace("""kindle:embed:([0-9A-Va-v]{4})(\?mime=[^"')\s]*)?""", html) { groups ->
            val image = groups[1].toIntOrNull(32)?.let { images.byNumber[it] } ?: return@replace ""
            "../images/$image"
        }
        out = replace("""kindle:flow:([0-9A-Va-v]{4})(\?mime=[^"')\s]*)?""", out) { groups ->
            val number = groups[1].toIntOrNull(32) ?: return@replace ""
            if (number in 1..styles.size) "../styles/${styles[number - 1]}" else ""
        }
        // A link to a place in the book points to a byte of the full text. The parts are files now, so it goes.
        return replace("""kindle:pos:fid:[0-9A-Va-v]+:off:[0-9A-Va-v]+""", out) { "#" }
    }

    /**
     * Puts an SVG flow into the page in place of an <img> that points to it. A browser loads no image
     * that an SVG in an <img> points to, and the cover page of a KF8 book is such an SVG. The SVG in the
     * page keeps its links, because text/ and styles/ are next to each other.
     */
    fun inlineSvg(html: String, svgs: Map<String, String>): String =
        replace("""<img[^>]*\ssrc\s*=\s*["']\.\./styles/(flow\d{4}\.svg)["'][^>]*>""", html) { groups ->
            val svg = svgs[groups[1]] ?: return@replace groups[0]
            svg.replace(Regex("<\\?xml[^>]*\\?>"), "").replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "").trim()
        }

    // The text records

    /** The text of the book: the text records, with no trailing entries, and decompressed. */
    fun text(db: PalmDb, header: MobiHeader, start: Int): ByteArray {
        if (header.encryption != 0) throw MobiException.Encrypted()
        val text = ByteArrayOutputStream(maxOf(header.textLength, 16))
        for (index in 1..maxOf(header.recordCount, 1)) {
            if (start + index >= db.records.size) break
            var record = db.record(start + index)
            record = record.copyOfRange(0, record.size - trailingSize(record, header.extraFlags))
            when (header.compression) {
                1 -> text.write(record)
                2 -> text.write(palmDoc(record))
                else -> throw MobiException.UnsupportedCompression(header.compression)
            }
        }
        var bytes = text.toByteArray()
        if (bytes.size > header.textLength && header.textLength > 0) bytes = bytes.copyOfRange(0, header.textLength)
        if (bytes.isEmpty()) throw MobiException.NoText()
        return bytes
    }

    /** The size of the extra bytes at the end of a text record, which the flags of the header tell. */
    fun trailingSize(record: ByteArray, flags: Int): Int {
        var size = 0
        var rest = flags shr 1
        while (rest != 0) {
            if (rest and 1 != 0) {
                // The size of each entry is a number in its last bytes, read from the end.
                var value = 0
                var shift = 0
                var at = record.size - size
                while (at > 0) {
                    val byte = record[at - 1].toInt() and 0xFF
                    value = value or ((byte and 0x7F) shl shift)
                    shift += 7
                    at--
                    if (byte and 0x80 != 0 || shift >= 28) break
                }
                size += value
            }
            rest = rest shr 1
        }
        if (flags and 1 != 0 && record.size > size) {
            size += (record[record.size - size - 1].toInt() and 0x3) + 1
        }
        return minOf(size, record.size)
    }

    /** The LZ77 compression of PalmDOC. */
    fun palmDoc(input: ByteArray): ByteArray {
        val out = ByteArray(input.size * 8 + 16)
        var size = 0
        fun put(byte: Int) {
            if (size < out.size) out[size] = byte.toByte()
            size++
        }
        var i = 0
        while (i < input.size) {
            val c = input[i].toInt() and 0xFF
            i++
            when (c) {
                in 0x01..0x08 -> {
                    val end = minOf(i + c, input.size)
                    while (i < end) put(input[i++].toInt())
                }
                in 0x80..0xBF -> {
                    if (i >= input.size) break
                    val pair = (c shl 8) or (input[i].toInt() and 0xFF)
                    i++
                    val distance = (pair shr 3) and 0x7FF
                    val length = (pair and 0x7) + 3
                    if (distance == 0 || distance > size) continue
                    repeat(length) { put(out[size - distance].toInt()) }
                }
                in 0xC0..0xFF -> {
                    put(0x20)
                    put(c xor 0x80)
                }
                else -> put(c)
            }
        }
        return out.copyOf(minOf(size, out.size))
    }

    // The images

    class Resources {
        /** The name of each image by its number, from 1. */
        val byNumber = mutableMapOf<Int, String>()
        val files = mutableListOf<Pair<String, ByteArray>>()
        var cover: String? = null
    }

    private val endMarks = setOf("FLIS", "FCIS", "SRCS", "é\u008e\r\n")

    /** The images of the book, from the first image record to the end. */
    fun resources(db: PalmDb, header: MobiHeader, start: Int): Resources {
        val result = Resources()
        val first = header.firstImage ?: return result
        if (first <= 0) return result
        var number = 1
        var index = start + first
        while (index < db.records.size) {
            val record = runCatching { db.record(index) }.getOrNull()
            val kind = record?.let(::imageKind)
            if (record != null && kind != null) {
                val name = "%05d.%s".format(number, kind)
                result.byNumber[number] = name
                result.files += name to record
            } else if (record != null && record.size >= 4 && String(record, 0, 4, Charsets.ISO_8859_1) in endMarks) {
                // The book ends with records that are not images, such as FLIS and EOF.
                break
            }
            number++
            index++
        }
        header.exth.coverOffset?.let { offset -> result.cover = result.byNumber[offset + 1] }
        return result
    }

    fun imageKind(data: ByteArray): String? {
        fun starts(vararg bytes: Int) = data.size >= bytes.size && bytes.indices.all { data[it].toInt() and 0xFF == bytes[it] }
        return when {
            starts(0xFF, 0xD8, 0xFF) -> "jpg"
            starts(0x89, 0x50, 0x4E, 0x47) -> "png"
            starts(0x47, 0x49, 0x46, 0x38) -> "gif"
            starts(0x42, 0x4D) -> "bmp"
            else -> null
        }
    }

    // Text helpers

    fun decodeText(data: ByteArray, encoding: Int): String =
        if (encoding == 1252) String(data, Charset.forName("windows-1252")) else String(data, Charsets.UTF_8)

    /** The part between <body> and </body>, or all of it. */
    fun body(html: String): String {
        var text = html
        Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(text)?.let { text = text.substring(it.range.last + 1) }
        val close = text.lastIndexOf("</body>", ignoreCase = true)
        if (close >= 0) text = text.substring(0, close)
        return text
    }

    private val headingPattern = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val titlePattern = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val paragraphPattern = Regex("""<body[^>]*>\s*(?:<div[^>]*>\s*)*<p[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * The title of the chapter of a part: its first heading, its title, or a short first paragraph.
     * A MOBI 6 book often makes its headings with a large font in a paragraph.
     */
    fun heading(html: String): String? {
        for (pattern in listOf(headingPattern, titlePattern, paragraphPattern)) {
            val match = pattern.find(html) ?: continue
            val text = strippedText(match.groupValues[1])
            if (pattern === paragraphPattern && text.length > 80) return null
            if (text.isNotEmpty()) return text.take(120)
        }
        return null
    }

    fun strippedText(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&#160;", " ")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private fun document(title: String?, body: String) = """
        |<!DOCTYPE html>
        |<html><head><meta charset="utf-8"><title>${Xhtml.escape(title ?: "")}</title></head>
        |<body>
        |$body
        |</body></html>
    """.trimMargin()

    /** Replaces each match of a pattern. The function gets the match and its groups. */
    fun replace(pattern: String, text: String, make: (List<String>) -> String): String =
        Regex(pattern, RegexOption.IGNORE_CASE).replace(text) { match -> make(match.groupValues) }
}

// The containers

internal fun ByteArray.u16(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

internal fun ByteArray.u32(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or (this[offset + 3].toLong() and 0xFF)

/** The records of a Palm database, the container of a MOBI file. */
class PalmDb(private val data: ByteArray) {
    val records: List<IntRange>

    init {
        if (data.size < 78) throw MobiException.NotMobi()
        val type = String(data, 60, 8, Charsets.ISO_8859_1)
        if (type != "BOOKMOBI" && type != "TEXtREAd") throw MobiException.NotMobi()
        val count = data.u16(76)
        if (data.size < 78 + count * 8) throw MobiException.NotMobi()
        val offsets = List(count) { data.u32(78 + it * 8).toInt() }
        records = offsets.mapIndexed { index, offset ->
            val end = if (index + 1 < count) offsets[index + 1] else data.size
            if (offset < 0 || offset > end || end > data.size) throw MobiException.NotMobi()
            offset until end
        }
    }

    fun record(index: Int): ByteArray {
        if (index !in records.indices) throw MobiException.NotMobi()
        val range = records[index]
        return data.copyOfRange(range.first, range.last + 1)
    }
}

/** The facts of record 0: the PalmDOC header, the MOBI header, and the EXTH header. */
class MobiHeader(record: ByteArray) {
    val compression: Int
    val textLength: Int
    val recordCount: Int
    val encryption: Int
    var encoding = 1252
        private set
    var version = 0
        private set
    var fullName: String? = null
        private set
    var firstImage: Int? = null
        private set
    var extraFlags = 0
        private set
    var fdstIndex: Int? = null
        private set
    var fragmentIndex: Int? = null
        private set
    var skeletonIndex: Int? = null
        private set
    var exth = Exth()
        private set

    data class Exth(
        val author: String? = null,
        val summary: String? = null,
        val title: String? = null,
        val language: String? = null,
        val coverOffset: Int? = null,
        val boundary: Int? = null,
    )

    init {
        if (record.size < 16) throw MobiException.NotMobi()
        compression = record.u16(0)
        textLength = record.u32(4).toInt()
        recordCount = record.u16(8)
        encryption = record.u16(12)
        // A plain PalmDOC book has no MOBI header.
        if (record.size >= 24 && String(record, 16, 4, Charsets.ISO_8859_1) == "MOBI") {
            val length = record.u32(20).toInt()
            fun field(offset: Int): Int? {
                if (offset + 4 > 16 + length || offset + 4 > record.size) return null
                val value = record.u32(offset)
                return if (value == 0xFFFF_FFFFL) null else value.toInt()
            }
            encoding = field(28) ?: 1252
            version = field(36) ?: 0
            val nameOffset = field(84)
            val nameSize = field(88)
            if (nameOffset != null && nameSize != null && nameOffset >= 0 && nameSize >= 0 && nameOffset + nameSize <= record.size) {
                fullName = String(record, nameOffset, nameSize, Charsets.UTF_8).nonEmpty()
            }
            firstImage = field(108)
            if (length >= 0xE4 && 16 + 0xE4 <= record.size) extraFlags = record.u16(0xF2)
            if (version >= 8) {
                fdstIndex = field(0xC0)
                fragmentIndex = field(0xF8)
                skeletonIndex = field(0xFC)
            }
            val flags = field(0x80)
            if (flags != null && flags and 0x40 != 0) exth = readExth(record, 16 + length, encoding)
        }
    }

    private fun readExth(record: ByteArray, start: Int, encoding: Int): Exth {
        var exth = Exth()
        if (start < 0 || start + 12 > record.size || String(record, start, 4, Charsets.ISO_8859_1) != "EXTH") return exth
        val count = record.u32(start + 8).toInt()
        var at = start + 12
        fun text(value: ByteArray) = MobiImporter.decodeText(value, encoding).trim().ifEmpty { null }
        repeat(count) {
            if (at + 8 > record.size) return exth
            val type = record.u32(at).toInt()
            val size = record.u32(at + 4).toInt()
            if (size < 8 || at + size > record.size) return exth
            val value = record.copyOfRange(at + 8, at + size)
            exth = when {
                type == 100 -> exth.copy(author = listOfNotNull(exth.author, text(value)).joinToString(", ").ifEmpty { null })
                type == 103 -> exth.copy(summary = text(value)?.let(EpubImporter::stripTags))
                type == 503 -> exth.copy(title = text(value))
                type == 524 -> exth.copy(language = text(value))
                type == 201 && value.size >= 4 -> exth.copy(coverOffset = value.u32(0).toInt())
                type == 121 && value.size >= 4 -> exth.copy(boundary = value.u32(0).toInt())
                else -> exth
            }
            at += size
        }
        return exth
    }
}

/** An INDX index of KF8, such as the skeletons or the fragments. */
object MobiIndex {
    data class Entry(val name: String, val tags: Map<Int, List<Int>>)

    data class Tag(val tag: Int, val valuesPerEntry: Int, val mask: Int, val end: Boolean)

    /** The entries of the index that starts at a record. */
    fun read(db: PalmDb, index: Int): List<Entry> {
        val head = db.record(index)
        if (head.size < 28 || String(head, 0, 4, Charsets.ISO_8859_1) != "INDX") return emptyList()
        val length = head.u32(4).toInt()
        val records = head.u32(24).toInt()
        if (length + 12 > head.size || String(head, length, 4, Charsets.ISO_8859_1) != "TAGX") return emptyList()
        val tagxLength = head.u32(length + 4).toInt()
        val controlBytes = head.u32(length + 8).toInt()
        val tags = mutableListOf<Tag>()
        var at = length + 12
        while (at + 4 <= length + tagxLength && at + 4 <= head.size) {
            tags += Tag(head[at].toInt() and 0xFF, head[at + 1].toInt() and 0xFF, head[at + 2].toInt() and 0xFF, head[at + 3].toInt() == 1)
            at += 4
        }
        val entries = mutableListOf<Entry>()
        for (number in 1..maxOf(records, 1)) {
            if (index + number >= db.records.size) break
            entries += readRecord(db.record(index + number), tags, controlBytes)
        }
        return entries
    }

    fun readRecord(record: ByteArray, tags: List<Tag>, controlBytes: Int): List<Entry> {
        if (record.size < 28 || String(record, 0, 4, Charsets.ISO_8859_1) != "INDX") return emptyList()
        val idxt = record.u32(20).toInt()
        val count = record.u32(24).toInt()
        if (idxt + 4 + count * 2 > record.size) return emptyList()
        val offsets = List(count) { record.u16(idxt + 4 + it * 2) }
        val entries = mutableListOf<Entry>()
        for ((number, offset) in offsets.withIndex()) {
            val end = if (number + 1 < count) offsets[number + 1] else idxt
            if (offset >= end || end > record.size) continue
            val nameLength = record[offset].toInt() and 0xFF
            if (offset + 1 + nameLength > end) continue
            val name = String(record, offset + 1, nameLength, Charsets.UTF_8)
            val start = offset + 1 + nameLength
            if (start + controlBytes > end) continue
            val control = record.copyOfRange(start, start + controlBytes).map { it.toInt() and 0xFF }
            val data = record.copyOfRange(start + controlBytes, end).map { it.toInt() and 0xFF }
            entries += Entry(name, values(data, tags, control))
        }
        return entries
    }

    private data class Step(val tag: Int, val count: Int?, val bytes: Int?, val perEntry: Int)

    /** The values of the tags of one entry, from its control bytes and its numbers. */
    fun values(data: List<Int>, tags: List<Tag>, control: List<Int>): Map<Int, List<Int>> {
        val plan = mutableListOf<Step>()
        var byte = 0
        var at = 0
        for (tag in tags) {
            if (tag.end) {
                byte++
                continue
            }
            if (byte >= control.size) break
            var value = control[byte] and tag.mask
            if (value == 0) continue
            if (value == tag.mask) {
                if (Integer.bitCount(tag.mask) > 1) {
                    val (size, read) = varint(data, at)
                    at += read
                    plan += Step(tag.tag, null, size, tag.valuesPerEntry)
                } else {
                    plan += Step(tag.tag, 1, null, tag.valuesPerEntry)
                }
            } else {
                var mask = tag.mask
                while (mask and 1 == 0) {
                    mask = mask shr 1
                    value = value shr 1
                }
                plan += Step(tag.tag, value, null, tag.valuesPerEntry)
            }
        }
        val result = mutableMapOf<Int, List<Int>>()
        for (step in plan) {
            val values = mutableListOf<Int>()
            if (step.count != null) {
                repeat(step.count * step.perEntry) {
                    if (at < data.size) {
                        val (value, read) = varint(data, at)
                        at += read
                        values += value
                    }
                }
            } else if (step.bytes != null) {
                var used = 0
                while (used < step.bytes && at < data.size) {
                    val (value, read) = varint(data, at)
                    at += read
                    used += read
                    values += value
                }
            }
            result[step.tag] = values
        }
        return result
    }

    /** A number in 7-bit bytes. The last byte has the high bit. */
    fun varint(data: List<Int>, start: Int): Pair<Int, Int> {
        var value = 0
        var at = start
        while (at < data.size) {
            val byte = data[at]
            value = (value shl 7) or (byte and 0x7F)
            at++
            if (byte and 0x80 != 0) break
        }
        return value to (at - start)
    }
}
