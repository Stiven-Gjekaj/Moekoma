package io.github.stivengjekaj.moekoma

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SourceOption
import io.github.stivengjekaj.moekoma.core.SuwayomiServer
import io.github.stivengjekaj.moekoma.core.SuwayomiSource
import io.github.stivengjekaj.moekoma.data.ChapterContent
import io.github.stivengjekaj.moekoma.data.SuwayomiOnlineSource
import io.github.stivengjekaj.moekoma.data.networkFetcher
import io.github.stivengjekaj.moekoma.data.withHeaders
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Reads a comic of the small Suwayomi server of scripts/test-server.py, with its password, on the device. It needs the
 * server on port 8765 of the computer, so it runs only with the argument server=1.
 */
@RunWith(AndroidJUnit4::class)
class SuwayomiServerTest {
    private val base = "http://10.0.2.2:8765"
    private val client = OkHttpClient()

    /** The password goes only to the host of the server, as the source list does it. */
    private val headers: (String) -> Map<String, String> = { url ->
        if (url.startsWith("$base/")) mapOf("Authorization" to Credentials.basic("asa", "morning")) else emptyMap()
    }

    private suspend fun source(): SuwayomiOnlineSource {
        val server = SuwayomiServer("$base/suwayomi", networkFetcher(client).withHeaders(headers))
        val info = server.sources().single()
        return SuwayomiOnlineSource(InstalledSource(UUID.randomUUID(), info), SuwayomiSource(info, server), client, headers)
    }

    @Before fun reset() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        client.newCall(Request.Builder().url("$base/control/reset").build()).execute().close()
    }

    @Test fun readsTheImagesOfAChapterWithThePasswordOfTheServer() = runBlocking {
        val source = source()
        assertEquals("Test Comics (EN)", source.info.name)
        val series = source.popular(1).items.single()
        assertEquals("$base/suwayomi/api/v1/page/0", series.cover)
        val details = source.details(series)
        assertEquals(listOf("Chapter 1"), details.chapters.map { it.title })
        val pages = (source.content(details.chapters[0], details.series) as ChapterContent.Pages).pages
        assertEquals(3, pages.size)
        // The image needs the password, so a page that draws proves that the image request has it.
        val bytes = source.image(pages[0]).use { it.body.bytes() }
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    @Test fun filtersTheListAndChangesAPreference() = runBlocking {
        val source = source()
        val genres = source.filterOptions().first { it.title == "Genres" }
        assertTrue(genres.kind is SourceOption.Kind.Multi)
        val filtered = source.browse(1, "", mapOf(genres.id to OptionValue.Multi(listOf("0"))))
        assertEquals("Comedy Strip", filtered.items.single().title)

        val stars = source.settingOptions().single()
        assertEquals(OptionValue.Toggle(false), source.settingValues()[stars.id])
        source.setSetting(stars.id, OptionValue.Toggle(true))
        assertEquals(OptionValue.Toggle(true), source.settingValues()[stars.id])
        assertEquals("★ Morning Strip", source.popular(1).items.single().title)
    }
}
