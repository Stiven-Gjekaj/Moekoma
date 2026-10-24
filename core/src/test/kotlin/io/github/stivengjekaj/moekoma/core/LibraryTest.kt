package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

class LibraryTest {
    @Test fun countsTheProgressFromTheFirstToTheLastPage() {
        var book = Book(title = "A", fileName = "a.cbz", pageCount = 5)
        assertEquals(0.0, book.progress, 0.0)
        book = book.read(page = 2)
        assertEquals(0.5, book.progress, 0.0)
        assertFalse(book.finished)
        book = book.read(page = 99)
        assertEquals(4, book.page)
        assertTrue(book.finished)
        assertEquals(1.0, book.progress, 0.0)
    }

    @Test fun givesTheStartedBooksTheLastOneFirst() {
        val old = Book(title = "Old", fileName = "o", pageCount = 10, lastRead = Instant.ofEpochSecond(100), page = 1)
        val new = Book(title = "New", fileName = "n", pageCount = 10, lastRead = Instant.ofEpochSecond(200), page = 1)
        val done = Book(title = "Done", fileName = "d", pageCount = 10, lastRead = Instant.ofEpochSecond(300), finished = true)
        val unread = Book(title = "Unread", fileName = "u", pageCount = 10)
        val library = Library(books = listOf(old, done, unread, new))
        assertEquals(listOf("New", "Old"), library.continueReading.map { it.title })
    }

    @Test fun usesTheModeOfTheLibraryWhenABookHasNone() {
        val book = Book(title = "A", fileName = "a", pageCount = 1)
        var library = Library(books = listOf(book), defaultMode = ReadingMode.Vertical)
        assertEquals(ReadingMode.Vertical, library.mode(book))
        library = library.update(book.id) { it.copy(mode = ReadingMode.LeftToRight) }
        assertEquals(ReadingMode.LeftToRight, library.mode(library.books[0]))
    }

    @Test fun writesAndReadsTheSameLibrary() {
        val file = File(temporaryFolder(), "data/library.json")
        val date = Instant.ofEpochSecond(1_800_000_000)
        val library = Library(books = listOf(Book(title = "A", fileName = "a.cbz", pageCount = 3, added = date, lastRead = date, page = 1)))
        LibraryStore.save(library, file)
        assertEquals(library, LibraryStore.load(file))
    }

    @Test fun givesAnEmptyLibraryWhenThereIsNoFile() {
        assertEquals(Library(), LibraryStore.load(File(temporaryFolder(), "library.json")))
    }

    @Test fun refusesAFileOfANewerForm() {
        val file = File(temporaryFolder(), "library.json").apply { writeText("""{"form": 99, "books": [], "defaultMode": "vertical"}""") }
        val error = assertThrows(NewerFormException::class.java) { LibraryStore.load(file) }
        assertEquals(99, error.form)
    }

    /** A book that Asabunko wrote, with the UUID and the date in the form of Swift, and fields that Moekoma does not know yet. */
    @Test fun readsABookThatAsabunkoWrote() {
        val text = """
            {"books":[{"added":"2026-10-23T09:30:00Z","fileName":"a.cbz","finished":false,"format":"comic",
            "id":"0F5B5E1C-2C4E-4D4B-9C8A-5B0E8E1F2A3B","mode":"rightToLeft","origin":null,"page":2,"pageCount":10,
            "position":0,"title":"A"}],"defaultMode":"leftToRight","form":1,"installed":[],"sources":[]}
        """.trimIndent()
        val file = File(temporaryFolder(), "library.json").apply { writeText(text) }
        val book = LibraryStore.load(file).books.single()
        assertEquals(UUID.fromString("0F5B5E1C-2C4E-4D4B-9C8A-5B0E8E1F2A3B"), book.id)
        assertEquals(Instant.parse("2026-10-23T09:30:00Z"), book.added)
        assertEquals(ReadingMode.RightToLeft, book.mode)
    }

    /** Swift reads an ISO 8601 date only with no part of a second, and a UUID in capital letters. */
    @Test fun writesTheFormsThatSwiftReads() {
        val id = UUID.fromString("0f5b5e1c-2c4e-4d4b-9c8a-5b0e8e1f2a3b")
        val book = Book(id = id, title = "A", fileName = "a", pageCount = 1, added = Instant.parse("2026-10-23T09:30:00.123456Z"))
        val file = File(temporaryFolder(), "library.json")
        LibraryStore.save(Library(books = listOf(book)), file)
        val text = file.readText()
        assertTrue(text, "\"2026-10-23T09:30:00Z\"" in text)
        assertTrue(text, "\"0F5B5E1C-2C4E-4D4B-9C8A-5B0E8E1F2A3B\"" in text)
    }

    @Test fun keepsOnlyTheListedBooksInTheReadList() {
        val old = Book(title = "Old", fileName = "o", pageCount = 1, added = Instant.ofEpochSecond(100))
        val new = Book(title = "New", fileName = "n", pageCount = 1, added = Instant.ofEpochSecond(200))
        val opened = Book(title = "Opened", fileName = "p", pageCount = 1, added = Instant.ofEpochSecond(300), listed = false)
        assertEquals(listOf("New", "Old"), Library(books = listOf(old, opened, new)).readList.map { it.title })
    }

