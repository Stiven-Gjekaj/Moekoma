package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URI

/** A plugin in the form that the LNReader build gives: CommonJS, with require() and exports.default. The same as in Asabunko. */
private val quietPlugin = """
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var fetch_1 = require("@libs/fetch");
var cheerio_1 = require("cheerio");
var novelStatus_1 = require("@libs/novelStatus");
var storage_1 = require("@libs/storage");
var Quiet = /** @class */ (function () {
  function Quiet() {
    this.id = "quiet"; this.name = "Quiet Novels"; this.site = "https://quiet.test/"; this.version = "1.0.0";
    this.imageRequestInit = { headers: { Referer: "https://quiet.test/" } };
    this.filters = { sort: { type: "Picker", label: "Sort", value: "popular", options: [] } };
  }
  Quiet.prototype.popularNovels = async function (page, options) {
    storage_1.storage.set("last", page);
    var kind = options.showLatestNovels ? "latest" : options.filters.sort.value;
    var html = await (0, fetch_1.fetchText)(this.site + "list/" + kind + "?page=" + page);
    var $ = (0, cheerio_1.load)(html);
    return $(".novel").map(function (i, el) {
      return { name: $(el).find("a").text(), path: $(el).find("a").attr("href"), cover: $(el).find("img").attr("src") };
    }).get();
  };
  Quiet.prototype.searchNovels = async function (term, page) {
    var res = await (0, fetch_1.fetchApi)(this.site + "search?q=" + encodeURIComponent(term));
    var data = await res.json();
    return data.results.map(function (r) { return { name: r.title, path: r.slug }; });
  };
  Quiet.prototype.parseNovel = async function (path) {
    var $ = (0, cheerio_1.load)(await (0, fetch_1.fetchText)(this.site + path));
    return {
      path: path, name: $("h1").text(), cover: $(".cover").attr("src"), summary: $(".summary").text().trim(),
      author: $(".author").text(), status: novelStatus_1.NovelStatus.Ongoing, genres: "Slice of Life, Drama",
      chapters: $(".chapter a").map(function (i, el) { return { name: $(el).text(), path: $(el).attr("href"), releaseTime: "2026-10-0" + (i + 1) }; }).get()
    };
  };
  Quiet.prototype.parseChapter = async function (path) {
    var $ = (0, cheerio_1.load)(await (0, fetch_1.fetchText)(this.site + path));
    return $("#content").html();
  };
  return Quiet;
}());
exports.default = new Quiet();
"""

private val site = mapOf(
    "https://quiet.test/list/popular?page=1" to Triple(200, "text/html", """<div class="novel"><img src="https://quiet.test/c/1.jpg"><a href="novel/dawn">Dawn Tea</a></div><div class="novel"><img src="/c/2.jpg"><a href="novel/rain">Rain Shop</a></div>"""),
    "https://quiet.test/list/latest?page=1" to Triple(200, "text/html", """<div class="novel"><a href="novel/new">New One</a></div>"""),
    "https://quiet.test/list/popular?page=2" to Triple(200, "text/html", "<p>No more</p>"),
    "https://quiet.test/search?q=tea%20time" to Triple(200, "application/json", """{"results": [{"title": "Dawn Tea", "slug": "novel/dawn"}]}"""),
    "https://quiet.test/novel/dawn" to Triple(200, "text/html", """
        <h1>Dawn Tea</h1><img class="cover" src="https://quiet.test/c/1.jpg"><p class="summary"> A shop opens at dawn. </p>
        <span class="author">Mina</span><ul><li class="chapter"><a href="novel/dawn/1">First Cup</a></li><li class="chapter"><a href="novel/dawn/2">Second Cup</a></li></ul>
    """.trimIndent()),
    "https://quiet.test/novel/dawn/1" to Triple(200, "text/html; charset=utf-8", """<div id="content"><p>The kettle sang.</p></div>"""),
)

private val optionsPlugin = """
const { FilterTypes } = require('@libs/filterInputs');
const { storage } = require('@libs/storage');
exports.default = {
  id: 'opts', name: 'Options', site: 'https://opts.test/', version: '1.0.0',
  filters: {
    order: { label: 'Order', type: FilterTypes.Picker, value: 'new', options: [{ label: 'New', value: 'new' }, { label: 'Top', value: 'top' }] },
    done: { label: 'Completed', type: FilterTypes.Switch, value: false },
    tags: { label: 'Tags', type: FilterTypes.ExcludableCheckboxGroup, value: { include: [], exclude: [] }, options: [{ label: 'Sea', value: 'sea' }] },
  },
  pluginSettings: { mirror: { label: 'Mirror', value: 'a', type: 'Select', options: [{ label: 'A', value: 'a' }, { label: 'B', value: 'b' }] } },
  async popularNovels(page, { filters }) {
    const name = [filters.order.value, String(filters.done.value), filters.tags.value.include.join('+'), filters.tags.value.exclude.join('+'), storage.get('mirror') || 'none'].join('|');
    return [{ name, path: 'n/1' }];
  },
  async searchNovels(q) { return [{ name: 'search ' + q, path: 's/1' }]; },
  async parseNovel(path) { return { path, name: 'N', chapters: [] }; },
  async parseChapter() { return ''; },
};
"""

