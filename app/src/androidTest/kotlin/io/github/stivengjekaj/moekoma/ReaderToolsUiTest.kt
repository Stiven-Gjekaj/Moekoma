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
        compose.onNodeWithText("Forward at each edge").performClick()
        assertEquals(TapZones.Forward, container.settings.preferences.value.layout.taps)
        compose.onNodeWithContentDescription("Page 1").performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)!!.page == 1 }

        // "No turns": a tap at the edge shows the bars, and the page stays.
        compose.onNodeWithContentDescription("Page layout").performClick()
        compose.onNodeWithText("No turns").performClick()
        compose.onNodeWithContentDescription("Page 2").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onNodeWithContentDescription("Page 2").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(1, container.model.library.value.book(book.id)!!.page)

        // A page that fills the width still shows.
        compose.onNodeWithContentDescription("Page layout").performClick()
        compose.onNodeWithText("Fit the width").performClick()
        assertEquals(PageFit.Width, container.settings.preferences.value.layout.fit)
        compose.onNodeWithContentDescription("Page 2").assertIsDisplayed()
    }
}
