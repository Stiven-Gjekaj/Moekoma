package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ComicImporterTest {
    @Test fun copiesThePagesInTheNaturalOrderAndSkipsTheFilesOfMacOSAndThePathsOut() {
        val work = temporaryFolder()
        val archive = File(work, "My_Comic_v01.cbz")
        writeZip(archive, listOf(
            "p10.png" to page(10),
            "__MACOSX/._p1.png" to "resource fork".toByteArray(),
            "p2.png" to page(2),
            "notes.txt" to "no page".toByteArray(),
            "p1.png" to page(1),
            "../../escape.png" to page(99),
        ))
        val book = File(work, "book")

        val result = ComicImporter.importComic(ZipFiles(archive), archive.name, book)

        assertEquals("My Comic v01", result.title)
        assertEquals(3, result.pageCount)
        val pages = ComicImporter.pages(book)
        assertEquals(listOf("0001.png", "0002.png", "0003.png"), pages.map { it.name })
        assertArrayEquals(page(1), pages[0].readBytes())
        assertArrayEquals(page(2), pages[1].readBytes())
        assertArrayEquals(page(10), pages[2].readBytes())
        assertFalse(File(work, "escape.png").exists())
        assertFalse(File(work.parentFile, "escape.png").exists())
        assertFalse(File(book, ".staging").exists())
    }

    @Test fun takesTheTitleAndTheDirectionFromComicInfo() {
        val work = temporaryFolder()
        val archive = File(work, "file.cbz")
        writeZip(archive, listOf(
            "Sub/ComicInfo.xml" to comicInfo(mapOf("Series" to "Berserk", "Number" to "1", "Manga" to "YesAndRightToLeft")),
            "001.png" to page(1),
        ))
        val result = ComicImporter.importComic(ZipFiles(archive), archive.name, File(work, "book"))
        assertEquals("Berserk 1", result.title)
        assertEquals(ReadingMode.RightToLeft, result.info?.readingMode)
    }

    @Test fun importsAFolderOfImagesWithChapters() {
        val work = temporaryFolder()
        val source = File(work, "Webtoon")
        for ((path, mark) in listOf("Ch 10/1.png" to 3, "Ch 2/1.png" to 2, "Ch 2/.hidden.png" to 9)) {
            File(source, path).apply { parentFile.mkdirs(); writeBytes(page(mark)) }
        }
        val book = File(work, "book")

        val result = ComicImporter.importComic(FolderFiles(source), source.name, book)

        assertEquals("Webtoon", result.title)
        assertEquals(2, result.pageCount)
        assertArrayEquals(page(2), ComicImporter.pages(book)[0].readBytes())
    }

    @Test fun failsAndLeavesNoFolderWhenTheArchiveHasNoImages() {
        val work = temporaryFolder()
        val archive = File(work, "empty.cbz")
        writeZip(archive, listOf("readme.txt" to "text".toByteArray()))
        val book = File(work, "book")
        assertThrows(ImportException.NoPages::class.java) { ComicImporter.importComic(ZipFiles(archive), archive.name, book) }
        assertFalse(book.exists())
    }

    @Test fun keepsTheNameOfAFileWithNoExtension() {
        assertEquals("README", ComicImporter.title("README"))
    }
}
