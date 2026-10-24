package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.data.Downloads
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL

/** The Storage page: the space of the downloads of each book, and their removal. It runs only with server=1. */
@RunWith(AndroidJUnit4::class)
class StorageUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun showsTheDownloadsOfABookAndRemovesThem() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        URL("http://10.0.2.2:8765/control/reset").openStream().use { it.readBytes() }
        val root = File(app.cacheDir, "storage-test").apply { deleteRecursively() }
        val container = AppContainer(app, root)
        val book = runBlocking {
            container.sources.save("http://10.0.2.2:8765/aidoku/index.min.json")
            repeat(60) { if (container.model.library.value.installed.none { it.info.id == "en.test" }) delay(500) }
            val installed = container.model.library.value.installed.first { it.info.id == "en.test" }
            val source = container.sources.load(installed)
            val book = container.sources.add(source.details(source.popular(1).items.single()), installed, listed = true)
            container.downloads.enqueue(listOf(Downloads.Job(book.id, 0)))
            container.downloads.run { _, _, _ -> }
            book
        }
        assertEquals(setOf(0), container.downloads.downloaded(book))

        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Storage").performScrollTo().performClick()
        compose.onNodeWithText("1 chapter on the device").assertIsDisplayed()
        compose.onNodeWithText("Remove all").performClick()
        compose.waitUntil(5_000) { container.downloads.downloaded(book).isEmpty() }
        compose.onNodeWithText("No downloads").assertIsDisplayed()
        root.deleteRecursively()
    }
}
