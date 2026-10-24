package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * A backup of Mihon, a `.tachibk` file: Protocol Buffers in gzip. The field numbers are the numbers of the backup of
 * Mihon (Apache License 2.0), eu.kanade.tachiyomi.data.backup.models. Moekoma reads the series, their chapters with
 * the read ones, the categories, and the repositories of extensions.
 */
@OptIn(ExperimentalSerializationApi::class)
object MihonBackup {
    @Serializable
    private class File(
        @ProtoNumber(1) val manga: List<Manga> = emptyList(),
        @ProtoNumber(2) val categories: List<Category> = emptyList(),
        @ProtoNumber(101) val sources: List<Source> = emptyList(),
        @ProtoNumber(106) val repositories: List<Repository> = emptyList(),
    )

    @Serializable
    private class Manga(
        @ProtoNumber(1) val source: Long = 0,
        @ProtoNumber(2) val url: String = "",
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(4) val artist: String? = null,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(16) val chapters: List<Chapter> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(104) val history: List<History> = emptyList(),
    )

    @Serializable
    private class Chapter(
        @ProtoNumber(1) val url: String = "",
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val scanlator: String? = null,
        @ProtoNumber(4) val read: Boolean = false,
        @ProtoNumber(5) val bookmark: Boolean = false,
        @ProtoNumber(6) val lastPageRead: Long = 0,
        @ProtoNumber(7) val dateFetch: Long = 0,
        @ProtoNumber(8) val dateUpload: Long = 0,
        @ProtoNumber(9) val chapterNumber: Float = 0f,
        @ProtoNumber(10) val sourceOrder: Long = 0,
    )

    @Serializable
    private class Category(@ProtoNumber(1) val name: String = "", @ProtoNumber(2) val order: Long = 0)

    @Serializable
    private class Source(@ProtoNumber(1) val name: String = "", @ProtoNumber(2) val sourceId: Long = 0)

    @Serializable
    private class History(@ProtoNumber(1) val url: String = "", @ProtoNumber(2) val lastRead: Long = 0)

    @Serializable
    private class Repository(@ProtoNumber(1) val baseUrl: String = "", @ProtoNumber(2) val name: String = "")

    /** One series of the backup, with its chapters from the oldest to the newest. */
    data class Entry(
        /** The ID of the Mihon source, as text, which is the ID of an installed Mihon source. */
        val source: String,
        val sourceName: String,
        val url: String,
        val title: String,
        val author: String?,
        val summary: String?,
        val genres: List<String>,
        val cover: String?,
        val chapters: List<OnlineChapter>,
        /** The chapters that the user read, by their place in [chapters]. */
        val read: Set<Int>,
        val categories: List<String>,
        val added: Instant?,
        val lastRead: Instant?,
        val favorite: Boolean,
    )

    /** The content of a backup: the series, the names of the categories, and the URLs of the repositories of extensions. */
    data class Content(val entries: List<Entry>, val categories: List<String>, val repositories: List<String>)

    class NotABackupException : Exception("The file is not a backup of Mihon.")

    fun parse(bytes: ByteArray): Content {
        val data = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).readBytes() else bytes
        val file = runCatching { ProtoBuf.decodeFromByteArray(File.serializer(), data) }.getOrElse { throw NotABackupException() }
        if (file.manga.isEmpty() && file.categories.isEmpty() && file.sources.isEmpty()) throw NotABackupException()
        val names = file.sources.associate { it.sourceId to it.name }
        val categories = file.categories.associate { it.order to it.name }
        val entries = file.manga.map { manga ->
            // A Mihon source gives the newest chapter first, with the lowest source order.
            val chapters = manga.chapters.sortedByDescending { it.sourceOrder }
            val lastRead = manga.history.maxOfOrNull { it.lastRead }?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            Entry(
                source = manga.source.toString(), sourceName = names[manga.source] ?: manga.source.toString(), url = manga.url, title = manga.title,
                author = listOfNotNull(manga.author, manga.artist).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ").ifEmpty { null },
                summary = manga.description?.trim()?.ifEmpty { null }, genres = manga.genre, cover = manga.thumbnailUrl?.ifEmpty { null },
                chapters = chapters.map { chapter ->
                    OnlineChapter(
                        id = chapter.url, title = chapter.name.trim().ifEmpty { "Chapter" },
                        number = chapter.chapterNumber.takeIf { it >= 0f }?.toDouble(),
                        date = chapter.dateUpload.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() },
                        group = chapter.scanlator?.trim()?.ifEmpty { null },
                    )
                },
                read = chapters.indices.filter { chapters[it].read }.toSet(),
                categories = manga.categories.mapNotNull { categories[it] },
                added = manga.dateAdded.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                lastRead = lastRead,
                favorite = manga.favorite,
            )
        }
        return Content(entries, file.categories.sortedBy { it.order }.map { it.name }, file.repositories.map { it.baseUrl }.filter { it.isNotEmpty() })
    }

    /**
     * The book and the chapters of an entry, for the installed source of the list [list]. The place of the book is the
     * first chapter after the last chapter that the user read.
     */
    fun book(entry: Entry, list: UUID, sourceName: String, categories: List<BookCategory>): Pair<Book, Novel> {
        val (novel, _) = Novel(title = entry.title, author = entry.author, summary = entry.summary, chapters = emptyList()).merge(entry.chapters)
        val last = entry.read.maxOrNull()
        val finished = novel.chapters.isNotEmpty() && entry.read.size == novel.chapters.size
        val page = when {
            last == null -> 0
            finished -> novel.chapters.size - 1
            else -> (last + 1).coerceAtMost(novel.chapters.size - 1)
        }
        val book = Book(
            title = entry.title, author = entry.author, summary = entry.summary, format = BookFormat.Series, fileName = sourceName,
            pageCount = novel.chapters.size, added = (entry.added ?: now()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
            lastRead = if (last != null) (entry.lastRead ?: now()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS) else null,
            page = page, position = if (finished) 1.0 else 0.0, finished = finished,
            origin = BookOrigin(list, entry.source, entry.url), listed = entry.favorite,
            categories = entry.categories.mapNotNull { name -> categories.firstOrNull { it.name == name }?.id },
        )
        return book to novel
    }
}
