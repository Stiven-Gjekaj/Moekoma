package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.net.URLDecoder

/** A novel that an importer wrote, and the image for its cover, when the book has one. */
data class ImportedNovel(val novel: Novel, val cover: File?)

/** The import of a book of text failed. */
sealed class NovelException(message: String) : ImportException(message) {
    /** The EPUB has no package file. */
    class NoPackage : NovelException("The book is damaged. It has no package file.")

    class NoChapters : NovelException("The book has no chapters.")

    /** The EPUB has DRM, so the text cannot be read. */
    class Encrypted : NovelException("The book has DRM. Moekoma reads only books with no DRM.")
}

/**
 * Reads an EPUB 2 or EPUB 3 into the folder of a book.
 * The importer copies every file of the EPUB into content/, and writes novel.json with the
 * chapters in the order of the spine and their titles from the table of contents.
 */
object EpubImporter {
    fun importBook(files: ComicFiles, fileName: String, folder: File): ImportedNovel {
        val content = File(folder, "content")
        try {
            content.mkdirs()
            files.forEach { path, open ->
                val safe = safePath(path) ?: return@forEach
                val file = File(content, safe)
                file.parentFile.mkdirs()
                open().use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            val (novel, cover) = readPackage(content, ComicImporter.title(fileName))
            novel.save(folder)
            return ImportedNovel(novel, cover?.let { File(content, it) }?.takeIf { it.exists() })
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
    }

    /** A path in the archive that stays in the folder, or null. */
    fun safePath(path: String): String? {
        val parts = path.split('/')
        if (parts.isEmpty() || path.startsWith("/") || ".." in parts || "" in parts) return null
        return parts.joinToString("/")
    }

    private data class Item(val href: String, val type: String, val properties: String)

    /** The novel, and the path of its cover image from content/. */
    fun readPackage(content: File, fallbackTitle: String): Pair<Novel, String?> {
        File(content, "META-INF/encryption.xml").takeIf { it.exists() }?.let { file ->
            val root = XmlElement.parse(file.readBytes())
            // Fonts can be obfuscated with no DRM. Only an encrypted chapter stops the import.
            if (root != null && root.descendants("CipherReference").any { "htm" in (it["URI"] ?: "").lowercase() }) {
                throw NovelException.Encrypted()
            }
        }
        val container = File(content, "META-INF/container.xml").takeIf { it.exists() }?.let { XmlElement.parse(it.readBytes()) }
        val opfPath = container?.descendants("rootfile")?.firstOrNull()?.get("full-path") ?: throw NovelException.NoPackage()
        val opf = File(content, opfPath).takeIf { it.exists() }?.let { XmlElement.parse(it.readBytes()) } ?: throw NovelException.NoPackage()

        val base = opfPath.substringBeforeLast('/', "")
        fun resolve(href: String): String {
            val clean = decode(href.substringBefore('#'))
            return normalize(if (base.isEmpty()) clean else "$base/$clean")
        }

        val manifest = linkedMapOf<String, Item>()
        for (item in opf.descendants("item")) {
            val id = item["id"] ?: continue
            val href = item["href"] ?: continue
            manifest[id] = Item(href, item["media-type"] ?: "", item["properties"] ?: "")
        }

        val metadata = opf.child("metadata")
        val title = metadata?.child("title")?.allText.nonEmpty() ?: fallbackTitle
        val author = metadata?.children("creator")?.map { it.allText }?.filter { it.isNotEmpty() }?.joinToString(", ").nonEmpty()
        val summary = metadata?.child("description")?.allText.nonEmpty()?.let(::stripTags)
        val language = metadata?.child("language")?.allText.nonEmpty()

        // The titles of the table of contents, by the path of their file.
        val titles = mutableMapOf<String, String>()
        val nav = manifest.values.firstOrNull { "nav" in it.properties.split(' ') }
        val navRoot = nav?.let { File(content, resolve(it.href)) }?.takeIf { it.exists() }?.let { XmlElement.parse(it.readBytes()) }
        if (nav != null && navRoot != null) {
            val navBase = resolve(nav.href).substringBeforeLast('/', "")
            val tocs = navRoot.descendants("nav").filter { "toc" in (it["epub:type"] ?: it["type"] ?: "") }
            for (link in (tocs.firstOrNull() ?: navRoot).descendants("a")) {
                val href = link["href"] ?: continue
                val text = link.allText.nonEmpty() ?: continue
                val file = href.substringBefore('#')
                val path = decode(normalize(if (navBase.isEmpty()) file else "$navBase/$file"))
                titles.putIfAbsent(path, text)
            }
        }
        val spine = opf.child("spine")
        val ncx = spine?.get("toc")?.let { manifest[it] }
        val ncxRoot = ncx?.let { File(content, resolve(it.href)) }?.takeIf { it.exists() }?.let { XmlElement.parse(it.readBytes()) }
        if (titles.isEmpty() && ncx != null && ncxRoot != null) {
            val ncxBase = resolve(ncx.href).substringBeforeLast('/', "")
            for (point in ncxRoot.descendants("navPoint")) {
                val src = point.child("content")?.get("src") ?: continue
                val text = point.child("navLabel")?.allText.nonEmpty() ?: continue
                val clean = decode(src.substringBefore('#'))
                titles.putIfAbsent(normalize(if (ncxBase.isEmpty()) clean else "$ncxBase/$clean"), text)
            }
        }

        val chapters = mutableListOf<Novel.Chapter>()
        for (itemref in spine?.children("itemref") ?: emptyList()) {
            val item = itemref["idref"]?.let { manifest[it] } ?: continue
            if ("html" !in item.type) continue
            val path = resolve(item.href)
            chapters += Novel.Chapter(title = titles[path], path = "content/$path")
        }
        if (chapters.isEmpty()) throw NovelException.NoChapters()

        // The cover: EPUB 3 marks it, EPUB 2 names it in the metadata.
        val coverId = manifest.entries.firstOrNull { "cover-image" in it.value.properties.split(' ') }?.key
            ?: metadata?.children("meta")?.firstOrNull { it["name"] == "cover" }?.get("content")
        val coverHref = coverId?.let { manifest[it] }?.takeIf { it.type.startsWith("image/") }?.href
            ?: manifest.values.filter { it.type.startsWith("image/") && "cover" in it.href.lowercase() }.map { it.href }.sorted().firstOrNull()
        val novel = Novel(
            title = title, author = author, summary = summary, language = language,
            rightToLeft = spine?.get("page-progression-direction") == "rtl", chapters = chapters,
        )
        return novel to coverHref?.let(::resolve)
    }

    /** "OEBPS/text/../images/a.png" gives "OEBPS/images/a.png". */
    fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> continue
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    internal fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}
