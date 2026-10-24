package io.github.stivengjekaj.moekoma.core

/**
 * The data of the Aidoku sources, in the order of the fields of aidoku-rs (MIT License),
 * crates/lib/src/structs/mod.rs. Postcard has no names, so the order of the fields is the format. As in Asabunko.
 */
object Aidoku {
    data class Manga(
        val key: String,
        val title: String,
        val cover: String? = null,
        val artists: List<String>? = null,
        val authors: List<String>? = null,
        val description: String? = null,
        val url: String? = null,
        val tags: List<String>? = null,
        val status: Long = 0,
        val contentRating: Long = 0,
        val viewer: Long = 0,
        val updateStrategy: Long = 0,
        val nextUpdateTime: Long? = null,
        val chapters: List<Chapter>? = null,
    ) {
        fun write(w: PostcardWriter) = with(w) {
            string(key)
            string(title)
            option(cover) { string(it) }
            option(artists) { list(it) { s -> string(s) } }
            option(authors) { list(it) { s -> string(s) } }
            option(description) { string(it) }
            option(url) { string(it) }
            option(tags) { list(it) { s -> string(s) } }
            varint(status)
            varint(contentRating)
            varint(viewer)
            varint(updateStrategy)
            option(nextUpdateTime) { signed(it) }
            option(chapters) { list(it) { c -> c.write(this) } }
        }

        /** The status in words, from the variants of MangaStatus. */
        val statusName: String? get() = listOf("Ongoing", "Completed", "Cancelled", "Hiatus").getOrNull(status.toInt() - 1)

        companion object {
            fun read(r: PostcardReader) = with(r) {
                Manga(
                    key = string(), title = string(), cover = option { string() },
                    artists = option { list { string() } }, authors = option { list { string() } },
                    description = option { string() }, url = option { string() }, tags = option { list { string() } },
                    status = varint(), contentRating = varint(), viewer = varint(), updateStrategy = varint(),
                    nextUpdateTime = option { signed() }, chapters = option { list { Chapter.read(this) } },
                )
            }
        }
    }

    data class Chapter(
        val key: String,
        val title: String? = null,
        val chapterNumber: Float? = null,
        val volumeNumber: Float? = null,
        val dateUploaded: Long? = null,
        val scanlators: List<String>? = null,
        val url: String? = null,
        val language: String? = null,
        val thumbnail: String? = null,
        val locked: Boolean = false,
    ) {
        fun write(w: PostcardWriter) = with(w) {
            string(key)
            option(title) { string(it) }
            option(chapterNumber) { f32(it) }
            option(volumeNumber) { f32(it) }
            option(dateUploaded) { signed(it) }
            option(scanlators) { list(it) { s -> string(s) } }
            option(url) { string(it) }
            option(language) { string(it) }
            option(thumbnail) { string(it) }
            bool(locked)
        }

        /** "Vol. 2 Ch. 12: The Rain", "Ch. 12", or the title of the source. */
        val displayTitle: String get() {
            fun number(value: Float) = if (value == Math.round(value).toFloat()) Math.round(value).toString() else value.toString()
            val head = listOfNotNull(volumeNumber?.let { "Vol. ${number(it)}" }, chapterNumber?.let { "Ch. ${number(it)}" }).joinToString(" ")
            val name = title?.ifEmpty { null } ?: return head.ifEmpty { key }
            return if (head.isEmpty()) name else "$head: $name"
        }

        companion object {
            fun read(r: PostcardReader) = with(r) {
                Chapter(
                    key = string(), title = option { string() }, chapterNumber = option { f32() }, volumeNumber = option { f32() },
                    dateUploaded = option { signed() }, scanlators = option { list { string() } }, url = option { string() },
                    language = option { string() }, thumbnail = option { string() }, locked = bool(),
                )
            }
        }
    }

    data class MangaPageResult(val entries: List<Manga>, val hasNextPage: Boolean) {
        companion object {
            fun read(r: PostcardReader) = MangaPageResult(r.list { Manga.read(this) }, r.bool())
        }
    }

    data class Listing(val id: String, val name: String, val kind: Long = 0) {
        fun write(w: PostcardWriter) = with(w) {
            string(id)
            string(name)
            varint(kind)
        }

        companion object {
            fun read(r: PostcardReader) = Listing(r.string(), r.string(), r.varint())
        }
    }

    sealed interface PageContent {
        data class Url(val url: String, val context: Map<String, String>?) : PageContent
        data class Text(val text: String) : PageContent
        data class Image(val rid: Int) : PageContent
        data class Zip(val url: String, val path: String) : PageContent
    }

    data class Page(val content: PageContent, val thumbnail: String?, val hasDescription: Boolean, val description: String?) {
        companion object {
            fun read(r: PostcardReader): Page {
                val content = when (val tag = r.varint()) {
                    0L -> PageContent.Url(r.string(), r.option { readMap(this) })
                    1L -> PageContent.Text(r.string())
                    2L -> PageContent.Image(r.signed().toInt())
                    3L -> PageContent.Zip(r.string(), r.string())
                    else -> throw PostcardException("The page kind $tag is not known.")
                }
                return Page(content, r.option { string() }, r.bool(), r.option { string() })
            }
        }
    }

    fun readMap(r: PostcardReader): Map<String, String> = buildMap { repeat(r.unsigned()) { put(r.string(), r.string()) } }

    fun writeMap(map: Map<String, String>, w: PostcardWriter) {
        w.unsigned(map.size)
        for ((key, value) in map.toSortedMap()) {
            w.string(key)
            w.string(value)
        }
    }
}
