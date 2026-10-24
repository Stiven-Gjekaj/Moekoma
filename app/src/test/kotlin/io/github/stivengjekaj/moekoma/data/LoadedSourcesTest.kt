package io.github.stivengjekaj.moekoma.data

import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlinePage
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.SourceInfo
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LoadedSourcesTest {
    private class Fake(id: String) : OnlineSource {
        @Volatile var closed = false
        override val info = SourceInfo(id, id, "en", "1", code = "", novels = false)
        override val canShowLatest = false
        override suspend fun popular(page: Int) = OnlinePage(emptyList(), false)
        override suspend fun latest(page: Int) = OnlinePage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = OnlinePage(emptyList(), false)
        override suspend fun details(series: OnlineSeries) = OnlineDetails(series)
        override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent = ChapterContent.Html("")
        override suspend fun image(image: RemoteImage): Response = throw UnsupportedOperationException()
        override fun closer() = AutoCloseable { closed = true }
    }

    @Test fun closesASourceThatLeavesTheMemory() {
        LoadedSources.closeDelayMillis = 0
        val fakes = (0..24).map { Fake("test-$it") }
        fakes.forEach { LoadedSources.set(it.info.id, it) }
        // The cache keeps 24 sources, so the first source leaves it and closes.
        assertNull(LoadedSources.get("test-0"))
        assertSame(fakes[24], LoadedSources.get("test-24"))
        val until = System.currentTimeMillis() + 5_000
        while (!fakes[0].closed && System.currentTimeMillis() < until) Thread.sleep(10)
        assertEquals(listOf(true) + List(24) { false }, fakes.map { it.closed })
        // A source that the app removes closes too.
        LoadedSources.remove("test-24")
        while (!fakes[24].closed && System.currentTimeMillis() < until) Thread.sleep(10)
        assertEquals(true, fakes[24].closed)
        (1..23).forEach { LoadedSources.remove("test-$it") }
    }
}
