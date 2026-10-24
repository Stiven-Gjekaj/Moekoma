package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class BookExportTest {
    private val directory = Files.createTempDirectory("export").toFile()

    @Test fun makesACbzOfThePagesOfAComic() {
        val folder = Files.createTempDirectory("comic").toFile()
        listOf("0001.png", "0002.png").forEach { java.io.File(folder, it).writeBytes(byteArrayOf(1, 2, 3)) }
        java.io.File(folder, "cover.jpg").writeBytes(byteArrayOf(4))
        val file = BookExport.file(BookFormat.Comic, "Tide: Vol/1", folder, directory)!!
        assertEquals("A file name has no slash or colon.", "Tide  Vol 1.cbz", file.name)
        ZipFile(file).use { zip -> assertEquals(listOf("0001.png", "0002.png"), zip.entries().toList().map { it.name }) }
    }

    @Test fun makesAnEpubThatStartsWithItsMimetype() {
        val folder = Files.createTempDirectory("novel").toFile()
        java.io.File(folder, "content/META-INF").mkdirs()
        java.io.File(folder, "content/META-INF/container.xml").writeText("<container/>")
        java.io.File(folder, "content/mimetype").writeText("application/epub+zip")
        java.io.File(folder, "content/OEBPS").mkdirs()
        java.io.File(folder, "content/OEBPS/one.xhtml").writeText("<html/>")
        val file = BookExport.file(BookFormat.Novel, "Dawn", folder, directory)!!
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            assertEquals(listOf("mimetype", "META-INF/container.xml", "OEBPS/one.xhtml"), entries.map { it.name })
            assertEquals(ZipEntry.STORED, entries.first().method)
            assertEquals("application/epub+zip", zip.getInputStream(entries.first()).readBytes().decodeToString())
        }
    }

    @Test fun givesNoFileForASeriesOrANovelWithNoEpub() {
        val folder = Files.createTempDirectory("other").toFile()
        assertNull(BookExport.file(BookFormat.Series, "S", folder, directory))
        assertFalse(BookExport.canShare(BookFormat.Novel, folder))
    }
}
