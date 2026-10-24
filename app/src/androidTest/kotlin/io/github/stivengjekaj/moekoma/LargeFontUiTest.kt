package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The largest font size of the device: the tab labels and the headings stay on one line. */
@RunWith(AndroidJUnit4::class)
class LargeFontUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "large-${UUID.randomUUID()}")

    @After fun clean() {
        root.deleteRecursively()
    }

    @Test fun keepsEachTabLabelAndTheReadListHeadingOnOneLine() {
        val container = AppContainer(app, root)
        container.model.change { it.copy(books = it.books + Book(title = "Night Harbor", format = BookFormat.Comic, fileName = "Night", pageCount = 3)) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) { MoekomaRoot(container) }
        }
        for (label in listOf("Library", "Discover", "Search", "Settings", "Read list")) {
            assertEquals("The text $label has more than one line", 1, lines(label))
        }
    }

    /** Each tab at the largest font size: no text goes past the edge of the screen, and each text keeps a margin. */
    @Test fun keepsTheTextOfEachTabInTheScreen() {
        val container = AppContainer(app, root)
        container.model.change { it.copy(books = it.books + Book(title = "Night Harbor", format = BookFormat.Comic, fileName = "Night", pageCount = 3)) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) { MoekomaRoot(container) }
        }
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val margin = 8 * app.resources.displayMetrics.density
        for (tab in listOf("Library", "Discover", "Search", "Settings")) {
            compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
            compose.waitForIdle()
            val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
            for (node in texts) {
                // A row that scrolls to the side, such as the category chips, goes past the edge by design.
                if (generateSequence(node.parent) { it.parent }.any { SemanticsProperties.HorizontalScrollAxisRange in it.config }) continue
                val bounds = node.boundsInRoot
                if (bounds.width <= 0f) continue
                val text = node.config[SemanticsProperties.Text].joinToString()
                assertTrue("\"$text\" goes past the edge in $tab", bounds.right <= width + 1f)
                assertTrue("\"$text\" has no margin at the left in $tab", bounds.left >= margin - 1f)
            }
        }
    }

    private fun lines(text: String): Int {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single().lineCount
    }
}