class LnReaderTest {
    private fun info(id: String) = SourceInfo(id = id, name = id, language = "English", version = "1.0.0", code = "https://x.test/$id.js", novels = true)

    private suspend fun load() = LnReaderPlugin.load(info("quiet"), quietPlugin, fixedFetcher(site))

    @Test fun listsThePopularAndTheLatestNovels() = runBlocking {
        val source = load()
        val popular = source.popular(1)
        assertEquals(listOf(
            OnlineSeries("novel/dawn", "Dawn Tea", "https://quiet.test/c/1.jpg"),
            OnlineSeries("novel/rain", "Rain Shop", "https://quiet.test/c/2.jpg"),
        ), popular.items)
        assertTrue(popular.hasMore)
        assertFalse(source.popular(2).hasMore)
        assertEquals(listOf("New One"), source.latest(1).items.map { it.title })
        source.close()
    }

    @Test fun searches() = runBlocking {
        val source = load()
        assertEquals(listOf("novel/dawn"), source.search("tea time", 1).items.map { it.id })
        source.close()
    }

    @Test fun readsANovelAndAChapter() = runBlocking {
        val source = load()
        val details = source.details(OnlineSeries("novel/dawn", "Dawn"))
        assertEquals("Dawn Tea", details.series.title)
        assertEquals("Mina", details.author)
        assertEquals("A shop opens at dawn.", details.summary)
        assertEquals("Ongoing", details.status)
        assertEquals(listOf("Slice of Life", "Drama"), details.genres)
        assertEquals(listOf(
            OnlineChapter("novel/dawn/1", "First Cup", date = "2026-10-01"),
            OnlineChapter("novel/dawn/2", "Second Cup", date = "2026-10-02"),
        ), details.chapters)
        assertEquals("<p>The kettle sang.</p>", source.chapter(details.chapters[0].id))
        assertEquals("https://quiet.test/", source.imageHeaders["Referer"])
        source.close()
    }

    @Test fun givesTheConstantsOfTheAppToAPlugin() = runBlocking {
        val code = """
            const { defaultCover } = require('@/types/constants');
            exports.default = { id: 'c', name: 'C', site: 'https://c.test/', version: '1',
              async popularNovels() { return [{ name: 'One', path: 'n/1', cover: defaultCover }]; },
              async searchNovels() { return []; }, async parseNovel(path) { return { path, name: 'One', chapters: [] }; }, async parseChapter() { return ''; } };
        """.trimIndent()
        val source = LnReaderPlugin.load(info("c"), code, fixedFetcher(emptyMap()))
        assertTrue(source.popular(1).items.first().cover!!.contains("coverNotAvailable"))
        source.close()
    }

    @Test fun callsAGrpcSourceWithProtobuf() = runBlocking {
        val code = """
            const { fetchProto } = require('@libs/fetch');
            const proto = 'syntax = "proto3"; message Ask { int32 id = 1; } message Reply { string name = 1; int64 count = 2; }';
            exports.default = { id: 'p', name: 'P', site: 'https://p.test/', version: '1',
              async popularNovels() {
                const reply = await fetchProto({ proto, requestType: 'Ask', responseType: 'Reply', requestData: { id: 7 } }, 'https://p.test/api.Ask');
                return [{ name: reply.name + ' ' + reply.count, path: 'n/1' }];
              },
              async searchNovels() { return []; }, async parseNovel(path) { return { path, name: 'N', chapters: [] }; }, async parseChapter() { return ''; } };
        """.trimIndent()
        val seen = mutableListOf<HttpRequest>()
        val fetcher: Fetcher = { request ->
            seen += request
            // Reply { name: "Tide", count: 300 } in a gRPC frame.
            val message = byteArrayOf(0x0A, 0x04) + "Tide".toByteArray() + byteArrayOf(0x10, 0xAC.toByte(), 0x02)
            HttpResponse(200, request.url, mapOf("content-type" to "application/grpc-web+proto"), byteArrayOf(0, 0, 0, 0, message.size.toByte()) + message)
        }
        val source = LnReaderPlugin.load(info("p"), code, fetcher)
        assertEquals("Tide 300", source.popular(1).items.first().title)
        assertEquals("POST", seen.first().method)
        assertEquals("The request is Ask { id: 7 } in a frame.", listOf(0, 0, 0, 0, 2, 8, 7), seen.first().body!!.map { it.toInt() })
        source.close()
    }

