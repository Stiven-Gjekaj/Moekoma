package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.BookOrigin
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/** The move of a book to another source of the same title. It needs no network, because the test gives the page of the new source. */
@RunWith(AndroidJUnit4::class)
class MoveDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun movesABookAndKeepsThePlaceAtTheChapterWithTheSameNumber() = runBlocking {
        val root = File(app.cacheDir, "move-test").apply { deleteRecursively() }
        val container = withContext(Dispatchers.Main) { AppContainer(app, root) }
        val list = UUID.randomUUID()
        val oldSource = InstalledSource(list, SourceInfo("en.old", "Old Scans", "English", "1", code = "", novels = false))
        val newSource = InstalledSource(list, SourceInfo("en.new", "New Scans", "English", "1", code = "", novels = false))
        val book = Book(
            title = "Quiet Tea", format = BookFormat.Series, fileName = "Old Scans", pageCount = 3, page = 1, position = 0.4,
            lastRead = Instant.ofEpochSecond(1_800_000_000), origin = BookOrigin(list, "en.old", "/old/tea"),
        )
        container.model.change { it.copy(books = it.books + book, installed = it.installed + oldSource + newSource) }
        val folder = container.model.folder(book).apply { mkdirs() }
        Novel("Quiet Tea", chapters = (1..3).map { Novel.Chapter("Ch. $it", "chapters/%05d.html".format(it), "/old/$it") }).save(folder)
        val oldChapter = File(folder, "chapters/00002.html").apply { parentFile!!.mkdirs(); writeText("old") }

        // The new source has a prologue first, so chapter 2 has the index 2 there.
        val details = OnlineDetails(
            OnlineSeries("/new/tea", "Quiet Tea"),
            chapters = listOf("Prologue", "Chapter 1", "Chapter 2", "Chapter 3", "Chapter 4").mapIndexed { i, title -> OnlineChapter("/new/$i", title) },
        )
        val moved = container.sources.move(book, newSource, details)

        assertEquals(BookOrigin(list, "en.new", "/new/tea"), moved.origin)
        assertEquals("New Scans", moved.fileName)
        assertEquals(5, moved.pageCount)
        assertEquals(2, moved.page)
        assertEquals(0.0, moved.position, 0.0)
        assertEquals((0..4).map { "/new/$it" }, Novel.load(folder).chapters.map { it.remote })
        assertFalse("The chapters of the old source leave the device.", oldChapter.exists())
        root.deleteRecursively()
        Unit
    }
}
