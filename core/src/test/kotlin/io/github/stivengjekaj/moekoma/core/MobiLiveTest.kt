package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URI

/** Imports real Kindle books of Project Gutenberg from the web. It runs only with MOEKOMA_LIVE=1. */
class MobiLiveTest {
    private fun importsAGutenbergBook(address: String, ext: String) {
        assumeTrue(System.getenv("MOEKOMA_LIVE") == "1")
        val data = URI(address).toURL().readBytes()
        val book = File(temporaryFolder(), "book")
        val imported = MobiImporter.importBook(data, "alice.$ext", book)
        val novel = imported.novel
        assertTrue(novel.title, "Alice" in novel.title)
        assertTrue(novel.author ?: "", "Carroll" in (novel.author ?: ""))
        assertTrue(novel.chapters.size >= 5)
        val all = novel.chapters.joinToString("") { File(book, it.path).readText() }
        assertTrue("The text of the first chapter is in a part.", "rabbit-hole" in all)
        assertTrue("The last chapter is in a part.", "Alice’s Evidence" in all || "Alice's Evidence" in all)
        assertFalse("Each image link points to a file.", "kindle:embed" in all)
        assertNotNull("The book has a cover.", imported.cover?.takeIf { it.exists() })
        println("LIVE $ext: ${novel.chapters.size} chapters: ${novel.chapters.take(6).map { it.title ?: "-" }}")
    }

    @Test fun importsTheAzw3OfGutenberg() = importsAGutenbergBook("https://www.gutenberg.org/ebooks/11.kf8.images", "azw3")

    @Test fun importsTheMobiOfGutenberg() = importsAGutenbergBook("https://www.gutenberg.org/ebooks/11.kindle.images", "mobi")
}
