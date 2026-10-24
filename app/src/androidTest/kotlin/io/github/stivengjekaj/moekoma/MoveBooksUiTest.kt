package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.BookOrigin
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Moves a book of a source that is gone to the Aidoku source of scripts/test-server.py. It runs only with server=1. */
@RunWith(AndroidJUnit4::class)
class MoveBooksUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun findsTheTitleInTheNewSourceAndMovesTheBook() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        val root = File(app.cacheDir, "move-books-test").apply { deleteRecursively() }
        val container = AppContainer(app, root)
        runBlocking {
            container.sources.save("http://10.0.2.2:8765/aidoku/index.min.json")
            repeat(60) { if (container.model.library.value.installed.none { it.info.id == "en.test" }) delay(500) }
        }
        val installed = container.model.library.value.installed.first { it.info.id == "en.test" }
        val book = Book(title = "Aidoku Sunrise", format = BookFormat.Series, fileName = "Old Scans", pageCount = 1, origin = BookOrigin(UUID.randomUUID(), "en.old", "/old"))
        Novel("Aidoku Sunrise", chapters = listOf(Novel.Chapter("Ch. 1", "chapters/00001.html", "/old/1"))).save(container.model.folder(book).apply { mkdirs() })
        container.model.change { it.copy(books = it.books + book) }

        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Move books to another source").performScrollTo().performClick()
        compose.onNodeWithText("Old Scans").performClick()
        compose.onNodeWithText("Choose a source").performClick()
        compose.onNodeWithText(installed.info.name).performClick()
        compose.onNodeWithText("Find the titles").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("Move 1 book") > 0 }
        compose.onNodeWithText("Move 1 book").performScrollTo().performClick()
        compose.waitUntil(10_000) { container.model.library.value.book(book.id)?.origin?.source == "en.test" }
        compose.onNodeWithText("1 book moved.").assertIsDisplayed()
        assertEquals(installed.list, container.model.library.value.book(book.id)!!.origin!!.list)
        root.deleteRecursively()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
