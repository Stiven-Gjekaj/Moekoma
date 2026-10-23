package io.github.stivengjekaj.moekoma

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Imports an EPUB, opens it, and goes to the next chapter. */
@RunWith(AndroidJUnit4::class)
class NovelUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "ui-${UUID.randomUUID()}")

    @Test fun importsAnEpubAndReadsTheNextChapter() {
        val container = AppContainer(app, root)
        compose.setContent { MoekomaRoot(container) }
        val file = File(app.cacheDir, "Quiet_Library.epub")
        writeArchive(TestArchive.Zip, file, epub(
            "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                "<dc:title>The Quiet Library</dc:title></metadata><manifest>" +
                "<item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"b\" href=\"b.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                "</manifest><spine><itemref idref=\"a\"/><itemref idref=\"b\"/></spine></package>",
        ))
        container.model.importDocuments(listOf(Uri.fromFile(file)))
        compose.waitUntil(10_000) { container.model.library.value.books.isNotEmpty() }
        val book = container.model.library.value.books.single()
        assertEquals(BookFormat.Novel, book.format)
        assertEquals(2, book.pageCount)

        compose.onNodeWithText("The Quiet Library").performClick()
        compose.onNodeWithText("Chapter 1 of 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next chapter").performClick()
        compose.onNodeWithText("Chapter 2 of 2").assertIsDisplayed()
        compose.waitUntil(5_000) { container.model.library.value.books.single().page == 1 }
        root.deleteRecursively()
    }

    private fun epub(opf: String): List<Pair<String, ByteArray>> = listOf(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to ("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
            "<rootfiles><rootfile full-path=\"content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>").toByteArray(),
        "content.opf" to opf.toByteArray(),
        "a.xhtml" to page("One", "The morning was quiet."),
        "b.xhtml" to page("Two", "The library opened."),
    )

    /** A chapter with a script. The script must not run, so its text must not show. */
    private fun page(title: String, text: String) = ("<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>$title</title></head>" +
        "<body><h1>$title</h1><p>$text</p><p id=\"check\">The script did not run.</p>" +
        "<script>document.getElementById('check').textContent = 'THE SCRIPT RAN';</script></body></html>").toByteArray()
}
