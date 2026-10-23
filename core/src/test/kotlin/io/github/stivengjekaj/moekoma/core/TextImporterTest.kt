package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.Base64

class TextImporterTest {
    @Test fun splitsAtTheHeadingsOfTheChapters() {
        val work = temporaryFolder()
        val file = File(work, "My_Web_Novel.txt").apply { writeText("Chapter 1: Dawn\nThe sun came up.\n\nShe read.\nChapter 2 - Noon\nLunch & tea <now>.\n") }
        val result = BookImporter.importBook(FileSource(file), File(work, "b"))
        assertEquals("My Web Novel", result.title)
        assertEquals(2, result.pageCount)
        val novel = Novel.load(File(work, "b"))
        assertEquals(listOf("Chapter 1: Dawn", "Chapter 2 - Noon"), novel.chapters.map { it.title })
        val second = File(work, "b/" + novel.chapters[1].path).readText()
        assertTrue(second, "<p>Lunch &amp; tea &lt;now&gt;.</p>" in second)
        val first = File(work, "b/" + novel.chapters[0].path).readText()
        assertTrue(first, "<p>The sun came up.</p>\n<p>She read.</p>" in first)
    }

    @Test fun findsChineseAndJapaneseHeadings() {
        assertEquals(listOf(null, "第一章 出发", "第二章 到达"), TextImporter.split("序\n第一章 出发\n内容\n第二章 到达\n内容\n").map { it.first })
    }

    @Test fun givesPartsToALongTextWithNoHeadings() {
        val paragraph = "word ".repeat(2_000)
        val parts = TextImporter.split(List(20) { paragraph }.joinToString("\n"))
        assertEquals(4, parts.size)
        assertEquals("Part 1", parts.first().first)
    }

    @Test fun readsShiftJis() {
        assertEquals("こんにちは、世界", TextDecoding.decode("こんにちは、世界".toByteArray(Charset.forName("Shift_JIS"))))
    }

    @Test fun readsGb18030() {
        assertEquals("第一章 少女在早晨读书。", TextDecoding.decode("第一章 少女在早晨读书。".toByteArray(Charset.forName("GB18030"))))
    }

    @Test fun readsUtf16WithAByteOrderMark() {
        assertEquals("Hello", TextDecoding.decode("Hello".toByteArray(Charsets.UTF_16)))
    }

    @Test fun readsWindows1252() {
        val text = "A café in the morning, with crème brûlée."
        assertEquals(text, TextDecoding.decode(text.toByteArray(Charset.forName("windows-1252"))))
    }

    @Test fun keepsAnHtmlFileAsOneChapter() {
        val work = temporaryFolder()
        val file = File(work, "page.html").apply { writeText("<html><head><title>A <b>Page</b></title></head><body><p>x</p></body></html>") }
        val result = BookImporter.importBook(FileSource(file), File(work, "b"))
        assertEquals("A Page", result.title)
        assertEquals(listOf(Novel.Chapter(path = "chapter.html")), Novel.load(File(work, "b")).chapters)
    }

    @Test fun readsTheSectionsTheAuthorAndTheImagesOfAnFb2() {
        val work = temporaryFolder()
        val file = File(work, "book.fb2")
        val image = Base64.getEncoder().encodeToString(page(7))
        file.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
            <description><title-info><author><first-name>Anna</first-name><last-name>Petrova</last-name></author>
            <book-title>Белые ночи</book-title><lang>ru</lang><coverpage><image l:href="#cover"/></coverpage></title-info></description>
            <body><section><title><p>Part One</p></title>
              <section><title><p>Night 1</p></title><p>First &amp; quiet.</p><image l:href="#cover"/></section>
              <section><title><p>Night 2</p></title><p>Second.</p></section>
            </section></body>
            <body name="notes"><section><p>A note.</p></section></body>
            <binary id="cover" content-type="image/png">$image</binary>
            </FictionBook>
        """.trimIndent())
        val folder = File(work, "b")
        val result = BookImporter.importBook(FileSource(file), folder)
        assertEquals("Белые ночи", result.title)
        assertEquals("Anna Petrova", result.author)
        val novel = Novel.load(folder)
        assertEquals(listOf("Part One: Night 1", "Night 2"), novel.chapters.map { it.title })
        val first = File(folder, novel.chapters[0].path).readText()
        assertTrue(first, "<p>First &amp; quiet.</p>" in first)
        assertTrue(first, "<img src=\"images/0.png\"" in first)
        assertEquals(File(folder, "chapters/images/0.png"), result.cover)
    }

    @Test fun countsTheChaptersAndThePlaceInTheChapter() {
        var book = Book(title = "N", format = BookFormat.Novel, fileName = "n.epub", pageCount = 4)
        book = book.read(chapter = 1, position = 0.5)
        assertEquals(0.375, book.progress, 0.0)
        book = book.read(chapter = 3, position = 0.99)
        assertTrue(book.finished)
    }
}
