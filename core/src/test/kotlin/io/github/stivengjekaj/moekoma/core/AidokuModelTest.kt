package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The byte examples of the layouts of aidoku-rs. */
class AidokuModelTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test fun writesAChapterAsTheRustCrate() {
        val data = PostcardWriter.write { Aidoku.Chapter("k", chapterNumber = 1.5f, dateUploaded = 1_751_374_800).write(this) }
        assertArrayEquals(bytes(0x01, 0x6B, 0x00, 0x01, 0x00, 0x00, 0xC0, 0x3F, 0x00, 0x01, 0xA0, 0xEF, 0x9E, 0x86, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x00), data)
    }

    @Test fun readsAPageResult() {
        val result = Aidoku.MangaPageResult.read(PostcardReader(bytes(0x01, 0x01, 0x31, 0x01, 0x41, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01)))
        assertEquals(listOf(Aidoku.Manga("1", "A")), result.entries)
        assertTrue(result.hasNextPage)
    }

    @Test fun readsAPageWithAContext() {
        val page = Aidoku.Page.read(PostcardReader(bytes(0x00, 0x01, 0x75, 0x01, 0x01, 0x01, 0x61, 0x01, 0x62, 0x00, 0x00, 0x00)))
        assertEquals(Aidoku.Page(Aidoku.PageContent.Url("u", mapOf("a" to "b")), null, false, null), page)
    }

    @Test fun writesAListing() {
        assertArrayEquals(bytes(0x01, 0x69, 0x01, 0x6E, 0x01), PostcardWriter.write { Aidoku.Listing("i", "n", 1).write(this) })
    }

    @Test fun namesAChapter() {
        assertEquals("Vol. 2 Ch. 12: Rain", Aidoku.Chapter("a", title = "Rain", chapterNumber = 12f, volumeNumber = 2f).displayTitle)
        assertEquals("Ch. 12.5", Aidoku.Chapter("a", chapterNumber = 12.5f).displayTitle)
        assertEquals("a", Aidoku.Chapter("a").displayTitle)
    }

    @Test fun readsTheNumbersThatItWrites() {
        val data = PostcardWriter.write {
            signed(-300L)
            varint(Long.MAX_VALUE)
            f64(2.5)
            option<String>(null) { string(it) }
        }
        val r = PostcardReader(data)
        assertEquals(-300L, r.signed())
        assertEquals(Long.MAX_VALUE, r.varint())
        assertEquals(2.5, r.f64(), 0.0)
        assertEquals(null, r.option { string() })
        assertTrue(r.isAtEnd)
    }
}
