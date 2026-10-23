package io.github.stivengjekaj.moekoma

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stivengjekaj.moekoma.core.HttpResponse
import io.github.stivengjekaj.moekoma.core.JsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QuickJS on Android: the libraries of the JavaScript sources run, and many contexts start at the same time, as when a
 * search asks many LNReader plugins. Android checks each JNI reference in a debug build, so a race of the start stops
 * the test.
 */
@RunWith(AndroidJUnit4::class)
class JsRuntimeDeviceTest {
    @Test fun startsManyContextsAtTheSameTimeAndRunsTheLibraries() = runBlocking {
        val fetcher: suspend (io.github.stivengjekaj.moekoma.core.HttpRequest) -> HttpResponse = { request ->
            HttpResponse(200, request.url, mapOf("content-type" to "text/html"), "<ul><li>One</li><li>Two</li></ul>".toByteArray())
        }
        val runtimes = (1..16).map { async(Dispatchers.Default) { JsRuntime.create(fetcher) } }.awaitAll()
        val answers = runtimes.mapIndexed { index, runtime ->
            async(Dispatchers.Default) {
                runtime.invoke("async () => { const $ = __libraries.cheerio.load(await (await fetch('https://a.test/')).text()); return $('li').length + $index; }")
            }
        }.awaitAll()
        assertEquals((0 until 16).map { (2 + it).toString() }, answers)
        runtimes.forEach { it.close() }
    }
}
