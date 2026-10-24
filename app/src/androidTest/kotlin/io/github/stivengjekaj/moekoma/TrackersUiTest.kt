package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.TrackerKind
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.UUID

/** The sign-in to AniList, the link of a book, and its progress, with the fake trackers of scripts/test-server.py. It runs only with server=1. */
@RunWith(AndroidJUnit4::class)
class TrackersUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "trackers-${UUID.randomUUID()}")
    private lateinit var container: AppContainer

    private fun server(path: String) = URL("http://10.0.2.2:8765/$path").openStream().use { String(it.readBytes()) }

    @After fun clean() {
        if (::container.isInitialized) { container.trackers.signOut(TrackerKind.AniList); container.trackers.setClientId(TrackerKind.AniList, "") }
        root.deleteRecursively()
    }

    @Test fun signsInLinksABookAndSendsItsProgress() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        server("control/reset")
        container = AppContainer(app, root)
        container.trackers.base = "http://10.0.2.2:8765/trackers"
        container.trackers.signOut(TrackerKind.AniList)
        val book = Book(title = "Aidoku Sunrise", format = BookFormat.Comic, fileName = "s.cbz", pageCount = 3)
        container.model.change { it.copy(books = listOf(book)) }
        compose.setContent { MoekomaRoot(container) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Trackers").performScrollTo().performClick()
        compose.onNodeWithText("AniList client ID").performTextInput("1")
        compose.onNodeWithText("Sign in to AniList").performClick()
        // The page of the sign-in goes back to moekoma://anilist with the token at once.
        compose.waitUntil(15_000) { TrackerKind.AniList in container.trackers.accounts.value }

        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Aidoku Sunrise").performTouchInput { longClick() }
        compose.onNodeWithText("Track").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("1 chapter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 chapter").performClick()
        compose.waitUntil(5_000) { container.model.library.value.book(book.id)!!.tracking.singleOrNull()?.id == 101 }

        // A new place sends the chapters that the user read.
        container.model.update(book.id) { it.read(2) }
        compose.waitUntil(10_000) { JSONObject(server("trackers/state")).has("anilist") }
        val sent = JSONObject(server("trackers/state")).getJSONObject("anilist")
        assertEquals(101, sent.getInt("id"))
        assertEquals("The last page finishes the comic.", 3, sent.getInt("progress"))
        assertEquals("COMPLETED", sent.getString("status"))
    }
}
