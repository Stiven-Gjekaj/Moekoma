package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubImporterTest {
    @Test fun readsTheSpineTheTitlesOfTheNavAndTheCover() {
        val work = temporaryFolder()
        val file = File(work, "book.epub")
        val opf = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>The Quiet Library</dc:title><dc:creator>A. Writer</dc:creator><dc:creator>B. Writer</dc:creator>
                <dc:language>en</dc:language><dc:description>&lt;p&gt;A calm &lt;b&gt;story&lt;/b&gt;.&lt;/p&gt;</dc:description>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c2" href="text/two%20b.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/one.xhtml" media-type="application/xhtml+xml"/>
                <item id="img" href="images/cover.png" media-type="image/png" properties="cover-image"/>
              </manifest>
              <spine page-progression-direction="rtl"><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>
        """.trimIndent()
        val nav = """
            <?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="landmarks"><ol><li><a href="text/two%20b.xhtml">Wrong</a></li></ol></nav>
            <nav epub:type="toc"><ol><li><a href="text/one.xhtml#start">One&nbsp;Morning</a></li><li><a href="text/two%20b.xhtml">Two</a></li></ol></nav>
            </body></html>
        """.trimIndent().toByteArray()
        writeZip(file, epub(opf, listOf(
            "OEBPS/nav.xhtml" to nav, "OEBPS/text/one.xhtml" to chapter("one"), "OEBPS/text/two b.xhtml" to chapter("two"),
            "OEBPS/images/cover.png" to page(1), "../escape.txt" to "x".toByteArray(),
        )))
        val folder = File(work, "book")

        val result = BookImporter.importBook(FileSource(file), folder)

        assertEquals(BookFormat.Novel, result.format)
        assertEquals("The Quiet Library", result.title)
        assertEquals("A. Writer, B. Writer", result.author)
        assertEquals("A calm story .", result.summary)
        assertEquals(2, result.pageCount)
        val novel = Novel.load(folder)
        assertTrue(novel.rightToLeft)
        assertEquals("en", novel.language)
        assertEquals(
            listOf(Novel.Chapter("One Morning", "content/OEBPS/text/one.xhtml"), Novel.Chapter("Two", "content/OEBPS/text/two b.xhtml")),
            novel.chapters,
        )
        assertTrue(File(folder, "content/OEBPS/text/two b.xhtml").exists())
        assertEquals(File(folder, "content/OEBPS/images/cover.png"), result.cover)
        assertFalse(File(work, "escape.txt").exists())
    }

    @Test fun readsTheTitlesOfAnNcxInAnEpub2() {
        val work = temporaryFolder()
        val file = File(work, "old.epub")
        val opf = """
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Old</dc:title><meta name="cover" content="cv"/></metadata>
            <manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="a" href="a.html" media-type="application/xhtml+xml"/><item id="cv" href="c.jpg" media-type="image/jpeg"/></manifest>
            <spine toc="ncx"><itemref idref="a"/></spine></package>
        """.trimIndent()
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint id="p"><navLabel><text>First</text></navLabel>
            <content src="a.html"/></navPoint></navMap></ncx>
        """.trimIndent().toByteArray()
        writeZip(file, epub(opf, listOf("toc.ncx" to ncx, "a.html" to chapter("a")), opfPath = "content.opf"))
        BookImporter.importBook(FileSource(file), File(work, "b"))
        val novel = Novel.load(File(work, "b"))
        assertEquals(listOf(Novel.Chapter("First", "content/a.html")), novel.chapters)
        assertFalse(novel.rightToLeft)
    }

    @Test fun refusesAnEpubWithEncryptedChapters() {
        val work = temporaryFolder()
        val file = File(work, "drm.epub")
        val encryption = """
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
            <enc:EncryptedData><enc:CipherData><enc:CipherReference URI="OEBPS/a.xhtml"/></enc:CipherData></enc:EncryptedData></encryption>
        """.trimIndent().toByteArray()
        writeZip(file, epub("<package/>", listOf("META-INF/encryption.xml" to encryption)))
        val folder = File(work, "b")
        assertThrows(NovelException.Encrypted::class.java) { BookImporter.importBook(FileSource(file), folder) }
        assertFalse(folder.exists())
    }

    @Test fun normalizesThePaths() {
        assertEquals("OEBPS/images/a.png", EpubImporter.normalize("OEBPS/text/../images/./a.png"))
        assertNull(EpubImporter.safePath("a/../../b"))
        assertNull(EpubImporter.safePath("/etc/passwd"))
        assertEquals("OEBPS/a.xhtml", EpubImporter.safePath("OEBPS/a.xhtml"))
    }

    @Test fun doesNotLoadAnExternalEntityOfABook() {
        val secret = File(temporaryFolder(), "secret.txt").apply { writeText("secret") }
        val root = XmlElement.parse("<?xml version=\"1.0\"?><!DOCTYPE p [<!ENTITY x SYSTEM \"${secret.toURI()}\">]><p>&x;</p>".toByteArray())
        assertFalse((root?.allText ?: "").contains("secret"))
    }
}
