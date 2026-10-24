package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookCategory
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/** The tools of the read list: the search, the filters, the categories, and the editor of the categories. */
@RunWith(AndroidJUnit4::class)
class LibraryToolsUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "tools-${UUID.randomUUID()}")

    @Test fun searchesFiltersAndSortsTheReadListIntoCategories() {
        val container = AppContainer(app, root)
        val reading = BookCategory(name = "Reading")
        val tide = Book(title = "Tide Comic", format = BookFormat.Comic, fileName = "t.cbz", pageCount = 3, added = Instant.ofEpochSecond(3), categories = listOf(reading.id))
        val sea = Book(title = "Sea Novel", author = "Mina", format = BookFormat.Novel, fileName = "s.epub", pageCount = 3, added = Instant.ofEpochSecond(2), finished = true)
        container.model.change { it.copy(books = listOf(tide, sea), categories = listOf(reading)) }
        compose.setContent { MoekomaRoot(container) }

        // The search finds a title or a writer.
        compose.onNodeWithContentDescription("Search the library").performClick()
        compose.onNodeWithText("Search the library").performTextInput("mina")
        compose.onNodeWithText("Sea Novel").assertIsDisplayed()
        compose.onNodeWithText("Tide Comic").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close the search").performClick()

        // A category shows only its books.
        compose.onNodeWithText("Reading").performClick()
        compose.onNodeWithText("Tide Comic").assertIsDisplayed()
        compose.onNodeWithText("Sea Novel").assertDoesNotExist()
        compose.onNodeWithText("All").performClick()

        // A filter keeps the books that fit it.
        compose.onNodeWithContentDescription("Filter the read list").performClick()
        compose.onNodeWithText("Unread").performClick()
        compose.onNodeWithText("Sea Novel").assertDoesNotExist()
        compose.onNodeWithText("Unread").performClick()
        compose.onNodeWithText("Comics").performClick()
        compose.onNodeWithText("Comics").performClick()

        // The menu of a book puts it in a category.
        compose.onNodeWithText("Sea Novel").performTouchInput { longClick() }
        compose.onNodeWithText("Categories").performClick()
        compose.onNode(hasText("Reading") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { container.model.library.value.book(sea.id)!!.categories == listOf(reading.id) }
        compose.onNodeWithText("Done").performClick()

        // The editor adds and removes a category, and a removed category leaves each book.
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("New category").performTextInput("On hold")
        compose.onNodeWithText("Add").performClick()
        compose.waitUntil(5_000) { container.model.library.value.categories.map { it.name } == listOf("Reading", "On hold") }
        compose.onNodeWithContentDescription("Remove Reading").performClick()
        compose.waitUntil(5_000) { container.model.library.value.categories.map { it.name } == listOf("On hold") }
        assertEquals(listOf(emptyList<UUID>(), emptyList()), container.model.library.value.books.map { it.categories })
        root.deleteRecursively()
    }
}
