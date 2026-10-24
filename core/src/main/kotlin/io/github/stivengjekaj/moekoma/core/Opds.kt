package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLEncoder

/** A page of an OPDS catalog: OPDS 1.2 in Atom, or OPDS 2.0 in JSON. */
data class OpdsFeed(
    val title: String,
    /** The links to other feeds, such as "Series" or "Latest". */
    val navigation: List<OpdsLink> = emptyList(),
    /** The books of this page. */
    val publications: List<OpdsPublication> = emptyList(),
    /** The groups of an OPDS 2.0 feed, each with a title. */
    val groups: List<OpdsGroup> = emptyList(),
    val next: String? = null,
    /** The template of a search URL, with {searchTerms} or {?query}, or a link to an OpenSearch description. */
    val search: OpdsSearch? = null,
)

data class OpdsGroup(val title: String, val navigation: List<OpdsLink>, val publications: List<OpdsPublication>)

data class OpdsLink(val title: String, val url: String, val summary: String? = null)

sealed interface OpdsSearch {
    data class Template(val text: String) : OpdsSearch

    /** A link to an OpenSearch description, which holds the template. */
    data class Description(val url: String) : OpdsSearch
}

data class OpdsPublication(
    val id: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val image: String? = null,
    val acquisitions: List<Acquisition> = emptyList(),
    val stream: Stream? = null,
) {
    data class Acquisition(val url: String, val type: String)

    /** The pages of a comic one at a time, with the Page Streaming Extension. */
    data class Stream(
        /** The URL with {pageNumber}, from 0, and maybe {maxWidth}. */
        val template: String,
        val count: Int,
        /** The last page that the user read on the server, from 0. Komga and Kavita give it. */
        val lastRead: Int? = null,
    ) {
        fun url(page: Int, maxWidth: Int = 1600): String =
            template.replace("{pageNumber}", page.toString()).replace("{maxWidth}", maxWidth.toString())

        /** The page to open: the last page that the user read, or the first page. */
        val startPage: Int get() = (lastRead ?: 0).coerceIn(0, maxOf(count - 1, 0))

        /**
         * The call that keeps the progress of a Komga book: a PATCH of its read progress, with the page from 1. A stream
         * of Komga has "/books/{id}/pages/{pageNumber}" in its path. Kavita keeps the progress when it serves a page, so
         * it needs no call.
         */
        fun komgaProgress(page: Int): HttpRequest? {
            val uri = runCatching { java.net.URI(template.replace("{pageNumber}", "0").replace("{maxWidth}", "0")) }.getOrNull() ?: return null
            val path = uri.rawPath?.split('/') ?: return null
            val books = path.lastIndexOf("books")
            if (books < 0 || books + 2 >= path.size || path[books + 2] != "pages") return null
            val api = path.indexOfFirst { it == "api" || it == "opds" }
            if (api < 0 || api >= books) return null
            val target = (path.subList(0, api) + listOf("api", "v1", "books", path[books + 1], "read-progress")).joinToString("/")
            val number = page.coerceIn(0, maxOf(count - 1, 0)) + 1
            val origin = uri.scheme + "://" + uri.rawAuthority
            return HttpRequest(
                origin + target, "PATCH", mapOf("Content-Type" to "application/json"),
                "{\"page\":$number,\"completed\":${number >= count}}".toByteArray(),
            )
        }
    }

    /** The first acquisition that the app can import, with the extension of its file. */
    val download: Pair<String, String>?
        get() = acquisitions.firstNotNullOfOrNull { acquisition -> Opds.fileExtension(acquisition.type, acquisition.url)?.let { acquisition.url to it } }
}

/** The page is not an OPDS feed, such as the page of a sign-in. */
class NotAFeedException : Exception("The address does not give an OPDS feed. Check the URL of the catalog.")

/** Reads OPDS feeds, as Asabunko does. */
object Opds {
    private const val STREAM_REL = "http://vaemendis.net/opds-pse/stream"

    /** Reads a feed of either version. [base] resolves the relative links. */
    fun parse(data: ByteArray, base: String): OpdsFeed {
        val first = data.firstOrNull { b -> (b.toInt() and 0xFF) !in setOf(0x20, 0x0A, 0x0D, 0x09, 0xEF, 0xBB, 0xBF) }
        return if (first == '{'.code.toByte()) parseJson(data, base) else parseAtom(data, base)
    }

