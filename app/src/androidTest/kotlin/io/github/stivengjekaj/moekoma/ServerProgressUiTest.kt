package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.UUID

/** A stream of a Komga server opens at the last page that the server keeps, and each page goes back to it. It runs only with server=1. */
@RunWith(AndroidJUnit4::class)
class ServerProgressUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "komga-${UUID.randomUUID()}")

    private fun server(path: String) = URL("http://10.0.2.2:8765/$path").openStream().use { String(it.readBytes()) }

    private fun waitForText(text: String, millis: Long = 15_000) =
        compose.waitUntil(millis) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @After fun clean() { root.deleteRecursively() }

    @Test fun opensTheLastPageAndSendsEachPageToKomga() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        server("control/reset")
        server("control/komga?page=3")
        val container = AppContainer(app, root)
        container.sources.save("http://asa:morning@10.0.2.2:8765/komga/opds/v1.2/catalog")
        compose.setContent { MoekomaRoot(container) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("1 source").performClick()
        // The row shows the user name and the address of the line.
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("asa at", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("asa at", substring = true).performClick()
        waitForText("Komga Dawn")
        compose.onNodeWithText("Komga Dawn").performClick()
        compose.onNodeWithText("Read now").performClick()

        // The server keeps page 3, from 1, so the reader opens at page 3.
        waitForText("3 of 4")
        compose.onNodeWithContentDescription("Page 3").performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        waitForText("4 of 4")
        compose.waitUntil(10_000) { JSONObject(server("komga/state")).getInt("page") == 4 }
        assertTrue("The last page finishes the book.", JSONObject(server("komga/state")).getBoolean("completed"))
    }
}