    @Test fun putsABookOfAnOlderVersionInTheReadList() {
        val text = """{"books":[{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","title":"A","format":"comic","fileName":"a","pageCount":1,"added":"2026-01-01T00:00:00Z","page":0,"finished":false}],"defaultMode":"rightToLeft","form":1}"""
        val file = File(temporaryFolder(), "library.json").apply { writeText(text) }
        assertTrue(LibraryStore.load(file).books.single().listed)
    }

    @Test fun sortsTheReadListInEachOrder() {
        val a = Book(title = "Volume 10", fileName = "a", pageCount = 5, added = Instant.ofEpochSecond(100), lastRead = Instant.ofEpochSecond(900), page = 4, finished = true)
        val b = Book(title = "Volume 2", fileName = "b", pageCount = 5, added = Instant.ofEpochSecond(200), lastRead = Instant.ofEpochSecond(500), page = 2)
        val c = Book(title = "Another", fileName = "c", pageCount = 5, added = Instant.ofEpochSecond(300))
        val library = Library(books = listOf(a, b, c))
        assertEquals(listOf("Another", "Volume 2", "Volume 10"), library.readList(ReadListOrder.Added).map { it.title })
        assertEquals(listOf("Volume 10", "Volume 2", "Another"), library.readList(ReadListOrder.LastRead).map { it.title })
        assertEquals(listOf("Another", "Volume 2", "Volume 10"), library.readList(ReadListOrder.Title).map { it.title })
        assertEquals(listOf("Another", "Volume 2", "Volume 10"), library.readList(ReadListOrder.Unread).map { it.title })
    }

    @Test fun keepsACopyOfAFileThatDoesNotLoad() {
        val file = File(temporaryFolder(), "library.json").apply { writeText("{not json") }
        val copy = LibraryStore.keepBroken(file, Instant.parse("2026-10-23T09:30:00Z"))
        assertEquals("library.broken-2026-10-23T09-30-00.json", copy.name)
        assertEquals("{not json", copy.readText())
        assertEquals("{not json", file.readText())
    }

    /** A library of Asabunko 3.0 keeps its categories, history, marks, updates, and trackers through Moekoma. */
    @Test fun keepsTheFieldsOfAsabunkoThree() {
        val text = """
            {"form":1,"defaultMode":"rightToLeft","books":[{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","title":"Tea","format":"series","fileName":"Tea",
              "pageCount":3,"added":"2026-10-24T01:00:00Z","page":1,"finished":false,"listed":true,"autoDownload":true,
              "categories":["11111111-2222-3333-4444-555555555555"],"tracking":[{"kind":"anilist","id":7,"title":"Tea","sent":2}]}],
             "sources":[],"installed":[],
             "categories":[{"id":"11111111-2222-3333-4444-555555555555","name":"Reading"}],
             "history":[{"id":"21111111-2222-3333-4444-555555555555","book":"6F9619FF-8B86-D011-B42D-00C04FC964FF","place":1,"date":"2026-10-24T02:00:00Z"}],
             "marks":[{"id":"31111111-2222-3333-4444-555555555555","book":"6F9619FF-8B86-D011-B42D-00C04FC964FF","chapter":0,"position":0.5,"text":"Steam","kind":"highlight","created":"2026-10-24T03:00:00Z"}],
             "updates":[{"id":"41111111-2222-3333-4444-555555555555","book":"6F9619FF-8B86-D011-B42D-00C04FC964FF","chapter":2,"title":"Ch. 3","found":"2026-10-24T04:00:00Z"}]}
        """.trimIndent()
        val file = kotlin.io.path.createTempFile("library", ".json").toFile()
        file.writeText(text)
        val library = LibraryStore.load(file)
        LibraryStore.save(library, file)
        // Each value of the file of Asabunko is in the file that Moekoma writes. Moekoma can add its defaults.
        fun keeps(expected: kotlinx.serialization.json.JsonElement, actual: kotlinx.serialization.json.JsonElement?, path: String) {
            when (expected) {
                is kotlinx.serialization.json.JsonObject -> expected.forEach { (key, value) -> keeps(value, (actual as? kotlinx.serialization.json.JsonObject)?.get(key), "$path.$key") }
                is kotlinx.serialization.json.JsonArray -> expected.forEachIndexed { index, value -> keeps(value, (actual as? kotlinx.serialization.json.JsonArray)?.getOrNull(index), "$path[$index]") }
                else -> assertEquals(path, expected, actual)
            }
        }
        keeps(LibraryStore.json.parseToJsonElement(text), LibraryStore.json.parseToJsonElement(file.readText()), "library")
        assertEquals(listOf("Reading"), library.categories.map { it.name })
        assertEquals(BookMark.Kind.Highlight, library.marks.single().kind)
        assertEquals(TrackerKind.AniList, library.books.single().tracking.single().kind)
        file.delete()
    }
}