    /** The extension of the file for a MIME type, or null when the app cannot import it. */
    fun fileExtension(type: String, url: String): String? {
        val mime = type.substringBefore(';').trim().lowercase()
        return when (mime) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "application/x-cbz", "application/vnd.comicbook+zip" -> "cbz"
            "application/x-cbr", "application/vnd.comicbook-rar" -> "cbr"
            "application/x-cb7" -> "cb7"
            "application/zip", "application/x-zip-compressed" -> "cbz"
            "application/x-rar-compressed", "application/vnd.rar" -> "cbr"
            "application/x-fictionbook+xml", "text/fb2+xml" -> "fb2"
            "text/plain" -> "txt"
            // Project Gutenberg gives its Kindle files with these types, and with no extension in the link.
            "application/x-mobipocket-ebook" -> "mobi"
            "application/vnd.amazon.mobi8-ebook", "application/vnd.amazon.ebook" -> "azw3"
            else -> {
                val path = runCatching { URI(url).path }.getOrNull() ?: url
                path.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it in BookImporter.supportedExtensions }
            }
        }
    }

    /** The URL of a search, from a template with {searchTerms} (OPDS 1) or {?query} (OPDS 2). */
    fun searchUrl(template: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        var text = template.replace("{searchTerms}", encoded)
        val at = text.indexOf("{?query}")
        if (at >= 0) {
            val joiner = if ('?' in text.substring(0, at)) "&" else "?"
            text = text.substring(0, at) + joiner + "query=" + encoded + text.substring(at + "{?query}".length)
        }
        text = text.replace("{query}", encoded)
        // The other parameters of a template, such as {startPage?}, are not used.
        return text.replace(Regex("\\{[^}]*\\}"), "")
    }

    /** The template of an OpenSearch description for Atom results. */
    fun openSearchTemplate(data: ByteArray): String? {
        val root = XmlElement.parse(data) ?: return null
        val urls = root.children("Url")
        return (urls.firstOrNull { "atom" in (it["type"] ?: "") } ?: urls.firstOrNull())?.get("template")
    }

    /** Makes a link absolute as text. A template keeps its braces, which a URI does not take. */
    fun absolute(href: String, base: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val uri = URI(base)
        var origin = "${uri.scheme ?: "https"}://${uri.host ?: ""}"
        if (uri.port >= 0) origin += ":${uri.port}"
        if (href.startsWith("/")) return origin + href
        val path = uri.rawPath ?: "/"
        val directory = if (path.endsWith("/")) path else path.substringBeforeLast('/', "") + "/"
        return origin + directory + href
    }

    /** A link of the feed, made absolute. */
    private fun resolve(href: String?, base: String): String? {
        if (href.isNullOrEmpty()) return null
        // RFC 3986 keeps the path of the base for a link that is only a query. The URI of Java does not.
        if (href.startsWith("?")) return base.substringBefore('?').substringBefore('#') + href
        return runCatching { URI(base).resolve(URI(href.replace(" ", "%20"))).toString() }.getOrNull() ?: absolute(href, base)
    }

    // OPDS 1.2

    private fun parseAtom(data: ByteArray, base: String): OpdsFeed {
        val root = XmlElement.parse(data)
        if (root == null || root.name != "feed") throw NotAFeedException()
        var next: String? = null
        var search: OpdsSearch? = null
        for (link in root.children("link")) {
            val rel = link["rel"] ?: ""
            val type = link["type"] ?: ""
            if (rel == "next") next = resolve(link["href"], base)
            if (rel == "search") {
                val href = link["href"] ?: continue
                search = if ("opensearchdescription" in type) resolve(href, base)?.let { OpdsSearch.Description(it) } else OpdsSearch.Template(absolute(href, base))
            }
        }
        val navigation = mutableListOf<OpdsLink>()
        val publications = mutableListOf<OpdsPublication>()
        for (entry in root.children("entry")) {
            val title = entry.child("title")?.allText.nonEmpty() ?: "Untitled"
            val acquisitions = mutableListOf<OpdsPublication.Acquisition>()
            var image: String? = null
            var thumbnail: String? = null
            var stream: OpdsPublication.Stream? = null
            var link: String? = null
            for (item in entry.children("link")) {
                val rel = item["rel"] ?: ""
                val type = item["type"] ?: ""
                when {
                    "opds-spec.org/acquisition" in rel -> resolve(item["href"], base)?.let { acquisitions += OpdsPublication.Acquisition(it, type) }
                    rel == "http://opds-spec.org/image" -> image = resolve(item["href"], base)
                    rel == "http://opds-spec.org/image/thumbnail" -> thumbnail = resolve(item["href"], base)
                    rel == STREAM_REL && item["href"] != null ->
                        stream = OpdsPublication.Stream(
                            absolute(item["href"]!!, base), (item["count"] ?: item["pse:count"])?.toIntOrNull() ?: 0,
                            (item["lastRead"] ?: item["pse:lastRead"])?.toIntOrNull(),
                        )
                    "application/atom+xml" in type || "opds-catalog" in type -> link = link ?: resolve(item["href"], base)
                }
            }
            if (acquisitions.isEmpty() && stream == null && link != null) {
                val summary = entry.child("content")?.allText.nonEmpty() ?: entry.child("summary")?.allText.nonEmpty()
                navigation += OpdsLink(title, link, summary)
                continue
            }
            val author = entry.children("author").mapNotNull { it.child("name")?.allText.nonEmpty() }.joinToString(", ").nonEmpty()
            val summary = (entry.child("summary") ?: entry.child("content"))?.allText.nonEmpty()?.let(EpubImporter::stripTags)
            publications += OpdsPublication(
                id = entry.child("id")?.allText.nonEmpty() ?: title, title = title, author = author, summary = summary,
                image = thumbnail ?: image, acquisitions = acquisitions, stream = stream,
            )
        }
        return OpdsFeed(root.child("title")?.allText.nonEmpty() ?: "Catalog", navigation, publications, emptyList(), next, search)
    }

    // OPDS 2.0

    private fun parseJson(data: ByteArray, base: String): OpdsFeed {
        val root = runCatching { Json.parseToJsonElement(String(data, Charsets.UTF_8)) as JsonObject }.getOrNull() ?: throw NotAFeedException()
        fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
        fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
        fun navigation(value: JsonElement?) = value.objects().mapNotNull { link ->
            resolve(link["href"].text(), base)?.let { OpdsLink(link["title"].text() ?: "Untitled", it) }
        }
        fun publications(value: JsonElement?) = value.objects().map { item ->
            val metadata = item["metadata"] as? JsonObject ?: JsonObject(emptyMap())
            val title = metadata["title"].text() ?: "Untitled"
            val authors = when (val author = metadata["author"]) {
                is JsonArray -> author.toList()
                null -> emptyList()
                else -> listOf(author)
            }
            val author = authors.mapNotNull { (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.get("name").text() }.joinToString(", ").nonEmpty()
            val acquisitions = item["links"].objects().mapNotNull { link ->
                val rel = when (val value = link["rel"]) {
                    is JsonArray -> value.mapNotNull { it.text() }.joinToString(" ")
                    else -> value.text() ?: ""
                }
                if ("acquisition" in rel) resolve(link["href"].text(), base)?.let { OpdsPublication.Acquisition(it, link["type"].text() ?: "") } else null
            }
            OpdsPublication(
                id = metadata["identifier"].text() ?: title, title = title, author = author,
                summary = metadata["description"].text()?.let(EpubImporter::stripTags),
                image = item["images"].objects().firstOrNull()?.let { resolve(it["href"].text(), base) },
                acquisitions = acquisitions,
            )
        }
        val metadata = root["metadata"] as? JsonObject
        var next: String? = null
        var search: OpdsSearch? = null
        for (link in root["links"].objects()) {
            val rel = link["rel"].text() ?: ""
            if (rel == "next") next = resolve(link["href"].text(), base)
            if (rel == "search") link["href"].text()?.let { search = OpdsSearch.Template(absolute(it, base)) }
        }
        val groups = root["groups"].objects().map { group ->
            OpdsGroup((group["metadata"] as? JsonObject)?.get("title").text() ?: "", navigation(group["navigation"]), publications(group["publications"]))
        }
        return OpdsFeed(metadata?.get("title").text() ?: "Catalog", navigation(root["navigation"]), publications(root["publications"]), groups, next, search)
    }
}
