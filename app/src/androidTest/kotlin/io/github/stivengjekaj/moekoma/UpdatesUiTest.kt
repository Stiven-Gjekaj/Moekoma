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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The Updates list of the library: the new chapters of the checks, and a tap that reads the chapter. */
@RunWith(AndroidJUnit4::class)
class UpdatesUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "updates-${UUID.randomUUID()}")

    @Test fun showsTheNewChaptersAndReadsOneOfThem() {
        val container = AppContainer(app, root)
        val book = Book(title = "Dawn Tea", format = BookFormat.Novel, fileName = "Quiet", pageCount = 2)
        val folder = container.model.folder(book).apply { mkdirs() }
        File(folder, "chapters").mkdirs()
        listOf("Arrival", "Harbor Lights").forEachIndexed { i, title ->
            File(folder, "chapters/%05d.html".format(i + 1)).writeText("<html><body><h2>$title</h2><p>Text.</p></body></html>")
        }
        Novel("Dawn Tea", chapters = listOf(Novel.Chapter("Arrival", "chapters/00001.html"), Novel.Chapter("Harbor Lights", "chapters/00002.html"))).save(folder)
        container.model.change { it.copy(books = it.books + book).record(listOf(1 to "Harbor Lights"), book.id) }

        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("1 new chapter").assertIsDisplayed()
        compose.onNodeWithText("Updates").performClick()
        compose.onNodeWithText("Harbor Lights").assertIsDisplayed()
        compose.onNodeWithText("Harbor Lights").performClick()
        // The reader opens at the new chapter.
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)?.page == 1 }
        assertEquals(0.0, container.model.library.value.book(book.id)!!.position, 0.0)
        root.deleteRecursively()
    }
}
