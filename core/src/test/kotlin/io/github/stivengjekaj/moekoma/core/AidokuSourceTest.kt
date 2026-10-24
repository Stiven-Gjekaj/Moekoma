package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class AidokuSourceTest {
    @Test fun readsTheListOfSourcesWithItsRelativeLinks() {
        val json = """{"name":"Community","sources":[{"id":"en.guya","name":"Guya","version":2,"iconURL":"icons/en.guya-v2.png","downloadURL":"sources/en.guya-v2.aix","languages":["en"],"contentRating":0},{"id":"multi.x","name":"X","version":1,"downloadURL":"sources/x.aix","languages":["en","ja"],"contentRating":2},{"id":"en.novelbuddy","name":"NovelBuddy","version":8,"downloadURL":"sources/n.aix","languages":["en"]}]}"""
        val list = AidokuIndex.parse(json.toByteArray(), "https://aidoku.test/sources/index.min.json")
        assertEquals("https://aidoku.test/sources/sources/en.guya-v2.aix", list[0].code)
        assertEquals("https://aidoku.test/sources/icons/en.guya-v2.png", list[0].icon)
        assertEquals("Multi", list[1].language)
        assertTrue(list[1].adult)
        assertTrue(list[2].novels)
        assertFalse(list[0].novels)
    }

    @Test fun turnsTheMarkdownOfANovelIntoHtml() {
        assertEquals("<h2>One</h2>\n<p>The <b>sun</b> came <i>up</i> &amp; &lt;set&gt;.</p>", AidokuSource.html("# One\n\nThe **sun** came *up* & <set>."))
    }

    @Test fun encodesTheFiltersOfASearch() {
        val filters = AidokuOptions.filters("""[{"type":"note","text":"Hi"},{"type":"text","id":"author","title":"Author"},{"type":"check","id":"done","name":"Done"},{"type":"select","id":"status","title":"Status","options":["Any","Ongoing"],"ids":["","1"]},{"type":"range","id":"year","title":"Year","min":1990,"max":2030}]""".toByteArray())
        assertEquals(listOf("author", "done", "status", "year"), filters.map { it.id })
        assertEquals(SourceOption.Kind.Range(1990.0, 2030.0, false), filters[3].kind)
        // An empty text and an unchanged check do not go. The status goes with its value.
        val data = AidokuOptions.encode(mapOf("status" to OptionValue.Select("1"), "year" to OptionValue.Range(2015.0, null)), filters)
        val expected = PostcardWriter.write {
            unsigned(2)
            unsigned(3); string("status"); string("1")
            unsigned(5); string("year"); option(2015f) { f32(it) }; option<Float>(null) { f32(it) }
        }
        assertArrayEquals(expected, data)
    }

    @Test fun readsTheFiltersOfGetFilters() {
        val data = PostcardWriter.write {
            unsigned(1)
            option("genre") { string(it) }; option("Genre") { string(it) }; option<Boolean>(null) { bool(it) }
            string("multi-select")
            option(true) { bool(it) }; option(true) { bool(it) }; option<Boolean>(null) { bool(it) }
            list(listOf("Action", "Drama")) { string(it) }
            option(listOf("a", "d")) { list(it) { s -> string(s) } }
            option(listOf("d")) { list(it) { s -> string(s) } }
            option<List<String>>(null) { list(it) { s -> string(s) } }
        }
        val option = AidokuOptions.filtersOfPostcard(data).single()
        assertEquals(SourceOption.Kind.Multi(listOf(SourceOption.Choice("Action", "a"), SourceOption.Choice("Drama", "d")), true), option.kind)
        assertEquals(OptionValue.Multi(listOf("d")), option.initial)
    }

    @Test fun keepsASettingInTheFormOfTheDefaults() {
        val settings = AidokuOptions.settings("""[{"type":"group","items":[{"type":"segment","key":"mode","title":"Mode","options":["A","B"],"default":1},{"type":"switch","key":"hd","title":"HD","default":true}]}]""".toByteArray())
        assertEquals(listOf("mode", "hd"), settings.map { it.id })
        val bytes = AidokuOptions.encodeSetting(OptionValue.Select("0"), settings[0])
        assertEquals(OptionValue.Select("0"), AidokuOptions.decodeSetting(bytes, settings[0]))
        assertEquals(OptionValue.Toggle(false), AidokuOptions.decodeSetting(AidokuOptions.encodeSetting(OptionValue.Toggle(false), settings[1]), settings[1]))
    }

    private val live = System.getenv("MOEKOMA_LIVE") == "1"

    private val web: Fetcher = { request ->
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36")
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        request.body?.let { body -> connection.doOutput = true; connection.outputStream.use { it.write(body) } }
        val status = connection.responseCode
        val body = (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
        HttpResponse(status, connection.url.toString(), connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(", ") }, body)
    }

    private suspend fun real(id: String, platform: AidokuPlatform? = null): AidokuSource {
        val listUrl = "https://aidoku-community.github.io/sources/index.min.json"
        val info = AidokuIndex.parse(web(HttpRequest(listUrl)).body, listUrl).first { it.id == id }
        val file = kotlin.io.path.createTempFile(id, ".aix").toFile().apply { writeBytes(web(HttpRequest(info.code)).body); deleteOnExit() }
        return AidokuSource.load(info, file, null, web, platform)
    }

    /** Runs real sources of Aidoku Community from the web. It runs only with MOEKOMA_LIVE=1. */
    @Test fun readsRealSources() = runBlocking {
        assumeTrue(live)
        for (id in listOf("en.guya", "en.mangabat")) {
            val source = real(id)
            val popular = source.popular(1)
            assertTrue("$id has titles", popular.items.isNotEmpty())
            val details = source.details(popular.items[0])
            assertTrue("$id has chapters", details.chapters.isNotEmpty())
            val pages = (source.content(details.chapters.last(), details.series) as AidokuContent.Pages).pages
            assertTrue("$id has pages", pages.isNotEmpty())
            val found = source.search(popular.items[0].title.take(6), 1)
            println("LIVE $id: ${popular.items.size} series; ${details.series.title}, ${details.chapters.size} chapters, ${pages.size} pages; search ${found.items.size}")
            source.close()
        }
    }

    /** MANGA Plus encrypts its pages. The source decrypts each one with the canvas module. It runs only with MOEKOMA_LIVE=1. */
    @Test fun drawsThePagesOfMangaPlus() = runBlocking {
        assumeTrue(live)
        val source = real("multi.mangaplus", AwtPlatform())
        val details = source.details(source.popular(1).items[0])
        val pages = (source.content(details.chapters.last(), details.series) as AidokuContent.Pages).pages
        val first = pages.first() as AidokuPage.Drawn
        // The decrypted page is a JPEG.
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), first.png.copyOf(3))
        println("LIVE mangaplus: ${details.series.title}, ${pages.size} pages, first ${first.png.size} bytes")
        source.close()
    }

    /** Searches a real source with a range filter. It runs only with MOEKOMA_LIVE=1. */
    @Test fun searchesARealSourceWithARange() = runBlocking {
        assumeTrue(live)
        val source = real("multi.mangafire")
        val range = source.filterOptions().first { it.kind is SourceOption.Kind.Range }
        val page = source.browse(1, "", mapOf(range.id to OptionValue.Range(2015.0, 2024.0)))
        assertTrue("The search with a range gives titles.", page.items.isNotEmpty())
        val old = source.browse(1, "", mapOf(range.id to OptionValue.Range(1990.0, 1995.0)))
        assertNotEquals("The source reads the range.", page.items.map { it.id }, old.items.map { it.id })
        println("LIVE mangafire range: ${page.items.take(3).map { it.title }} / ${old.items.take(3).map { it.title }}")
        source.close()
    }

    /** Reads the filters that a real source makes in code. It runs only with MOEKOMA_LIVE=1. */
    @Test fun readsTheDynamicFiltersOfARealSource() = runBlocking {
        assumeTrue(live)
        val source = real("multi.kagane")
        val filters = source.filterOptions()
        assertTrue("The source gives its filters with get_filters.", filters.isNotEmpty())
        val choice = filters.firstNotNullOfOrNull { filter ->
            when (val kind = filter.kind) {
                is SourceOption.Kind.Select -> if (kind.choices.size > 1) filter.id to OptionValue.Select(kind.choices[1].value) else null
                is SourceOption.Kind.Sort -> if (kind.choices.size > 1) filter.id to OptionValue.Sort(1, false) else null
                else -> null
            }
        }
        val page = source.browse(1, "", listOfNotNull(choice).toMap())
        println("LIVE kagane filters ${filters.map { it.title }} chosen $choice items ${page.items.size}")
        assertTrue("The search with a filter gives titles.", page.items.isNotEmpty())
        source.close()
    }
}
