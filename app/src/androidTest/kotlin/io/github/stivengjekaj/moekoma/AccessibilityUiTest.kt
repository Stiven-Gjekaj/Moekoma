package io.github.stivengjekaj.moekoma

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheckNames
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import java.io.File
import java.util.UUID
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.anyOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the Accessibility Test Framework on each main screen, as the accessibility audit of Xcode does in Asabunko.
 * A missing label, a small touch target, or a wrong role fails the test.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "a11y-${UUID.randomUUID()}")

    @After fun clean() {
        root.deleteRecursively()
    }

    private fun open(): AppContainer {
        val container = AppContainer(app, root)
        compose.enableAccessibilityChecks(
            AccessibilityValidator().setRunChecksFromRootView(true)
                // The contrast shows as a note, as in Asabunko. The colours of the palette have a contrast of 4.5 or more.
                .setSuppressingResultMatcher(matchesCheckNames(anyOf(`is`("TextContrastCheck"), `is`("ImageContrastCheck")))),
        )
        compose.setContent { MoekomaRoot(container) }
        return container
    }

    private fun audit(tab: String) {
        compose.onNodeWithText(tab).performClick()
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun library() { open(); audit("Library") }

    @Test fun discover() { open(); audit("Discover") }

    @Test fun search() { open(); audit("Search") }

    @Test fun settings() { open(); audit("Settings") }

    /** The pages of Settings: Storage, Trackers, and the move of books. */
    @Test fun thePagesOfSettings() {
        open()
        compose.onNodeWithText("Settings").performClick()
        for (row in listOf("Storage", "Trackers", "Move books to another source")) {
            compose.onNodeWithText(row).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onRoot().tryPerformAccessibilityChecks()
            compose.onNodeWithContentDescription("Back").performClick()
        }
    }

    /** The reader of a comic, with its bars, and the reader of a novel. */
    @Test fun theReaders() {
        val container = open()
        val archive = File(root.apply { mkdirs() }, "Audit Comic.cbz")
        writeArchive(TestArchive.Zip, archive, (1..3).map { "p$it.png" to png(it * 60) })
        container.model.importDocuments(listOf(Uri.fromFile(archive)))
        compose.waitUntil(10_000) { container.model.library.value.books.isNotEmpty() }
        compose.onNodeWithText("Audit Comic").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Close")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithContentDescription("Close").performClick()

        val novel = Book(title = "Quiet Mornings", format = BookFormat.Novel, fileName = "Quiet", pageCount = 1)
        val folder = container.model.folder(novel).apply { mkdirs() }
        File(folder, "chapters").mkdirs()
        File(folder, "chapters/00001.html").writeText("<html><body><h2>Arrival</h2><p>The train came in.</p></body></html>")
        Novel("Quiet Mornings", chapters = listOf(Novel.Chapter("Arrival", "chapters/00001.html"))).save(folder)
        container.model.change { it.copy(books = it.books + novel) }
        compose.onNodeWithText("Quiet Mornings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Search the book")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
