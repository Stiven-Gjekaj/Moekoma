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
}
