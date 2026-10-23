package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The way that the reader turns the pages. The names are the names of Asabunko. */
@Serializable
enum class ReadingMode {
    /** One page at a time, from the left to the right, as in a western comic. */
    @SerialName("leftToRight") LeftToRight,

    /** One page at a time, from the right to the left, as in a manga. */
    @SerialName("rightToLeft") RightToLeft,

    /** All pages in one column, as in a webtoon. */
    @SerialName("vertical") Vertical,
}

/** The kind of file that a book comes from. */
@Serializable
enum class BookFormat {
    /** The pages are images: a CBZ, CBR, CB7, CBT, archive, or folder. */
    @SerialName("comic") Comic,

    /** The book is text in chapters: an EPUB, TXT, FB2, or HTML file. */
    @SerialName("novel") Novel,

    /** The book is a PDF, which keeps its pages as they are. */
    @SerialName("pdf") Pdf,

    /** The book is a series of an online source: chapters of pages that come from the network. */
    @SerialName("series") Series,
}

/** One book in the library. The fields are the fields of Asabunko, so that a backup moves between the two apps. */
@Serializable
data class Book(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val format: BookFormat = BookFormat.Comic,
    /** The name of the file that the user imported. */
    val fileName: String,
    val pageCount: Int,
    @Serializable(with = InstantSerializer::class) val added: Instant = now(),
    @Serializable(with = InstantSerializer::class) val lastRead: Instant? = null,
    /** The page that the user reads now, from 0. In a novel, it is the chapter. */
    val page: Int = 0,
    /** The part of the chapter of a novel that the user read, from 0 to 1. */
    val position: Double = 0.0,
    val finished: Boolean = false,
    /** The mode of this book. With null, the book uses the mode of the library. */
    val mode: ReadingMode? = null,
    /** The online source of the book, or null for a book from a file. */
    val origin: BookOrigin? = null,
    /**
     * True when the book is in the read list. A series that the user only opened is not.
     * Before the read list, the user added each book on purpose, so a book with no value is listed.
     */
    val listed: Boolean = true,
) {
    /** The part of the book that the user read, from 0 to 1. */
    val progress: Double
        get() {
            if (finished) return 1.0
            if (format == BookFormat.Novel || format == BookFormat.Series) {
                if (pageCount <= 0) return 0.0
                val chapter = page.coerceIn(0, pageCount - 1)
                return ((chapter + position.coerceIn(0.0, 1.0)) / pageCount).coerceAtMost(1.0)
            }
            if (pageCount <= 1) return 0.0
            return page.coerceIn(0, pageCount - 1).toDouble() / (pageCount - 1)
        }

    /** Keeps the page and the time. The last page marks the book as finished. */
    fun read(page: Int, at: Instant = now()): Book {
        val clamped = page.coerceIn(0, maxOf(pageCount - 1, 0))
        return copy(page = clamped, lastRead = at, finished = finished || clamped == pageCount - 1)
    }

    /** Keeps the chapter and the place in it. The end of the last chapter marks the novel as finished. */
    fun read(chapter: Int, position: Double, at: Instant = now()): Book {
        val clamped = chapter.coerceIn(0, maxOf(pageCount - 1, 0))
        val place = position.coerceIn(0.0, 1.0)
        return copy(
            page = clamped, position = place, lastRead = at,
            finished = finished || (clamped == pageCount - 1 && place >= 0.98),
        )
    }

    fun markRead(at: Instant = now()): Book = copy(finished = true, lastRead = at)

    fun markUnread(): Book = copy(finished = false, page = 0, position = 0.0, lastRead = null)
}

/** The orders of the read list. */
enum class ReadListOrder(val label: String) {
    Added("Date added"),
    LastRead("Last read"),
    Title("Title"),
    Unread("Unread first"),
}

/** All data of the library, in one JSON file. */
@Serializable
data class Library(
    /** The form of the data. A newer app can read an older form, but not the reverse. */
    val form: Int = CURRENT_FORM,
    val books: List<Book> = emptyList(),
    val defaultMode: ReadingMode = ReadingMode.RightToLeft,
    /** The source list of the user. */
    val sources: List<SourceLink> = emptyList(),
    /** The sources that the user installed from the lists. */
    val installed: List<InstalledSource> = emptyList(),
) {
    /** The book of a series or a novel of a source, when the library has it. */
    fun book(origin: BookOrigin): Book? = books.firstOrNull { it.origin == origin }

    /** The mode that the reader uses for a book. */
    fun mode(of: Book): ReadingMode = of.mode ?: defaultMode

    /** The books that the user started and did not finish, the last one first. */
    val continueReading: List<Book>
        get() = books.filter { it.lastRead != null && !it.finished }.sortedByDescending { it.lastRead }

    /** All books, the newest first. */
    val recentlyAdded: List<Book>
        get() = books.sortedByDescending { it.added }

    /** The books of the read list, the newest first. */
    val readList: List<Book>
        get() = recentlyAdded.filter { it.listed }

    /** The books of the read list in an order that the user chooses. */
    fun readList(order: ReadListOrder): List<Book> {
        val list = readList
        return when (order) {
            ReadListOrder.Added -> list
            ReadListOrder.LastRead -> list.sortedByDescending { it.lastRead ?: Instant.MIN }
            ReadListOrder.Title -> list.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }
            // The books that the user did not finish come first, the least read first.
            ReadListOrder.Unread -> list.sortedBy { if (it.finished) 2.0 else it.progress }
        }
    }

    fun book(id: UUID): Book? = books.firstOrNull { it.id == id }

    fun update(id: UUID, change: (Book) -> Book): Library =
        copy(books = books.map { if (it.id == id) change(it) else it })

    companion object {
        const val CURRENT_FORM = 1
    }
}

/** The file comes from a newer version of the app. */
class NewerFormException(val form: Int) : Exception("The library has the form $form, which is newer than ${Library.CURRENT_FORM}.")

/** Reads and writes the library file. */
object LibraryStore {
    val json = Json {
        ignoreUnknownKeys = true
        // A kind of source of a newer Asabunko becomes null, and the app finds the kind again.
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun load(file: File): Library {
        if (!file.exists()) return Library()
        val text = file.readText()
        val form = json.parseToJsonElement(text).jsonObject["form"]?.jsonPrimitive?.int ?: 0
        if (form > Library.CURRENT_FORM) throw NewerFormException(form)
        return json.decodeFromString(Library.serializer(), text)
    }

    /**
     * Copies a library file that the app cannot read next to it, as library.broken-<date>.json,
     * so that no save can write over it. It gives the copy.
     */
    fun keepBroken(file: File, at: Instant = now()): File {
        val stamp = at.toString().removeSuffix("Z").replace(':', '-')
        val copy = File(file.parentFile, "library.broken-$stamp.json")
        file.copyTo(copy, overwrite = true)
        return copy
    }

    /**
     * Writes a new file next to the old one, then puts it in the place of the old one.
     * The app writes the file after each page, so the file has no spaces. A large library writes faster.
     */
    fun save(library: Library, file: File) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".new")
        temporary.writeText(json.encodeToString(Library.serializer(), library))
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) throw java.io.IOException("The library file was not written.")
        }
    }
}

/** The time with no part of a second. The ISO 8601 dates of Swift have no part of a second either. */
fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

/** A UUID in capital letters, as Swift writes it. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString().uppercase())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** An ISO 8601 date such as 2026-10-23T09:30:00Z, as Swift writes it. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