    @Test fun refusesAFileThatIsNotAPlugin() = runBlocking {
        val error = runCatching { LnReaderPlugin.load(info("x"), "exports.default = {};", fixedFetcher(emptyMap())) }.exceptionOrNull()
        assertEquals("The file is not an LNReader plugin.", error?.message)
    }

    @Test fun readsTheIndexWithTheBracketsOfItsUrls() {
        val json = """[{"id":"anf","name":"AllNovelFull","site":"https://a.test/","lang":"\u200eEnglish","version":"1.0.0","url":"https://raw.test/AllNovelFull[readnovelfull].js","iconUrl":"https://raw.test/i.png"}]"""
        val list = LnReaderIndex.parse(json.toByteArray())
        assertEquals("English", list.first().language)
        assertEquals("https://raw.test/AllNovelFull%5Breadnovelfull%5D.js", list.first().code)
    }

    // Filters and settings

    private suspend fun options(settings: File?, code: String = optionsPlugin) =
        LnReaderPlugin.load(info("opts"), code, { throw IOException("No network") }, settings)

    @Test fun aPluginWithASearchFilterSearchesWithTheOtherFilters() = runBlocking {
        // A plugin in the form of WTR-LAB: its list reads the words of a search from the text filter "search".
        val code = optionsPlugin
            .replace("filters: {\n", "filters: {\n    search: { label: 'Search', type: FilterTypes.TextInput, value: '' },\n")
            .replace("const name = [filters.order.value,", "const name = ['words ' + filters.search.value, filters.order.value,")
        val source = options(null, code)
        assertEquals("The test changed the plugin.", "search", source.filterOptions().map { it.id }.first())
        assertEquals("words tide|top|false|||none", source.browse(1, "tide", mapOf("order" to OptionValue.Select("top"))).items.first().title)
        assertEquals("words |top|false|||none", source.browse(1, "", mapOf("order" to OptionValue.Select("top"))).items.first().title)
        source.close()
    }

    @Test fun anLnReaderPluginGetsTheValuesOfTheFilters() = runBlocking {
        val source = options(null)
        val filters = source.filterOptions()
        assertEquals(listOf("order", "done", "tags"), filters.map { it.id })
        assertEquals(OptionValue.Select("new"), filters[0].initial)
        assertEquals(SourceOption.Kind.Multi(listOf(SourceOption.Choice("Sea", "sea")), excludable = true), filters[2].kind)
        assertEquals("new|false|||none", source.browse(1, "", emptyMap()).items.first().title)
        val chosen = source.browse(1, "", mapOf("order" to OptionValue.Select("top"), "done" to OptionValue.Toggle(true), "tags" to OptionValue.Multi(emptyList(), listOf("sea"))))
        assertEquals("top|true||sea|none", chosen.items.first().title)
        assertEquals("search tide", source.browse(1, "tide", mapOf("done" to OptionValue.Toggle(true))).items.first().title)
        source.close()
    }

    @Test fun aSettingOfAnLnReaderPluginStaysInItsFile() = runBlocking {
        val file = File(temporaryFolder(), "opts.settings.json")
        val source = options(file)
        assertEquals(listOf("mirror"), source.settingOptions().map { it.id })
        assertEquals(OptionValue.Select("a"), source.settingValues()["mirror"])
        source.setSetting("mirror", OptionValue.Select("b"))
        assertTrue(source.browse(1, "", emptyMap()).items.first().title.endsWith("|b"))
        source.close()
        // A new load of the plugin reads the saved value.
        val again = options(file)
        assertEquals(OptionValue.Select("b"), again.settingValues()["mirror"])
        again.close()
    }

    /** Reads a real plugin from the web. It runs only with MOEKOMA_LIVE=1, because it needs the network. */
    @Test fun readsARealPlugin() = runBlocking {
        assumeTrue(System.getenv("MOEKOMA_LIVE") == "1")
        val web: Fetcher = { request ->
            val connection = URI(request.url).toURL().openConnection() as java.net.HttpURLConnection
            connection.requestMethod = request.method
            request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36")
            request.body?.let { body -> connection.doOutput = true; connection.outputStream.use { it.write(body) } }
            val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
            HttpResponse(connection.responseCode, request.url, connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(", ") }, stream?.readBytes() ?: ByteArray(0))
        }
        val index = LnReaderIndex.parse(URI("https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json").toURL().readBytes())
        val info = index.first { it.id == "anf.net" }
        val source = LnReaderPlugin.load(info, URI(info.code).toURL().readText(), web)
        val popular = source.popular(1)
        assertTrue(popular.items.isNotEmpty())
        val details = source.details(popular.items[0])
        assertTrue(details.chapters.isNotEmpty())
        val text = source.chapter(details.chapters[0].id)
        assertTrue(text.length > 200)
        println("LIVE ${popular.items[0].title}: ${details.chapters.size} chapters, ${text.length} characters in the first")
        source.close()
    }
}
