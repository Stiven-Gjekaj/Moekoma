package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The reading history: each read goes into it, private reading keeps a read out of it, and the History list reads a chapter again. */
@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "history-${UUID.randomUUID()}")

    private fun novel(container: AppContainer): Book {
        val book = Book(title = "Dawn Tea", format = BookFormat.Novel, fileName = "Quiet", pageCount = 2)
        val folder = container.model.folder(book).apply { mkdirs() }
        File(folder, "chapters").mkdirs()
        listOf("Arrival", "Harbor Lights").forEachIndexed { i, title ->
            File(folder, "chapters/%05d.html".format(i + 1)).writeText("<html><body><h2>$title</h2><p>Text.</p></body></html>")
        }
        Novel("Dawn Tea", chapters = listOf(Novel.Chapter("Arrival", "chapters/00001.html"), Novel.Chapter("Harbor Lights", "chapters/00002.html"))).save(folder)
        container.model.change { it.copy(books = it.books + book) }
        return book
    }

    @Test fun keepsEachReadAndNotAPrivateRead() {
        val container = AppContainer(app, root)
        val book = novel(container)
        container.model.update(book.id) { it.read(chapter = 1, position = 0.2) }
        container.model.update(book.id) { it.read(chapter = 1, position = 0.5) }
        assertEquals("Two reads of one chapter on one day are one entry.", listOf(1), container.model.library.value.history.map { it.place })

        val before = container.model.library.value.book(book.id)!!.lastRead
        container.model.privateReading = { true }
        container.model.update(book.id) { it.read(chapter = 0, position = 0.1) }
        assertEquals(listOf(1), container.model.library.value.history.map { it.place })
        // A private read keeps the place, but not the time of the read.
        assertEquals(0, container.model.library.value.book(book.id)!!.page)
        assertEquals(before, container.model.library.value.book(book.id)!!.lastRead)
        val fresh = novel(container)
        container.model.update(fresh.id) { it.read(chapter = 1, position = 0.1) }
        assertNull("A book that the user reads in private does not go into Continue reading.", container.model.library.value.book(fresh.id)!!.lastRead)
        root.deleteRecursively()
    }

    @Test fun readsAChapterOfTheHistoryAgain() {
        val container = AppContainer(app, root)
        val book = novel(container)
        container.model.update(book.id) { it.read(chapter = 1, position = 0.5) }
        container.model.update(book.id) { it.copy(page = 0) }

        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Harbor Lights").assertIsDisplayed()
        compose.onNodeWithText("Harbor Lights").performClick()
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)?.page == 1 }
        root.deleteRecursively()
    }
}
