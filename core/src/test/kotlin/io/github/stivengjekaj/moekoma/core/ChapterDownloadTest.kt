package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class ChapterDownloadTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9)
    private val chapter = Novel.Chapter("One", "chapters/00001.html", "c1")

    private fun book(): File = kotlin.io.path.createTempDirectory("book").toFile().apply { deleteOnExit() }

    @Test fun keepsThePagesInTheirOrderWithTheExtensionOfTheirKind() = runBlocking {
        val book = book()
        val seen = mutableListOf<Pair<Int, Int>>()
        ChapterDownload.download(2, chapter, book, { index ->
            if (index == 0) ChapterDownload.Page(png, "image/png; charset=binary", "https://a.test/1") else ChapterDownload.Page(jpeg, null, "https://a.test/2.JPG?x=1")
        }) { done, total -> seen += done to total }
        assertTrue(ChapterDownload.isDownloaded(chapter, book))
        assertEquals(listOf("0001.png", "0002.jpg"), ChapterDownload.pages(chapter, book).map { it.name })
        assertEquals(File(book, "chapters/00001"), ChapterDownload.folder(chapter, book))
        assertEquals(listOf(1 to 2, 2 to 2), seen)
        // No part folder stays.
        assertEquals(listOf("00001"), File(book, "chapters").list()!!.toList())
        ChapterDownload.remove(chapter, book)
        assertFalse(ChapterDownload.isDownloaded(chapter, book))
        assertTrue(book.deleteRecursively())
    }

    @Test fun triesAPageThreeTimesAndKeepsNothingOfAChapterThatFails() {
        val book = book()
        var asked = 0
        // A page of HTML, such as a check page, is not an image.
        val error = assertThrows(IOException::class.java) {
            runBlocking {
                ChapterDownload.download(2, chapter, book, { index ->
                    if (index == 0) ChapterDownload.Page(png, "image/png", "u") else { asked++; ChapterDownload.Page("<html>check</html>".toByteArray(), "text/html", "u") }
                })
            }
        }
        assertEquals("Page 2 is not an image.", error.message)
        assertEquals(3, asked)
        assertFalse(ChapterDownload.isDownloaded(chapter, book))
        assertEquals(emptyList<File>(), ChapterDownload.pages(chapter, book))
        assertEquals(emptyList<String>(), File(book, "chapters").list().orEmpty().toList())
        assertTrue(book.deleteRecursively())
    }

    @Test fun takesAPageThatCameAtTheSecondAttempt() = runBlocking {
        val book = book()
        var asked = 0
        ChapterDownload.download(1, chapter, book, { asked++; if (asked == 1) throw IOException("timeout") else ChapterDownload.Page(jpeg, "image/jpeg", "u") })
        assertEquals(2, asked)
        assertTrue(ChapterDownload.isDownloaded(chapter, book))
        assertTrue(book.deleteRecursively())
    }

    @Test fun knowsTheKindsOfImage() {
        assertTrue(ChapterDownload.isImage(png))
        assertTrue(ChapterDownload.isImage(jpeg))
        assertTrue(ChapterDownload.isImage("GIF89a".toByteArray()))
        assertTrue(ChapterDownload.isImage("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertTrue(ChapterDownload.isImage(byteArrayOf(0, 0, 0, 0x1C) + "ftypavif".toByteArray()))
        assertFalse(ChapterDownload.isImage("<!DOCTYPE html>".toByteArray()))
        assertFalse(ChapterDownload.isImage(ByteArray(0)))
    }
}
