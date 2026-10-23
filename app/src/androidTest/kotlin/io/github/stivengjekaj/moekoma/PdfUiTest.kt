package io.github.stivengjekaj.moekoma

import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.ReadingMode
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Imports a PDF, reads it from the left, and refuses a file that is not a PDF. */
@RunWith(AndroidJUnit4::class)
class PdfUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "ui-${UUID.randomUUID()}")

    /** Writes a PDF of 400 by 600 points with the pages in the test. */
    private fun writePdf(file: File, pages: Int) {
        val document = android.graphics.pdf.PdfDocument()
        repeat(pages) { index ->
            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
            page.canvas.drawRect(50f, 50f, 350f, 550f, Paint().apply { color = Color.rgb(50, 100, 150) })
            document.finishPage(page)
        }
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    @Test fun importsAPdfAndReadsItFromTheLeft() {
        val container = AppContainer(app, root)
        compose.setContent { MoekomaRoot(container) }
        val file = File(app.cacheDir, "Lecture_Notes.pdf").also { writePdf(it, 3) }
        container.model.importDocuments(listOf(Uri.fromFile(file)))
        compose.waitUntil(10_000) { container.model.library.value.books.isNotEmpty() }
        val book = container.model.library.value.books.single()
        assertEquals(BookFormat.Pdf, book.format)
        assertEquals("Lecture Notes", book.title)
        assertEquals(3, book.pageCount)
        assertEquals(ReadingMode.LeftToRight, book.mode)
        assertTrue(container.model.cover(book).exists())

        compose.onNodeWithText("Lecture Notes").performClick()
        compose.onNodeWithText("1 of 3").assertIsDisplayed()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithContentDescription("Page 1").assertIsDisplayed() }.isSuccess }
        // From the left, a tap at the right edge goes forward.
        compose.onNodeWithContentDescription("Page 1").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitUntil(5_000) { container.model.library.value.books.single().page == 1 }
        root.deleteRecursively()
    }

    @Test fun refusesAFileThatIsNotAPdf() {
        val container = AppContainer(app, root)
        compose.setContent { MoekomaRoot(container) }
        val file = File(app.cacheDir, "fake.pdf").apply { writeText("not a pdf") }
        container.model.importDocuments(listOf(Uri.fromFile(file)))
        compose.waitUntil(10_000) { container.model.fault.value != null }
        assertTrue(container.model.fault.value!!, "The file is not a PDF." in container.model.fault.value!!)
        assertTrue(container.model.library.value.books.isEmpty())
        root.deleteRecursively()
    }
}
