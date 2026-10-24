package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

/**
 * A backup of the library in one JSON file, in the form of Asabunko: the books and their places, the source list with
 * no passwords, the installed sources, and the chapters of each online book. The files of the books are not in it.
 *
 * Asabunko restores a backup only when its field `app` is "Asabunko", so the field names the form of the file, and
 * [writer] names the app that wrote it.
 */
@Serializable
data class Backup(
    val form: Int = CURRENT_FORM,
    val app: String = FORM_NAME,
    val writer: String? = null,
    @Serializable(with = InstantSerializer::class) val created: Instant = now(),
    val library: Library,
    /** The chapters of each online book, by the ID of the book. */
    @Serializable(with = ChaptersSerializer::class) val chapters: Map<UUID, Novel> = emptyMap(),
) {
    /** What a restore does on this device. */
    data class Plan(
        /** The library after the restore. */
        val library: Library,
        /** The chapters to write for each online book that comes back. */
        val chapters: Map<UUID, Novel>,
        /** The books from files whose files are not on this device. */
        val skipped: List<String>,
    )

    /**
     * The backup takes the place of the library. A book from a file stays only when [hasFiles] says that its folder is
     * on the device. An online book always comes back, with its chapters.
     */
    fun plan(hasFiles: (UUID) -> Boolean): Plan {
        val skipped = mutableListOf<String>()
        val books = library.books.filter { book ->
            when {
                book.origin != null && chapters.containsKey(book.id) -> true
                hasFiles(book.id) -> true
                else -> { skipped += book.title; false }
            }
        }
        val kept = books.map { it.id }.toSet()
        return Plan(library.copy(books = books), chapters.filterKeys { it in kept }, skipped)
    }

    fun encoded(): ByteArray = pretty.encodeToString(serializer(), this).toByteArray()

    class NotABackupException : Exception("The file is not a backup of Moekoma or Asabunko.")
    class NewerBackupException(val form: Int) : Exception("A newer version of the app made this backup (form $form).")

    companion object {
        /** The form of the backup. A newer app reads an older backup, but not the reverse. */
        const val CURRENT_FORM = 1
        const val FORM_NAME = "Asabunko"

        private val pretty = Json(LibraryStore.json) { prettyPrint = true }

        fun decode(data: ByteArray): Backup {
            val text = String(data, Charsets.UTF_8)
            val head = runCatching { LibraryStore.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw NotABackupException()
            val app = runCatching { head["app"]?.jsonPrimitive?.content }.getOrNull()
            val form = runCatching { head["form"]?.jsonPrimitive?.int }.getOrNull()
            if (app != FORM_NAME && app != "Moekoma" || form == null) throw NotABackupException()
            if (form > CURRENT_FORM) throw NewerBackupException(form)
            return runCatching { LibraryStore.json.decodeFromString(serializer(), text) }.getOrElse { throw NotABackupException() }
        }

        /** The name of the file of a backup, with the local time, so that a new backup does not replace an older one. */
        fun fileName(at: java.time.LocalDateTime = java.time.LocalDateTime.now()): String =
            "Moekoma backup %04d-%02d-%02d %02d.%02d.%02d.json".format(at.year, at.monthValue, at.dayOfMonth, at.hour, at.minute, at.second)
    }
}

/**
 * The chapters of a backup in the form of Swift. Swift writes a dictionary with UUID keys as an array of the keys and the
 * values in turn: ["6F96...", {...}, "1A2B...", {...}]. A JSON object with the keys is read too.
 */
internal object ChaptersSerializer : KSerializer<Map<UUID, Novel>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Chapters")

    override fun serialize(encoder: Encoder, value: Map<UUID, Novel>) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(JsonArray(value.flatMap { (id, novel) ->
            listOf(JsonPrimitive(id.toString().uppercase()), json.json.encodeToJsonElement(Novel.serializer(), novel))
        }))
    }

    override fun deserialize(decoder: Decoder): Map<UUID, Novel> {
        val json = decoder as JsonDecoder
        return when (val element = json.decodeJsonElement()) {
            is JsonArray -> element.chunked(2).filter { it.size == 2 }.associate { (id, novel) ->
                UUID.fromString((id as JsonPrimitive).content) to json.json.decodeFromJsonElement(Novel.serializer(), novel)
            }
            is JsonObject -> element.entries.associate { (id, novel) -> UUID.fromString(id) to json.json.decodeFromJsonElement(Novel.serializer(), novel) }
            else -> emptyMap()
        }
    }
}
