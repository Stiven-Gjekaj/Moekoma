package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/** The backup in the form of Asabunko, as in the tests of Asabunko. */
class BackupTest {
    private val origin = BookOrigin(UUID.randomUUID(), "quiet", "novel/dawn")
    /** A date in whole seconds, because a backup writes the dates in ISO 8601 with no fractions. */
    private val day = Instant.ofEpochSecond(1_800_000_000)

    private fun library() = Library(
        books = listOf(
            Book(title = "From a file", fileName = "a.cbz", pageCount = 10, added = day, page = 4),
            Book(title = "Lost file", fileName = "b.epub", pageCount = 3, added = day),
            Book(title = "Dawn Tea", format = BookFormat.Novel, fileName = "Quiet", pageCount = 2, added = day, page = 1, position = 0.5, origin = origin),
        ),
        defaultMode = ReadingMode.Vertical,
        sources = listOf(SourceLink(url = "https://x.test/plugins.min.json", username = "asa", kind = SourceKind.LnReader)),
        installed = listOf(InstalledSource(origin.list, SourceInfo("quiet", "Quiet", "English", "1", code = "https://x.test/q.js", novels = true))),
    )

    @Test fun writesAndReadsTheSameBackup() {
        val library = library()
        val novel = Novel("Dawn Tea", chapters = listOf(Novel.Chapter("One", "chapters/00001.html", "c/1")))
        val backup = Backup(library = library, chapters = mapOf(library.books[2].id to novel), writer = "Moekoma 1.0.0", created = day)
        assertEquals(backup, Backup.decode(backup.encoded()))
    }

    @Test fun writesTheChaptersInTheFormOfSwift() {
        val library = library()
        val id = library.books[2].id
        val text = String(Backup(library = library, chapters = mapOf(id to Novel("Dawn Tea", chapters = emptyList()))).encoded())
        val chapters = LibraryStore.json.parseToJsonElement(text).let { (it as kotlinx.serialization.json.JsonObject)["chapters"] as kotlinx.serialization.json.JsonArray }
        // Swift writes a dictionary with UUID keys as the keys and the values in turn.
        assertEquals(id.toString().uppercase(), (chapters[0] as kotlinx.serialization.json.JsonPrimitive).content)
        assertTrue(chapters[1] is kotlinx.serialization.json.JsonObject)
        assertTrue(text, "\"app\": \"Asabunko\"" in text)
    }

    @Test fun readsABackupThatAsabunkoWrote() {
        val text = """
            {"app":"Asabunko","chapters":["6F9619FF-8B86-D011-B42D-00C04FC964FF",{"title":"Dawn Tea","chapters":[{"title":"One","path":"chapters/00001.html","remote":"c/1"}]}],
             "created":"2027-01-15T08:00:00Z","form":1,
             "library":{"form":1,"defaultMode":"vertical","books":[{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","title":"Dawn Tea","format":"novel","fileName":"Quiet",
               "pageCount":1,"added":"2027-01-15T08:00:00Z","page":0,"finished":false,"origin":{"list":"11111111-2222-3333-4444-555555555555","source":"quiet","series":"novel/dawn"}}],
               "sources":[],"installed":[]}}
        """.trimIndent()
        val backup = Backup.decode(text.toByteArray())
        assertEquals(listOf("One"), backup.chapters.values.single().chapters.map { it.title })
        assertEquals("Dawn Tea", backup.library.books.single().title)
    }

    @Test fun bringsBackTheOnlineBooksAndTheBooksWithTheirFiles() {
        val library = library()
        val backup = Backup(library = library, chapters = mapOf(library.books[2].id to Novel("Dawn Tea", chapters = emptyList())))
        val plan = backup.plan { it == library.books[0].id }
        assertEquals(listOf("From a file", "Dawn Tea"), plan.library.books.map { it.title })
        assertEquals(0.5, plan.library.books[1].position, 0.0)
        assertEquals(setOf(library.books[2].id), plan.chapters.keys)
        assertEquals(listOf("Lost file"), plan.skipped)
        assertEquals(library.sources, plan.library.sources)
        assertEquals(library.installed, plan.library.installed)
    }

    @Test fun refusesAFileThatIsNotABackupAndABackupOfANewerApp() {
        assertThrows(Backup.NotABackupException::class.java) { Backup.decode("""{"form": 1, "books": []}""".toByteArray()) }
        assertThrows(Backup.NotABackupException::class.java) { Backup.decode("not json".toByteArray()) }
        val newer = assertThrows(Backup.NewerBackupException::class.java) { Backup.decode("""{"form": 9, "app": "Asabunko"}""".toByteArray()) }
        assertEquals(9, newer.form)
    }

    @Test fun namesTheFileWithTheLocalTime() {
        assertEquals("Moekoma backup 2027-01-05 09.07.03.json", Backup.fileName(LocalDateTime.of(2027, 1, 5, 9, 7, 3)))
    }
}
