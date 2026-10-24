package io.github.stivengjekaj.moekoma

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.ReadingMode
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Imports a CBZ into an empty library, reads it from the right to the left, and changes the look. */
@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "ui-${UUID.randomUUID()}")

    @Test fun importsAComicAndReadsItFromTheRightToTheLeft() {
        val container = AppContainer(app, root)
        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("Your shelf is empty").assertIsDisplayed()

        val archive = File(app.cacheDir, "Test_Manga.cbz")
        writeArchive(TestArchive.Zip, archive, (1..3).map { "p$it.png" to png(it * 40) } + ("ComicInfo.xml" to
            "<ComicInfo><Series>Test Manga</Series><Number>1</Number><Manga>YesAndRightToLeft</Manga></ComicInfo>".toByteArray()))
        container.model.importDocuments(listOf(Uri.fromFile(archive)))
        compose.waitUntil(10_000) { container.model.library.value.books.isNotEmpty() }
        val book = container.model.library.value.books.single()
        assertEquals("Test Manga 1", book.title)
        assertEquals(ReadingMode.RightToLeft, book.mode)

        compose.onNodeWithText("Test Manga 1").performClick()
        compose.onNodeWithText("1 of 3").assertIsDisplayed()
        // In a manga, a tap at the left edge goes forward.
        compose.onNodeWithContentDescription("Page 1").performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        compose.waitUntil(5_000) { container.model.library.value.books.single().page == 1 }
        compose.onNodeWithText("2 of 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()

        compose.onNodeWithText("Continue reading").assertIsDisplayed()
        compose.onNodeWithText("Read list").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Paper").performScrollTo().performClick()
        assertEquals("Paper", container.settings.preferences.value.appearance.label)
        container.settings.update { it.copy(appearance = io.github.stivengjekaj.moekoma.ui.theme.Appearance.System) }
        root.deleteRecursively()
    }

    /** A library file that does not load stays as it is, until the user starts a new library. */
    @Test fun keepsALibraryFileThatDoesNotLoad() {
        root.mkdirs()
        val file = File(root, "library.json").apply { writeText("{\"form\": 1, \"books\": [{\"title\": 5}]") }
        val container = AppContainer(app, root)
        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("OK").performClick()
        container.model.importDocuments(emptyList())
        container.model.setDefaultMode(ReadingMode.Vertical)
        Thread.sleep(800)
        assertEquals("{\"form\": 1, \"books\": [{\"title\": 5}]", file.readText())
        assertEquals(1, root.listFiles()!!.count { it.name.startsWith("library.broken-") })

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Start a new library").performClick()
        compose.onAllNodesWithText("Start a new library")[1].performClick()
        compose.waitUntil(3_000) { file.readText().startsWith("{\"form\":1") }
        root.deleteRecursively()
    }
}
