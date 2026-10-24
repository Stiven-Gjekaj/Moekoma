package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.ui.NovelReaderScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.UUID

/** A chapter of a web novel that opens downloads the next chapter too, so that the next chapter opens with no wait. */
@RunWith(AndroidJUnit4::class)
class NovelPrefetchUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "prefetch-${UUID.randomUUID()}")

    @After fun clean() {
        root.deleteRecursively()
    }

    @Test fun downloadsTheNextChapterAfterTheChapterThatOpens() {
        val container = AppContainer(app, root)
        val book = Book(title = "Night Tale", format = BookFormat.Novel, fileName = "Night", pageCount = 3)
        val folder = container.model.folder(book).apply { mkdirs() }
        val chapters = (1..3).map { Novel.Chapter("Part $it", "chapters/%05d.html".format(it), remote = "https://novel.test/$it") }
        Novel("Night Tale", chapters = chapters).save(folder)
        container.model.change { it.copy(books = it.books + book) }
        // The fake source writes the file of each chapter that it gets.
        val asked = Collections.synchronizedList(mutableListOf<Int>())
        val fetch: suspend (Int) -> Unit = { index ->
            asked += index
            File(folder, chapters[index].path).apply { parentFile?.mkdirs() }
                .writeText("<html><body><h2>Part ${index + 1}</h2><p>Words of part ${index + 1}.</p></body></html>")
        }

        compose.setContent { NovelReaderScreen(container.model, container.settings, book.id, fetchChapter = fetch) {} }
        compose.waitUntil(10_000) { File(folder, chapters[1].path).isFile }
        assertEquals(listOf(0, 1), asked.toList())
        // The chapter after the next does not download yet.
        assertEquals(false, File(folder, chapters[2].path).isFile)
    }
}
