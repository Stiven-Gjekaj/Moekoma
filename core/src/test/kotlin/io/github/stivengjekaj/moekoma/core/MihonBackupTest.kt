package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream

class MihonBackupTest {
    /** The wire format of Protocol Buffers, written by hand, so that the test does not read the backup with its own writer. */
    private class Message {
        private val out = ByteArrayOutputStream()
        val bytes: ByteArray get() = out.toByteArray()
        private fun varint(value: Long) {
            var rest = value
            while (rest and 0x7F.inv().toLong() != 0L) { out.write(((rest and 0x7F) or 0x80).toInt()); rest = rest ushr 7 }
            out.write(rest.toInt())
        }
        fun number(field: Int, value: Long) = apply { varint((field shl 3).toLong()); varint(value) }
        fun float(field: Int, value: Float) = apply {
            varint(((field shl 3) or 5).toLong())
            val bits = java.lang.Float.floatToIntBits(value)
            for (shift in 0 until 32 step 8) out.write((bits ushr shift) and 0xFF)
        }
        fun bytes(field: Int, value: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(value.size.toLong()); out.write(value) }
        fun text(field: Int, value: String) = bytes(field, value.toByteArray())
        fun message(field: Int, value: Message) = bytes(field, value.bytes)
    }

    private fun chapter(url: String, name: String, number: Float, order: Long, read: Boolean) =
        Message().text(1, url).text(2, name).number(4, if (read) 1 else 0).float(9, number).number(10, order)

    private fun backup(): ByteArray {
        val manga = Message().number(1, 4_242L).text(2, "/manga/tea").text(3, "Morning Tea").text(4, "Mina").text(5, "Mina")
            .text(6, "A shop at dawn.").text(7, "Slice of life").text(9, "https://a.test/c.jpg").number(13, 1_800_000_000_000)
            // The newest chapter comes first, with the lowest source order.
            .message(16, chapter("/c/3", "Ch. 3", 3f, 0, read = false))
            .message(16, chapter("/c/2", "Ch. 2", 2f, 1, read = true))
            .message(16, chapter("/c/1", "Ch. 1", 1f, 2, read = true))
            .number(17, 1).number(100, 1)
            .message(104, Message().text(1, "/c/2").number(2, 1_800_000_500_000))
        val file = Message().message(1, manga)
            .message(2, Message().text(1, "Reading").number(2, 1))
            .message(2, Message().text(1, "Later").number(2, 2))
            .message(101, Message().text(1, "Quiet Scans").number(2, 4_242L))
            .message(106, Message().text(1, "https://repo.test/index.min.json").text(2, "Repo"))
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(file.bytes) } }.toByteArray()
    }

    @Test fun readsTheSeriesTheChaptersAndTheCategories() {
        val content = MihonBackup.parse(backup())
        assertEquals(listOf("Reading", "Later"), content.categories)
        assertEquals(listOf("https://repo.test/index.min.json"), content.repositories)
        val entry = content.entries.single()
        assertEquals("4242", entry.source)
        assertEquals("Quiet Scans", entry.sourceName)
        // The chapters go from the oldest to the newest.
        assertEquals(listOf("/c/1", "/c/2", "/c/3"), entry.chapters.map { it.id })
        assertEquals(setOf(0, 1), entry.read)
        assertEquals("Mina", entry.author)
        assertEquals(listOf("Reading"), entry.categories)
    }

    @Test fun makesABookWithItsPlaceAfterTheLastChapterThatTheUserRead() {
        val entry = MihonBackup.parse(backup()).entries.single()
        val list = UUID.randomUUID()
        val reading = BookCategory(name = "Reading")
        val (book, novel) = MihonBackup.book(entry, list, "Quiet Scans", listOf(reading))
        assertEquals(BookOrigin(list, "4242", "/manga/tea"), book.origin)
        assertEquals(BookFormat.Series, book.format)
        assertEquals(2, book.page)
        assertEquals(3, book.pageCount)
        assertEquals(false, book.finished)
        assertEquals(1_800_000_500L, book.lastRead?.epochSecond)
        assertEquals(listOf(reading.id), book.categories)
        assertTrue(book.listed)
        assertEquals(listOf("c/1", "c/2", "c/3").map { "/$it" }, novel.chapters.map { it.remote })
    }

    @Test fun startsABookThatTheUserDidNotReadAtTheFirstChapter() {
        val entry = MihonBackup.parse(backup()).entries.single().copy(read = emptySet())
        val (book, _) = MihonBackup.book(entry, UUID.randomUUID(), "Quiet Scans", emptyList())
        assertEquals(0, book.page)
        assertNull(book.lastRead)
    }

    @Test fun readsTheCategoriesOfASeriesInThePackedForm() {
        // A list of numbers can come packed: one field with all the varints.
        val manga = Message().number(1, 7).text(2, "/m").text(3, "Packed").bytes(17, byteArrayOf(1, 2))
        val file = Message().message(1, manga).message(2, Message().text(1, "A").number(2, 1)).message(2, Message().text(1, "B").number(2, 2))
        assertEquals(listOf("A", "B"), MihonBackup.parse(file.bytes).entries.single().categories)
    }

    @Test fun refusesAFileThatIsNotABackup() {
        assertThrows(MihonBackup.NotABackupException::class.java) { MihonBackup.parse("hello".toByteArray()) }
    }
}
