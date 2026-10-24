package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** A fetcher with fixed answers, by URL: the status, the type, and the text. */
fun fixedFetcher(pages: Map<String, Triple<Int, String, String>>, seen: MutableList<HttpRequest>? = null): Fetcher = { request ->
    seen?.add(request)
    val page = pages[request.url] ?: throw IOException("No host for ${request.url}")
    HttpResponse(page.first, request.url, mapOf("content-type" to page.second), page.third.toByteArray())
}

class JsRuntimeTest {
    @Serializable data class Answer(val ok: Boolean, val text: String, val chapters: Int, val type: String)
    @Serializable data class Link(val name: String, val path: String)
    @Serializable data class Parsed(val links: List<Link>, val year: Int, val url: String)

    @Test fun fetchesTextAndJsonWithHeadersAndABody() = runBlocking {
        val seen = mutableListOf<HttpRequest>()
        val runtime = JsRuntime.create(fixedFetcher(mapOf(
            "https://a.test/page" to Triple(200, "text/html; charset=utf-8", "<p>Morning</p>"),
            "https://a.test/api" to Triple(200, "application/json", """{"chapters": 3}"""),
        ), seen))
        runtime.evaluate("""
            globalThis.work = async function () {
              const page = await fetch('https://a.test/page', { headers: { 'X-Test': 'yes' } });
              const api = await (await fetch('https://a.test/api', { method: 'POST', body: JSON.stringify({ q: 1 }) })).json();
              return { ok: page.ok, text: await page.text(), chapters: api.chapters, type: page.headers.get('Content-Type') };
            };
        """.trimIndent())
        assertEquals(Answer(true, "<p>Morning</p>", 3, "text/html; charset=utf-8"), runtime.invoke(Answer.serializer(), "work"))
        assertEquals("yes", seen.first().headers["x-test"])
        assertEquals("POST", seen.last().method)
        assertEquals("""{"q":1}""", String(seen.last().body!!))
        runtime.close()
    }

    @Test fun parsesHtmlWithCheerioAndDatesWithDayjs() = runBlocking {
        val runtime = JsRuntime.create(fixedFetcher(emptyMap()))
        runtime.evaluate("""
            globalThis.work = function () {
              const $ = __libraries.cheerio.load('<ul><li class="c"><a href="/c/1">One</a></li><li class="c"><a href="/c/2">Two</a></li></ul>');
              const links = $('li.c a').map((i, el) => ({ name: $(el).text(), path: $(el).attr('href') })).get();
              return { links, year: __libraries.dayjs('2026-10-23').year(), url: new URL('/x?y=1', 'https://a.test/b/').href };
            };
        """.trimIndent())
        val answer = runtime.invoke(Parsed.serializer(), "work")
        assertEquals(listOf(Link("One", "/c/1"), Link("Two", "/c/2")), answer.links)
        assertEquals(2026, answer.year)
        assertEquals("https://a.test/x?y=1", answer.url)
        runtime.close()
    }

    @Test fun decodesTextInOtherEncodings() = runBlocking {
        val runtime = JsRuntime.create(fixedFetcher(emptyMap()))
        runtime.evaluate("globalThis.work = (bytes) => new TextDecoder('shift_jis').decode(new Uint8Array(bytes));")
        val bytes = "朝の文庫".toByteArray(charset("Shift_JIS"))
        assertEquals("朝の文庫", runtime.invoke(String.serializer(), "work", listOf(bytes)))
        runtime.close()
    }

    @Test fun waitsForATimer() = runBlocking {
        val runtime = JsRuntime.create(fixedFetcher(emptyMap()))
        runtime.evaluate("globalThis.work = () => new Promise((resolve) => setTimeout(() => resolve('later'), 20));")
        assertEquals("later", runtime.invoke(String.serializer(), "work"))
        runtime.close()
    }

    @Test fun givesTheMessageOfAnError() = runBlocking {
        val runtime = JsRuntime.create(fixedFetcher(emptyMap()))
        runtime.evaluate("globalThis.work = async () => { throw new Error('No novel here'); };")
        val error = runCatching { runtime.invoke("work") }.exceptionOrNull()
        assertEquals("No novel here", error?.message)
        // A request that fails rejects with a TypeError, as fetch does in a browser.
        val name = runtime.invoke(String.serializer(), "async () => { try { await fetch('https://nowhere.test/'); return 'no error'; } catch (e) { return e.name; } }")
        assertEquals("TypeError", name)
        runtime.close()
    }

    @Test fun stopsAScriptThatNeverAnswers() = runBlocking {
        val runtime = JsRuntime.create(fixedFetcher(emptyMap()))
        runtime.timeoutMillis = 200
        val error = runCatching { runtime.invoke("() => new Promise(() => {})") }.exceptionOrNull()
        assertEquals("The source did not answer in time.", error?.message)
        runtime.close()
    }

    @Test fun givesTheCookiesOfAnAnswer() = runBlocking {
        val fetcher: Fetcher = { request ->
            HttpResponse(200, request.url, emptyMap(), ByteArray(0), listOf("a=1; Path=/; Max-Age=60", "b=2; Domain=.site.test; Path=/x"))
        }
        val runtime = JsRuntime.create(fetcher)
        val text = runtime.invoke(String.serializer(), "async () => (await fetch('https://www.site.test/')).setCookies.map((c) => c.name + '=' + c.value + '@' + c.domain + c.path + (c.expires ? '+' : '')).join(' ')")
        // Two Set-Cookie headers give two cookies. A cookie with no domain has the host of the answer.
        assertEquals("a=1@www.site.test/+ b=2@site.test/x", text)
        runtime.close()
    }

    /** Many contexts start at the same time, as when a search asks many JavaScript sources. */
    @Test fun startsManyContextsAtTheSameTime() = runBlocking {
        val runtimes = (1..12).map { async(kotlinx.coroutines.Dispatchers.Default) { JsRuntime.create(fixedFetcher(emptyMap())) } }.map { it.await() }
        val answers = runtimes.mapIndexed { index, runtime -> async(kotlinx.coroutines.Dispatchers.Default) { runtime.invoke("() => $index * 2") } }.map { it.await() }
        assertEquals((0 until 12).map { (it * 2).toString() }, answers)
        runtimes.forEach { it.close() }
    }
}
