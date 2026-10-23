package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.MihonRepo
import io.github.stivengjekaj.moekoma.data.ChapterContent
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
 * Installs a real Mihon extension from the repository of Keiyoushi, loads it, and reads a series and the first page
 * of a chapter. It needs the network, so it runs only with the argument live=1:
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.live=1
 */
@RunWith(AndroidJUnit4::class)
class MihonLiveTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val http = OkHttpClient()

    @Test fun runsAnExtensionOfKeiyoushi() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "1")
        val store = MihonRepo.load("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json") { url ->
            http.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
        }
        val info = MihonRepo.sourceInfos(store).first { it.name == "MangaDex" && it.language == "en" }
        val host = MihonHost(app, http)
        host.install(info, store.signingKey)
        val source = host.load(InstalledSource(UUID.randomUUID(), info))
        val popular = source.popular(1)
        println("MangaDex popular: ${popular.items.take(3).map { it.title }}")
        assertTrue(popular.items.isNotEmpty())
        // Some titles of a search have no chapters on the source, such as a licensed title.
        val (series, details) = source.search("Frieren", 1).items.take(6).map { it to source.details(it) }.first { it.second.chapters.isNotEmpty() }
        println("${details.series.title}: ${details.chapters.size} chapters, first ${details.chapters.first().title}, ${details.genres.take(3)}")
        val content = source.content(details.chapters.last(), series) as ChapterContent.Pages
        val first = source.image(content.pages.first())
        println("The first page: ${first.code} ${first.body.contentType()} ${first.body.contentLength()} bytes")
        assertTrue(first.isSuccessful)
        first.close()
    }
}
