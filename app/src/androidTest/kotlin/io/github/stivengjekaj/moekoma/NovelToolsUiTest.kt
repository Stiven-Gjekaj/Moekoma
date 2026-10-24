package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The tools of the novel reader: the search of the book, the marks, and the speech. */
@RunWith(AndroidJUnit4::class)
class NovelToolsUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "novel-tools-${UUID.randomUUID()}")

    @After fun clean() { root.deleteRecursively() }

    /** A novel of two chapters, open in the reader at the first chapter. */
    private fun openNovel(): Pair<AppContainer, Book> {
        val container = AppContainer(app, root)
        val book = Book(title = "Dawn Tea", format = BookFormat.Novel, fileName = "Quiet", pageCount = 2)
        val folder = container.model.folder(book).apply { mkdirs() }
        File(folder, "chapters").mkdirs()
        File(folder, "chapters/00001.html").writeText("<html><body><h2>Arrival</h2><p>The train came in. Mina stood up.</p></body></html>")
        File(folder, "chapters/00002.html").writeText("<html><body><h2>Harbor Lights</h2><p>The lanterns of the quay shone. The sea was calm.</p></body></html>")
        Novel("Dawn Tea", chapters = listOf(Novel.Chapter("Arrival", "chapters/00001.html"), Novel.Chapter("Harbor Lights", "chapters/00002.html"))).save(folder)
        container.model.change { it.copy(books = it.books + book) }
        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("Dawn Tea").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search the book")).fetchSemanticsNodes().isNotEmpty() }
        return container to book
    }

    @Test fun searchesTheBookAndOpensTheChapterOfAResult() {
        val (container, book) = openNovel()
        compose.onNodeWithContentDescription("Search the book").performClick()
        compose.onNodeWithText("Words in the book").performTextInput("lanterns")
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("lanterns of the quay", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("lanterns of the quay", substring = true).performClick()
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)!!.page == 1 }
    }

    @Test fun keepsABookmarkAndOpensItsPlace() {
        val (container, book) = openNovel()
        compose.onNodeWithContentDescription("Marks").performClick()
        compose.onNodeWithText("Add a bookmark here").performClick()
        compose.waitUntil(5_000) { container.model.library.value.marks(book.id).isNotEmpty() }
        val mark = container.model.library.value.marks(book.id).single()
        assertEquals(0, mark.chapter)
        // The bookmark has the first words at the top of the screen.
        assertTrue(mark.text, mark.text == "Arrival" || mark.text.startsWith("The train"))

        // From another chapter, a tap on the bookmark opens its chapter.
        compose.onNodeWithContentDescription("Next chapter").performClick()
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)!!.page == 1 }
        compose.onNodeWithContentDescription("Marks").performClick()
        compose.onNodeWithContentDescription("Bookmark: ${mark.text}").performClick()
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)!!.page == 0 }

        compose.onNodeWithContentDescription("Marks").performClick()
        compose.onNodeWithContentDescription("Remove the mark").performClick()
        compose.waitUntil(5_000) { container.model.library.value.marks(book.id).isEmpty() }
    }
}
