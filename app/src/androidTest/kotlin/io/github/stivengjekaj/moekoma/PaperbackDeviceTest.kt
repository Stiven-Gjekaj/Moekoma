package io.github.stivengjekaj.moekoma

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.FormInput
import io.github.stivengjekaj.moekoma.core.HttpRequest
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.PaperbackExtension
import io.github.stivengjekaj.moekoma.core.PaperbackIndex
import io.github.stivengjekaj.moekoma.data.ChapterContent
import io.github.stivengjekaj.moekoma.data.PaperbackSource
import io.github.stivengjekaj.moekoma.data.PaperbackWebView
import io.github.stivengjekaj.moekoma.data.networkFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The Paperback extensions on the device: the extension of the test server, and the web view of Android. */
@RunWith(AndroidJUnit4::class)
class PaperbackDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Reads the extension of scripts/test-server.py and changes a setting. It runs only with the argument server=1. */
    @Test fun readsTheExtensionOfTheTestServer() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        val client = OkHttpClient()
        val fetcher = networkFetcher(client)
        val repo = "http://10.0.2.2:8765/paperback/versioning.json"
        val info = PaperbackIndex.parse(fetcher(HttpRequest(repo)).body, repo).single()
        val state = File(context.cacheDir, "paperback-${UUID.randomUUID()}.json")
        val extension = PaperbackExtension.load(info, String(fetcher(HttpRequest(info.code)).body), state, "Test", fetcher)
        val source = PaperbackSource(InstalledSource(UUID.randomUUID(), info), extension, client)
        val series = source.popular(1).items.first()
        val details = source.details(series)
        assertEquals("Paper Moon", details.series.title)
        val pages = (source.content(details.chapters.single(), details.series) as ChapterContent.Pages).pages
        assertEquals(2, pages.size)
        // The page needs the referer of the interceptor, so a page that draws proves that the request has it.
        val bytes = source.image(pages[0]).use { it.body.bytes() }
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val root = source.settingsForm()
        val loud = root.sections.flatMap { it.rows }.first { it.id == "loud" }
        source.formAction(root.id, loud.id, FormInput.Toggle(true))
        assertEquals(series.title.uppercase(), source.popular(1).items.first().title)
        extension.close()
        assertEquals(true, state.delete())
    }

    /** A page with a value that it sets after its load, and a cookie of the extension. */
    @Test fun runsAPageInTheWebViewOfAndroid() = runBlocking {
        val html = "<html><head><script>setTimeout(function () { window.__answer = 'page-' + document.cookie; }, 200);</script></head><body></body></html>"
        val input = JSONObject().put("html", html).put("baseUrl", "https://quiet.test/read").put("inject", "return window.__answer")
            .put("cookies", org.json.JSONArray().put(JSONObject().put("name", "seen").put("value", "yes").put("domain", "quiet.test").put("path", "/")))
        val answer = JSONObject(PaperbackWebView(context).run(input.toString()))
        assertEquals("page-seen=yes", answer.getString("result"))
        assertEquals("seen", answer.getJSONArray("cookies").getJSONObject(0).getString("name"))
    }
}
