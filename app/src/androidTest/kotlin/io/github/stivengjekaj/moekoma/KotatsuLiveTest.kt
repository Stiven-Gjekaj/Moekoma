package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.data.ChapterContent
import io.github.stivengjekaj.moekoma.data.KotatsuHost
import io.github.stivengjekaj.moekoma.data.MihonHost
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Loads the plugin of the Kotatsu parsers of Gekkoushi/plugin, and reads a series and the first page of a chapter
 * of MangaPill. It needs the network, so it runs only with the argument live=1.
 */
@RunWith(AndroidJUnit4::class)
class KotatsuLiveTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val http = OkHttpClient()

    @Test fun runsAParserOfThePlugin() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "1")
        MihonHost.setUpInjekt(app)
        val host = KotatsuHost(app, http)
        val jar = host.jar("https://github.com/Gekkoushi/plugin") { url -> http.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() } }
        val infos = host.sources(jar)
        println("Kotatsu plugin ${jar.version}: ${infos.size} sources, ${infos.count { it.language == "en" }} in English")
        assertTrue(infos.size > 500)
        val info = infos.first { it.id == "MANGAPILL" }
        val source = host.load(InstalledSource(UUID.randomUUID(), info))
        val popular = source.popular(1)
        println("MangaPill popular: ${popular.items.take(3).map { it.title }}")
        assertTrue(popular.items.isNotEmpty())
        val series = source.search("Frieren", 1).items.first()
        val details = source.details(series)
        println("${details.series.title}: ${details.chapters.size} chapters, first ${details.chapters.firstOrNull()?.title}")
        assertTrue(details.chapters.isNotEmpty())
        val content = source.content(details.chapters.first(), series) as ChapterContent.Pages
        source.image(content.pages.first()).use { first ->
            println("The first page: ${first.code} ${first.body.contentType()} ${first.body.contentLength()} bytes")
            assertTrue(first.isSuccessful)
        }
    }
}
