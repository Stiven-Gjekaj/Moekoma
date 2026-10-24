package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.data.Downloads
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL

/**
 * Downloads a chapter of the Aidoku source of scripts/test-server.py into a library of its own, and reads it with no
 * network. It runs only with the argument server=1.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun control(path: String) = URL("http://10.0.2.2:8765/control/$path").openStream().use { it.readBytes() }

    @Test fun downloadsAChapterAndKeepsTheQueueInAFile() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        withContext(Dispatchers.IO) { control("reset") }
        val root = File(app.cacheDir, "downloads-test").apply { deleteRecursively() }
        val container = withContext(Dispatchers.Main) { AppContainer(app, root) }
        container.sources.save("http://10.0.2.2:8765/aidoku/index.min.json")
        var installed = container.model.library.value.installed.firstOrNull { it.info.id == "en.test" }
        repeat(60) { if (installed == null) { delay(500); installed = container.model.library.value.installed.firstOrNull { it.info.id == "en.test" } } }
        val source = container.sources.load(installed!!)
        val details = source.details(source.popular(1).items.single())
        val book = container.sources.add(details, installed!!, listed = true)

        // The queue stays in a file, so a new start of the app goes on with it.
        container.downloads.enqueue(listOf(Downloads.Job(book.id, 0)))
        assertEquals(listOf(Downloads.Job(book.id, 0)), Downloads(app, container.model, container.sources, root).state.value.queue)

        container.downloads.run { _, _, _ -> }
        val state = container.downloads.state.value
        assertEquals(emptyList<Downloads.Job>(), state.queue)
        assertEquals(emptyMap<Downloads.Job, String>(), state.failed)
        assertEquals(listOf("0001.png", "0002.png"), container.downloads.pages(book, 0)?.map { it.name })
        assertEquals(setOf(0), container.downloads.downloaded(book))

        // A page that does not come is a fault of the chapter, and no part of it stays.
        container.downloads.remove(book)
        assertEquals(emptySet<Int>(), container.downloads.downloaded(book))
        withContext(Dispatchers.IO) { control("offline") }
        try {
            container.downloads.enqueue(listOf(Downloads.Job(book.id, 0)))
            container.downloads.run { _, _, _ -> }
        } finally {
            withContext(Dispatchers.IO) { control("online") }
        }
        assertTrue(container.downloads.state.value.failed.containsKey(Downloads.Job(book.id, 0)))
        assertEquals(null, container.downloads.pages(book, 0))
        root.deleteRecursively()
        Unit
    }
}
