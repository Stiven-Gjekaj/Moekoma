package io.github.stivengjekaj.moekoma

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.AidokuColor
import io.github.stivengjekaj.moekoma.core.AidokuIndex
import io.github.stivengjekaj.moekoma.core.AidokuPathOp
import io.github.stivengjekaj.moekoma.core.AidokuRect
import io.github.stivengjekaj.moekoma.core.AidokuSource
import io.github.stivengjekaj.moekoma.core.HttpRequest
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.data.AidokuOnlineSource
import io.github.stivengjekaj.moekoma.data.AndroidAidokuPlatform
import io.github.stivengjekaj.moekoma.data.ChapterContent
import io.github.stivengjekaj.moekoma.data.networkFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** The Aidoku sources on the device: Chicory on Android, and the canvas and the web view of Android. */
@RunWith(AndroidJUnit4::class)
class AidokuDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val platform = AndroidAidokuPlatform(context)

    /** Reads the Aidoku source of scripts/test-server.py. It runs only with the argument server=1. */
    @Test fun readsTheSourceOfTheTestServer() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        val client = OkHttpClient()
        val fetcher = networkFetcher(client)
        val listUrl = "http://10.0.2.2:8765/aidoku/index.min.json"
        val info = AidokuIndex.parse(fetcher(HttpRequest(listUrl)).body, listUrl).first { it.id == "en.test" }
        val file = File(context.cacheDir, "test.aix").apply { writeBytes(fetcher(HttpRequest(info.code)).body) }
        val source = AidokuOnlineSource(InstalledSource(UUID.randomUUID(), info), AidokuSource.load(info, file, null, fetcher, platform), client, File(context.cacheDir, "aidoku-test"))
        val series = source.popular(1).items.single()
        assertEquals("Aidoku Sunrise", series.title)
        val details = source.details(series)
        assertEquals("Wasm Artist", details.author)
        val pages = (source.content(details.chapters.single(), details.series) as ChapterContent.Pages).pages
        assertEquals(2, pages.size)
        val bytes = source.image(pages[0]).use { it.body.bytes() }
        assertNotNull("The page draws.", BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertEquals("Year", source.filterOptions().single().title)
        source.source.close()
    }

    /** Measures a real source of Aidoku Community on the device. It runs only with the argument live=1. */
    @Test fun measuresARealSource() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "1")
        val fetcher = networkFetcher(OkHttpClient())
        val listUrl = "https://aidoku-community.github.io/sources/index.min.json"
        val info = AidokuIndex.parse(fetcher(HttpRequest(listUrl)).body, listUrl).first { it.id == "en.mangabat" }
        val file = File(context.cacheDir, "mangabat.aix").apply { writeBytes(fetcher(HttpRequest(info.code)).body) }
        var start = System.nanoTime()
        fun lap(): Long = ((System.nanoTime() - start) / 1_000_000).also { start = System.nanoTime() }
        val source = AidokuSource.load(info, file, null, fetcher, platform)
        val load = lap()
        val popular = source.popular(1)
        val list = lap()
        val details = source.details(popular.items[0])
        val title = lap()
        val found = source.search("Solo", 1)
        val search = lap()
        android.util.Log.i("AidokuSpeed", "load $load ms, popular $list ms, details $title ms (${details.chapters.size} chapters), search $search ms (${found.items.size})")
        source.close()
    }

    @Test fun copiesThePartsOfAnImageWithTheCanvasOfAndroid() {
        val redBlue = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, android.graphics.Color.RED)
            setPixel(1, 0, android.graphics.Color.BLUE)
        }
        val png = ByteArrayOutputStream().also { redBlue.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val image = platform.decode(png)!!
        val canvas = platform.canvas(2, 1)!!
        canvas.draw(image, AidokuRect(1f, 0f, 1f, 1f), AidokuRect(0f, 0f, 1f, 1f))
        canvas.draw(image, AidokuRect(0f, 0f, 1f, 1f), AidokuRect(1f, 0f, 1f, 1f))
        val drawn = canvas.image()!!.png()!!.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        // The blue pixel is on the left now.
        assertEquals(android.graphics.Color.BLUE, drawn.getPixel(0, 0))
        assertEquals(android.graphics.Color.RED, drawn.getPixel(1, 0))
        val square = platform.canvas(4, 4)!!
        square.fill(listOf(AidokuPathOp.Move(0f, 0f), AidokuPathOp.Line(4f, 0f), AidokuPathOp.Line(4f, 4f), AidokuPathOp.Line(0f, 4f), AidokuPathOp.Close), AidokuColor(0f, 255f, 0f, 1f))
        val green = square.image()!!.png()!!.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        assertEquals(android.graphics.Color.GREEN, green.getPixel(2, 2))
    }

    /** A source makes its web view on its own thread, and waits for the page and the scripts. */
    @Test fun readsAPageInAWebViewFromTheThreadOfASource() {
        var texts: List<String?> = emptyList()
        val thread = Thread {
            val view = platform.webView()
            view.loadHtml("<p id=\"x\">Tide</p>", "https://moekoma.test/")
            view.waitForLoad()
            texts = listOf(
                view.evaluate("document.getElementById('x').textContent", async = false),
                view.evaluate("return await Promise.resolve(40).then((n) => n + 2);", async = true),
            )
            view.close()
        }
        thread.start()
        thread.join(90_000)
        assertEquals(listOf("Tide", "42"), texts)
    }
}
