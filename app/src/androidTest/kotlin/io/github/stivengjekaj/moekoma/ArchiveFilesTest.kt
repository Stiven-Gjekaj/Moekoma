package io.github.stivengjekaj.moekoma

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.ComicImporter
import io.github.stivengjekaj.moekoma.data.ArchiveFiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** libarchive runs only on the device, so these tests run on the emulator. */
@RunWith(AndroidJUnit4::class)
class ArchiveFilesTest {
    private val work = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test-${UUID.randomUUID()}").apply { mkdirs() }

    private fun files(file: File) = ArchiveFiles { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }

    private fun read(file: File): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        files(file).forEach { path, open -> result[path] = open().use { it.readBytes() } }
        return result
    }

    @Test fun readsEachKindOfArchive() {
        for (kind in TestArchive.entries) {
            val archive = File(work, "book-$kind")
            writeArchive(kind, archive, listOf("b.png" to png(2), "a.png" to png(1)))
            val entries = read(archive)
            assertEquals(kind.name, setOf("a.png", "b.png"), entries.keys)
            assertArrayEquals(kind.name, png(1), entries["a.png"])
        }
    }

    @Test fun skipsTheDataOfAnEntryThatTheAppDoesNotRead() {
        val archive = File(work, "skip.cb7")
        writeArchive(TestArchive.SevenZip, archive, listOf("notes.txt" to ByteArray(200_000) { 7 }, "p1.png" to png(1)))
        val paths = mutableListOf<String>()
        var page: ByteArray? = null
        files(archive).forEach { path, open ->
            paths += path
            if (path == "p1.png") page = open().use { it.readBytes() }
        }
        assertEquals(listOf("notes.txt", "p1.png"), paths)
        assertArrayEquals(png(1), page)
    }

    @Test fun importsAComicAndKeepsAPathOutOfTheFolder() {
        val archive = File(work, "My_Comic.cbz")
        writeArchive(TestArchive.Zip, archive, listOf("p10.png" to png(10), "../../escape.png" to png(99), "p2.png" to png(2)))
        val book = File(work, "book")
        val result = ComicImporter.importComic(files(archive), archive.name, book)
        assertEquals(2, result.pageCount)
        assertArrayEquals(png(2), ComicImporter.pages(book)[0].readBytes())
        assertFalse(File(work.parentFile, "escape.png").exists())
    }
}
