package io.github.stivengjekaj.moekoma

import android.app.Application
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
import io.github.stivengjekaj.moekoma.data.PageFit
import io.github.stivengjekaj.moekoma.data.TapZones
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The page layout menu of the reader of pages: the fit and the taps at the edges. */
@RunWith(AndroidJUnit4::class)
class ReaderToolsUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "reader-tools-${UUID.randomUUID()}")
    private lateinit var container: AppContainer

    /** Chooses an item of an open menu when the menu shows it. */
    private fun choose(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    @After fun reset() {
        // The layout is a preference of the app, so the test puts back the layout of the app.
        container.settings.update { it.copy(layout = io.github.stivengjekaj.moekoma.data.PageLayout()) }
        root.deleteRecursively()
    }

    @Test fun choosesTheTapsAtTheEdgesAndTheFit() {
        container = AppContainer(app, root)
        container.settings.update { it.copy(layout = io.github.stivengjekaj.moekoma.data.PageLayout()) }
        val archive = File(app.cacheDir, "Tools.cbz")
        writeArchive(TestArchive.Zip, archive, (1..4).map { "p$it.png" to png(it * 40) })
        container.model.importDocuments(listOf(Uri.fromFile(archive)))
        compose.setContent { MoekomaRoot(container) }
        compose.waitUntil(10_000) { container.model.library.value.books.isNotEmpty() }
        val book = container.model.library.value.books.single()
        container.model.update(book.id) { it.copy(mode = io.github.stivengjekaj.moekoma.core.ReadingMode.LeftToRight) }
        compose.onNodeWithText("Tools").performClick()

        // "Forward at each edge": the left edge of a western comic goes forward too.
        compose.onNodeWithContentDescription("Page layout").performClick()
        choose("Forward at each edge")
        assertEquals(TapZones.Forward, container.settings.preferences.value.layout.taps)
        compose.onNodeWithContentDescription("Page 1").performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)!!.page == 1 }

        // "No turns": a tap at the edge shows the bars, and the page stays.
        compose.onNodeWithContentDescription("Page layout").performClick()
        choose("No turns")
        compose.onNodeWithContentDescription("Page 2").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onNodeWithContentDescription("Page 2").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(1, container.model.library.value.book(book.id)!!.page)

        // A page that fills the width still shows.
        compose.onNodeWithContentDescription("Page layout").performClick()
        choose("Fit the width")
        assertEquals(PageFit.Width, container.settings.preferences.value.layout.fit)
        compose.onNodeWithContentDescription("Page 2").assertIsDisplayed()
    }

    @Test fun showsTwoPagesOnAWideScreen() {
        container = AppContainer(app, root)
        container.settings.update { it.copy(layout = io.github.stivengjekaj.moekoma.data.PageLayout()) }
        val archive = File(app.cacheDir, "Pairs.cbz")
        writeArchive(TestArchive.Zip, archive, (1..5).map { "p$it.png" to png(it * 40) })
        container.model.importDocuments(listOf(Uri.fromFile(archive)))
        compose.setContent {
            // A screen that is wider than it is tall, as a tablet or a phone on its side.
            val wide = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { screenWidthDp = 900; screenHeightDp = 420 }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides wide) { MoekomaRoot(container) }
        }
        compose.waitUntil(10_000) { container.model.library.value.books.isNotEmpty() }
        val book = container.model.library.value.books.single()
        container.model.update(book.id) { it.copy(mode = io.github.stivengjekaj.moekoma.core.ReadingMode.LeftToRight) }
        compose.onNodeWithText("Pairs").performClick()
        // The first page is alone, as the cover of a book is.
        compose.onNodeWithText("1 of 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1").performTouchInput { click(Offset(width * 0.95f, height / 2f)) }
        compose.onNodeWithText("2-3 of 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 3").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onNodeWithText("4-5 of 5").assertIsDisplayed()
        assertEquals(3, container.model.library.value.book(book.id)!!.page)

        // The pairs move by one page.
        compose.onNodeWithContentDescription("Page layout").performClick()
        choose("Move the pairs by one page")
        compose.onNodeWithText("3-4 of 5").assertIsDisplayed()
    }
}
