package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.BookOrigin
import io.github.stivengjekaj.moekoma.core.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.UUID

/** The check of all online books for new chapters, with the Aidoku source of scripts/test-server.py. It runs only with server=1. */
@RunWith(AndroidJUnit4::class)
class ChapterCheckDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun control(path: String) = URL("http://10.0.2.2:8765/control/$path").openStream().use { it.readBytes() }

    @Test fun findsTheNewChaptersOfEachBookAndCountsTheBooksThatDidNotUpdate() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        withContext(Dispatchers.IO) { control("reset") }
        val root = File(app.cacheDir, "check-test").apply { deleteRecursively() }
        val container = withContext(Dispatchers.Main) { AppContainer(app, root) }
        container.sources.save("http://10.0.2.2:8765/aidoku/index.min.json")
        repeat(60) { if (container.model.library.value.installed.none { it.info.id == "en.test" }) delay(500) }
        val installed = container.model.library.value.installed.first { it.info.id == "en.test" }
        val source = container.sources.load(installed)
        val book = container.sources.add(source.details(source.popular(1).items.single()), installed, listed = false)

        // The book has no chapters on the device, so the one chapter of the source is new.
        val folder = container.model.folder(book)
        Novel.load(folder).copy(chapters = emptyList()).save(folder)
        assertEquals(0, container.sources.refreshAll(listedOnly = true).found.size)
        val check = container.sources.refreshAll()
        assertEquals(1, check.added)
        assertEquals(0, check.failed)
        assertEquals(listOf(book.id to 1), check.found.map { it.first.id to it.second })
        assertEquals("1 new chapter.", check.message())
        // The new chapter goes into the updates.
        assertEquals(listOf(book.id to 0), container.model.library.value.updates.map { it.book to it.chapter })
        assertEquals(null, container.sources.refreshAll().message(quiet = true))

        // A source whose code is not on the device does not answer, so its book did not update.
        val lost = installed.copy(info = installed.info.copy(id = "en.lost"))
        container.model.change { library ->
            library.copy(
                installed = library.installed + lost,
                books = library.books + book.copy(id = UUID.randomUUID(), title = "Lost", origin = BookOrigin(lost.list, "en.lost", "/lost")),
            )
        }
        assertEquals("0 new chapters. 1 book did not update: Lost.", container.sources.refreshAll().message())
        root.deleteRecursively()
        Unit
    }

    @Test fun downloadsTheNewChaptersOfABookThatAsksForThem() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        withContext(Dispatchers.IO) { control("reset") }
        val root = File(app.cacheDir, "check-download-test").apply { deleteRecursively() }
        val container = withContext(Dispatchers.Main) { AppContainer(app, root) }
        container.sources.save("http://10.0.2.2:8765/aidoku/index.min.json")
        repeat(60) { if (container.model.library.value.installed.none { it.info.id == "en.test" }) delay(500) }
        val installed = container.model.library.value.installed.first { it.info.id == "en.test" }
        val source = container.sources.load(installed)
        val book = container.sources.add(source.details(source.popular(1).items.single()), installed, listed = true)
        container.model.update(book.id) { it.copy(autoDownload = true) }
        val folder = container.model.folder(book)
        Novel.load(folder).copy(chapters = emptyList()).save(folder)

        assertEquals(1, container.sources.refreshAll().added)
        // The new chapter goes into the queue of the downloads, and the test runs the queue here.
        container.downloads.run { _, _, _ -> }
        assertEquals(setOf(0), container.downloads.downloaded(container.model.library.value.book(book.id)!!))
        root.deleteRecursively()
        Unit
    }
}
