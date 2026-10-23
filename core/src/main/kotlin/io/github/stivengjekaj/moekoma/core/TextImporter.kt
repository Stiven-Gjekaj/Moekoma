package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.util.Base64

/** Reads a TXT or an HTML file into the folder of a book as a novel. */
object TextImporter {
    /** A long text with no headings gets parts of about this many characters. */
    internal const val PART_LENGTH = 60_000

    private val heading = Regex(
        """^\s*((chapter|chap\.|part|volume|book|prologue|epilogue|interlude|afterword|side story)\b[^\n]{0,80}|第[0-9０-９〇零一二三四五六七八九十百千两]+[章話话节節回卷部].{0,40})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun importText(data: ByteArray, fileName: String, folder: File): ImportedNovel =
        guarded(folder) { ImportedNovel(write(split(TextDecoding.decode(data)), ComicImporter.title(fileName), folder = folder), null) }

    fun importHtml(data: ByteArray, fileName: String, folder: File): ImportedNovel = guarded(folder) {
        folder.mkdirs()
        File(folder, "chapter.html").writeBytes(data)
        val html = TextDecoding.decode(data)
        val found = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(html)
            ?.value?.replace(Regex("<[^>]+>"), "")?.nonEmpty()
        val novel = Novel(title = found ?: ComicImporter.title(fileName), chapters = listOf(Novel.Chapter(path = "chapter.html")))
        novel.save(folder)
        ImportedNovel(novel, null)
    }

    /**
     * The chapters of a text: a line such as "Chapter 12", "Prologue", or "第十二章" starts a chapter.
     * With fewer than two headings, a long text gets parts at the ends of paragraphs.
     */
    fun split(text: String): List<Pair<String?, String>> {
        val normal = text.replace("\r\n", "\n").replace('\r', '\n')
        val chapters = mutableListOf<Pair<String?, MutableList<String>>>(null to mutableListOf())
        var headings = 0
        for (line in normal.split("\n")) {
            if (line.length <= 90 && heading.matches(line)) {
                headings++
                chapters += line.trim() to mutableListOf()
            } else {
                chapters.last().second += line
            }
        }
        if (headings >= 2) {
            val result = chapters.map { it.first to it.second.joinToString("\n") }.toMutableList()
            // The text before the first heading is a chapter only when it has text.
            if (result.first().second.isBlank()) result.removeAt(0)
            return result
        }
        if (normal.length <= PART_LENGTH) return listOf(null to normal)
        val parts = mutableListOf<Pair<String?, String>>()
        val current = StringBuilder()
        for (paragraph in normal.split("\n")) {
            current.append(paragraph).append('\n')
            if (current.length >= PART_LENGTH) {
                parts += "Part ${parts.size + 1}" to current.toString()
                current.setLength(0)
            }
        }
        if (current.isNotBlank()) parts += "Part ${parts.size + 1}" to current.toString()
        return parts
    }

    internal fun write(chapters: List<Pair<String?, String>>, title: String, author: String? = null, summary: String? = null, language: String? = null, folder: File): Novel {
        val dir = File(folder, "chapters").apply { mkdirs() }
        val list = chapters.mapIndexed { index, (chapterTitle, text) ->
            val name = "%04d.xhtml".format(index + 1)
            val head = chapterTitle?.let { "<h2>${Xhtml.escape(it)}</h2>\n" } ?: ""
            File(dir, name).writeText(Xhtml.document(chapterTitle, head + Xhtml.paragraphs(text), language))
            Novel.Chapter(title = chapterTitle, path = "chapters/$name")
        }
        val novel = Novel(title = title, author = author, summary = summary, language = language, chapters = list)
        novel.save(folder)
        return novel
    }
}

/** Reads a FictionBook 2 file, the common format of Russian e-books. */
object Fb2Importer {
    fun importBook(data: ByteArray, fileName: String, folder: File): ImportedNovel = guarded(folder) {
        val root = XmlElement.parse(data) ?: throw NovelException.NoPackage()
        File(folder, "chapters/images").mkdirs()

        // The images are in base64, in <binary> elements at the end of the file.
        val images = mutableMapOf<String, String>()
        root.children("binary").forEachIndexed { index, binary ->
            val id = binary["id"] ?: return@forEachIndexed
            val bytes = runCatching { Base64.getMimeDecoder().decode(binary.text) }.getOrNull() ?: return@forEachIndexed
            val ext = if ("png" in (binary["content-type"] ?: "image/jpeg")) "png" else "jpg"
            val name = "images/$index.$ext"
            File(folder, "chapters/$name").writeBytes(bytes)
            images[id] = name
        }

        val info = root.child("description")?.child("title-info")
        val title = info?.child("book-title")?.allText.nonEmpty() ?: ComicImporter.title(fileName)
        val author = info?.children("author")?.map { author ->
            listOf("first-name", "middle-name", "last-name").mapNotNull { author.child(it)?.allText.nonEmpty() }.joinToString(" ")
        }?.filter { it.isNotEmpty() }?.joinToString(", ").nonEmpty()
        val summary = info?.child("annotation")?.allText.nonEmpty()
        val language = info?.child("lang")?.allText.nonEmpty()

        fun href(element: XmlElement): String? {
            val link = element["href"] ?: element["l:href"] ?: element["xlink:href"] ?: return null
            return images[link.removePrefix("#")]
        }

        fun render(element: XmlElement): String = when (element.name) {
            "p", "v" -> "<p>${Xhtml.escape(element.allText)}</p>"
            "subtitle" -> "<h3>${Xhtml.escape(element.allText)}</h3>"
            "empty-line" -> "<br/>"
            "image" -> href(element)?.let { "<p><img src=\"$it\" alt=\"\"/></p>" } ?: ""
            "poem", "stanza", "cite", "epigraph" -> "<blockquote>" + element.children.joinToString("\n") { render(it) } + "</blockquote>"
            "title", "section" -> ""
            else -> element.children.joinToString("\n") { render(it) }
        }

        // Each section with text is a chapter. A section that only holds sections gives its title to the first of them.
        val chapters = mutableListOf<Pair<String?, String>>()
        fun walk(section: XmlElement, inherited: String?) {
            val own = section.child("title")?.allText.nonEmpty()
            val body = section.children.filter { it.name != "section" }.joinToString("\n") { render(it) }
            val subsections = section.children("section")
            val joined = listOfNotNull(inherited, own).joinToString(": ").nonEmpty()
            if (body.isNotBlank() || subsections.isEmpty()) {
                chapters += joined to ((own?.let { "<h2>${Xhtml.escape(it)}</h2>\n" } ?: "") + body)
                subsections.forEach { walk(it, null) }
            } else {
                subsections.forEachIndexed { index, sub -> walk(sub, if (index == 0) joined else null) }
            }
        }
        for (body in root.children("body")) {
            if (body["name"] == "notes") continue
            body.children("section").forEach { walk(it, null) }
        }
        if (chapters.isEmpty()) throw NovelException.NoChapters()

        val list = chapters.mapIndexed { index, (chapterTitle, body) ->
            val name = "%04d.xhtml".format(index + 1)
            File(folder, "chapters/$name").writeText(Xhtml.document(chapterTitle, body, language))
            Novel.Chapter(title = chapterTitle, path = "chapters/$name")
        }
        val cover = info?.child("coverpage")?.child("image")?.let(::href)?.let { File(folder, "chapters/$it") }
        val novel = Novel(title = title, author = author, summary = summary, language = language, chapters = list)
        novel.save(folder)
        ImportedNovel(novel, cover)
    }
}

/** On a fault, the folder of the book does not stay. */
internal inline fun <T> guarded(folder: File, body: () -> T): T = try {
    body()
} catch (e: Exception) {
    folder.deleteRecursively()
    throw e
}
