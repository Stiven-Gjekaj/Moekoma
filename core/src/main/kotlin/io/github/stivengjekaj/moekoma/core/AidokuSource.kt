package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

/** The list of Aidoku sources, such as index.min.json of Aidoku Community. As in Asabunko. */
object AidokuIndex {
    @Serializable
    private class Item(
        val id: String, val name: String, val version: Int, val iconURL: String? = null, val downloadURL: String? = null,
        val languages: List<String>? = null, val contentRating: Int? = null,
        // The form of the lists before Aidoku 0.7.
        val file: String? = null, val icon: String? = null, val lang: String? = null, val nsfw: Int? = null,
    )

    @Serializable
    private class Listed(val sources: List<Item>)

    private val json = Json { ignoreUnknownKeys = true }

    /** The links of the list are relative to the folder of [url]. */
    fun parse(data: ByteArray, url: String): List<SourceInfo> {
        val text = String(data, Charsets.UTF_8)
        val items = runCatching { json.decodeFromString(Listed.serializer(), text).sources }.getOrNull()
            ?: json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Item.serializer()), text)
        val folder = URI(url)
        return items.mapNotNull { item ->
            val download = item.downloadURL ?: item.file?.let { "sources/$it" } ?: return@mapNotNull null
            val code = runCatching { folder.resolve(download.replace(" ", "%20")).toString() }.getOrNull() ?: return@mapNotNull null
            val icon = (item.iconURL ?: item.icon?.let { "icons/$it" })?.let { runCatching { folder.resolve(it).toString() }.getOrNull() }
            val languages = item.languages ?: listOfNotNull(item.lang)
            // Aidoku has sources of novels too. Their names say it.
            val novels = "novel" in "${item.id} ${item.name}".lowercase()
            SourceInfo(
                id = item.id, name = item.name, language = if (languages.size > 1) "Multi" else languages.firstOrNull().orEmpty(),
                version = item.version.toString(), icon = icon, code = code, novels = novels, adult = (item.contentRating ?: item.nsfw ?: 0) >= 2,
            )
        }
    }
}

/** The files of an .aix package: a ZIP with Payload/main.wasm, Payload/source.json, and other files. */
class AidokuPackage(file: File) {
    val wasm: ByteArray
    val manifest: JsonObject
    val settings: ByteArray?
    val filters: ByteArray?

    init {
        val found = HashMap<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("Payload/")
                if (name in setOf("main.wasm", "source.json", "settings.json", "filters.json")) found[name] = zip.readBytes()
            }
        }
        wasm = found["main.wasm"] ?: throw AidokuException.Missing("main.wasm or source.json")
        manifest = Json.parseToJsonElement(String(found["source.json"] ?: throw AidokuException.Missing("main.wasm or source.json"))) as JsonObject
        settings = found["settings.json"]
        filters = found["filters.json"]
    }

    private val info get() = manifest["info"] as? JsonObject
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonElement?.strings(): List<String> = (this as? JsonArray)?.mapNotNull { it.text() }.orEmpty()

    val languages: List<String> get() = info?.get("languages").strings()
    val multiLanguage: Boolean get() = (manifest["config"] as? JsonObject)?.get("languageSelectType").text() == "multi"
    val baseUrl: String? get() = info?.get("urls").strings().firstOrNull() ?: info?.get("url").text()

    /** The listings of source.json. A listing can be only its ID. */
    val listings: List<Aidoku.Listing> get() = (manifest["listings"] as? JsonArray).orEmpty().mapNotNull { item ->
        item.text()?.let { return@mapNotNull Aidoku.Listing(it, it) }
        val listing = item as? JsonObject ?: return@mapNotNull null
        val id = listing["id"].text() ?: return@mapNotNull null
        Aidoku.Listing(id, listing["name"].text() ?: id, (listing["kind"] as? JsonPrimitive)?.longOrNull ?: 0)
    }

    /** The first values of the settings: the languages, the base URL, and the default of each setting of settings.json. */
    val initialDefaults: Map<String, ByteArray> get() {
        val values = HashMap<String, ByteArray>()
        values["languages"] = PostcardWriter.write { list(if (multiLanguage) languages else languages.take(1)) { string(it) } }
        baseUrl?.let { url -> values["url"] = PostcardWriter.write { string(url) } }
        val groups = settings?.let { runCatching { Json.parseToJsonElement(String(it)) as? JsonArray }.getOrNull() }.orEmpty()
        fun visit(setting: JsonObject) {
            (setting["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.forEach(::visit)
            val key = setting["key"].text() ?: return
            val value = setting["default"] ?: return
            val type = setting["type"].text()
            val primitive = value as? JsonPrimitive
            when {
                type == "switch" && primitive?.isString == false && primitive.booleanOrNull != null -> values[key] = PostcardWriter.write { bool(primitive.booleanOrNull!!) }
                type == "segment" && primitive?.isString == false && primitive.longOrNull != null -> values[key] = PostcardWriter.write { signed(primitive.longOrNull!!) }
                type == "stepper" && primitive?.isString == false && primitive.doubleOrNull != null -> values[key] = PostcardWriter.write { f32(primitive.doubleOrNull!!.toFloat()) }
                primitive?.isString == true -> values[key] = PostcardWriter.write { string(primitive.content) }
                value is JsonArray -> values[key] = PostcardWriter.write { list(value.strings()) { string(it) } }
            }
        }
        groups.mapNotNull { it as? JsonObject }.forEach(::visit)
        return values
    }
}

/** A page of a chapter of an Aidoku source: a request of an image, or an image that the source drew. */
sealed interface AidokuPage {
    data class Remote(val request: HttpRequest) : AidokuPage
    class Drawn(val png: ByteArray) : AidokuPage
}

/** The content of a chapter: pages, or the HTML of a chapter of a novel. */
sealed interface AidokuContent {
    data class Pages(val pages: List<AidokuPage>) : AidokuContent
    data class Html(val html: String) : AidokuContent
}

/** One Aidoku source in its own WebAssembly instance. As in Asabunko. */
class AidokuSource private constructor(
    val info: SourceInfo,
    private val host: AidokuHost,
    private val listings: List<Aidoku.Listing>,
    private val baseUrl: String?,
    private val filterList: List<SourceOption>,
    private val settingList: List<SourceOption>,
    private val settingsForm: AidokuSettings,
    private val fetcher: Fetcher,
) : AutoCloseable {
    /** The full data of each series and chapter that the source gave, because a source can need more than the key. */
    private val known = HashMap<String, Aidoku.Manga>()
    private val chapters = HashMap<String, Aidoku.Chapter>()

    companion object {
        /** Loads the source from its .aix [file]. The values of its settings stay in [settings]. */
        suspend fun load(info: SourceInfo, file: File, settings: File?, fetcher: Fetcher, platform: AidokuPlatform?): AidokuSource {
            val pack = AidokuPackage(file)
            val host = AidokuHost(info.id, pack.wasm, AidokuDefaults(settings, pack.initialDefaults), fetcher, platform)
            try {
                return host.run {
                    start()
                    var listings = pack.listings
                    if (listings.isEmpty() && "get_listings" in exports) {
                        listings = runCatching { PostcardReader(call("get_listings")).list { Aidoku.Listing.read(this) } }.getOrDefault(emptyList())
                    }
                    // The static filters of filters.json come first, and the filters of get_filters after them.
                    var filters = AidokuOptions.filters(pack.filters)
                    if ("get_filters" in exports) {
                        val ids = filters.map { it.id }.toSet()
                        filters = filters + runCatching { AidokuOptions.filtersOfPostcard(call("get_filters")) }.getOrDefault(emptyList()).filter { it.id !in ids }
                    }
                    val dynamic = if ("get_settings" in exports) runCatching { call("get_settings") }.getOrNull() else null
                    val form = AidokuSettings(pack.settings, dynamic, defaults, pack.languages, pack.multiLanguage)
                    AidokuSource(info, host, listings, pack.baseUrl, filters, AidokuOptions.settings(pack.settings), form, fetcher)
                }
            } catch (e: Exception) {
                host.close()
                throw e
            }
        }

        /** The text of a novel source is Markdown. Paragraphs, headings, bold, and italic are enough for a chapter. */
        fun html(markdown: String): String = markdown.split("\n\n").mapNotNull { block ->
            var line = Xhtml.escape(block.trim())
            if (line.isEmpty()) return@mapNotNull null
            line = line.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>").replace(Regex("\\*(.+?)\\*"), "<i>$1</i>").replace("\n", "<br/>")
            when {
                line.startsWith("### ") -> "<h4>${line.drop(4)}</h4>"
                line.startsWith("## ") -> "<h3>${line.drop(3)}</h3>"
                line.startsWith("# ") -> "<h2>${line.drop(2)}</h2>"
                else -> "<p>$line</p>"
            }
        }.joinToString("\n")
    }

    val defaults: AidokuDefaults get() = host.defaults

    fun filterOptions(): List<SourceOption> = filterList

    suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>): OnlinePage =
        search(query.ifEmpty { null }, page, AidokuOptions.encode(filters, filterList))

    fun settingOptions(): List<SourceOption> = settingList

    fun settingValues(): Map<String, OptionValue> =
        settingList.associate { setting -> setting.id to (host.defaults[setting.id]?.let { AidokuOptions.decodeSetting(it, setting) } ?: setting.initial) }

    fun setSetting(id: String, value: OptionValue) {
        val setting = settingList.firstOrNull { it.id == id } ?: return
        host.defaults[id] = AidokuOptions.encodeSetting(value, setting)
    }

    val hasSettingsForm: Boolean get() = !settingsForm.isEmpty

    /** A form of the settings: "root" for the first form. */
    suspend fun form(id: String): SourceForm {
        if (settingsForm.isEmpty) throw SourceFormException.noForm()
        return host.run { settingsForm.form(id) }
    }

    /** Does the action of a row, and gives the form again. */
    suspend fun formAction(form: String, row: String, input: FormInput): SourceForm {
        // An OAuth sign-in with PKCE changes its code into a token before the source gets it.
        var token: String? = null
        if (input is FormInput.Callback) {
            settingsForm.tokenRequest(row, input.url)?.let { request ->
                val response = fetcher(request)
                if (response.status !in 200..299) throw SourceFormException.refused()
                token = String(response.body, Charsets.UTF_8)
            }
        }
        return host.run {
            settingsForm.act(form, row, input, token, this)
            settingsForm.form(form)
        }
    }

    /** The listing for popular series, and the listing for the latest ones, by their names. */
    private fun listing(words: List<String>) = listings.firstOrNull { listing -> words.any { it in "${listing.id} ${listing.name}".lowercase() } }
    private val popularListing get() = listing(listOf("popular", "hot", "trend", "top", "rank"))
    private val latestListing get() = listing(listOf("latest", "update", "recent", "new"))

    val canShowLatest: Boolean get() = "get_manga_list" in host.exports && latestListing != null

    private fun page(data: ByteArray): OnlinePage {
        val result = Aidoku.MangaPageResult.read(PostcardReader(data))
        synchronized(known) { result.entries.forEach { known[it.key] = it } }
        return OnlinePage(result.entries.map { OnlineSeries(it.key, it.title, it.cover) }, result.hasNextPage)
    }

    private suspend fun list(listing: Aidoku.Listing, page: Int): OnlinePage {
        val bytes = PostcardWriter.write { listing.write(this) }
        return page(host.run { call("get_manga_list", bytes, page) })
    }

    /** The search of the source. The query is raw UTF-8. With no filters, the list of filters is empty. */
    private suspend fun search(query: String?, page: Int, filters: ByteArray = byteArrayOf(0)): OnlinePage {
        val text = (query ?: "").toByteArray(Charsets.UTF_8)
        return page(host.run { call("get_search_manga_list", text, page, filters) })
    }

    suspend fun popular(page: Int): OnlinePage {
        val listing = popularListing
        return if (listing != null && "get_manga_list" in host.exports) list(listing, page) else search(null, page)
    }

    suspend fun latest(page: Int): OnlinePage = latestListing?.let { list(it, page) } ?: search(null, page)

    suspend fun search(query: String, page: Int): OnlinePage = search(query as String?, page)

    private fun manga(series: OnlineSeries) = synchronized(known) { known[series.id] } ?: Aidoku.Manga(series.id, series.title, series.cover)

    suspend fun details(series: OnlineSeries): OnlineDetails {
        val bytes = PostcardWriter.write { manga(series).write(this) }
        val manga = Aidoku.Manga.read(PostcardReader(host.run { call("get_manga_update", bytes, true, true) }))
        // A source gives the newest chapter first.
        val list = manga.chapters.orEmpty().reversed()
        synchronized(known) {
            known[manga.key] = manga.copy(chapters = null)
            list.forEach { chapters[it.key] = it }
        }
        val authors = manga.authors.orEmpty()
        val people = authors + manga.artists.orEmpty().filter { it !in authors }
        return OnlineDetails(
            series = OnlineSeries(manga.key, manga.title, manga.cover ?: series.cover),
            author = people.joinToString(", ").ifEmpty { null }, summary = manga.description?.ifEmpty { null }, status = manga.statusName,
            genres = manga.tags.orEmpty(),
            chapters = list.filter { !it.locked }.map { chapter ->
                OnlineChapter(
                    id = chapter.key, title = chapter.displayTitle, number = chapter.chapterNumber?.toDouble(),
                    date = chapter.dateUploaded?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().toString() },
                    group = chapter.scanlators?.joinToString(", "),
                )
            },
            isNovel = if (info.novels) true else null,
        )
    }

    /** A cover or a page with the referer of the site, which many sites ask for. */
    fun plainRequest(url: String): HttpRequest = HttpRequest(url, headers = baseUrl?.let { mapOf("Referer" to "$it/") }.orEmpty())

    /** The request of an image of the source, with `get_image_request` when the source has it. The caller is on the thread of the source. */
    private fun AidokuHost.imageRequest(url: String, context: Map<String, String>?): HttpRequest? {
        val urlBytes = PostcardWriter.write { string(url) }
        val contextArgument: Any = context?.let { map -> PostcardWriter.write { Aidoku.writeMap(map, this) } } ?: -1
        val rid = PostcardReader(call("get_image_request", urlBytes, contextArgument)).signed().toInt()
        try {
            val request = item(rid) as? AidokuHost.Request ?: return null
            return HttpRequest(request.url ?: return null, request.method, request.headers.toMap())
        } finally {
            destroy(rid)
        }
    }

    suspend fun content(chapter: OnlineChapter, series: OnlineSeries): AidokuContent {
        if (synchronized(known) { chapters[chapter.id] } == null) details(series)
        val mangaBytes = PostcardWriter.write { manga(series).write(this) }
        val chapterBytes = PostcardWriter.write { (synchronized(known) { chapters[chapter.id] } ?: Aidoku.Chapter(chapter.id, chapter.title)).write(this) }
        val hasImageRequest = "get_image_request" in host.exports
        val processes = "process_page_image" in host.exports
        val prepared: List<Any> = host.run {
            val pages = PostcardReader(call("get_page_list", mangaBytes, chapterBytes)).list { Aidoku.Page.read(this) }
            val texts = pages.mapNotNull { (it.content as? Aidoku.PageContent.Text)?.text }
            if (texts.isNotEmpty() && texts.size == pages.size) return@run listOf(AidokuContent.Html(html(texts.joinToString("\n\n"))))
            pages.mapNotNull { page ->
                when (val content = page.content) {
                    is Aidoku.PageContent.Url -> {
                        val request = if (hasImageRequest) runCatching { imageRequest(content.url, content.context) }.getOrNull() else null
                        (request ?: plainRequest(content.url)) to content.context
                    }
                    // The source drew the page itself. The host owns the image.
                    is Aidoku.PageContent.Image -> try {
                        (item(content.rid) as? AidokuHost.Picture)?.fileData?.let(AidokuPage::Drawn)
                    } finally {
                        destroy(content.rid)
                    }
                    else -> null
                }
            }
        }
        (prepared.firstOrNull() as? AidokuContent.Html)?.let { return it }
        if (!processes) {
            @Suppress("UNCHECKED_CAST")
            return AidokuContent.Pages(prepared.map { item -> (item as? Pair<HttpRequest, *>)?.first?.let(AidokuPage::Remote) ?: item as AidokuPage })
        }
        return AidokuContent.Pages(processed(prepared))
    }

    /**
     * Downloads the images of a source that changes its pages, and gives each image to `process_page_image`, which can
     * put the parts of a page in order again. The downloads run at the same time. The drawing runs on the thread of the source.
     */
    private suspend fun processed(prepared: List<Any>): List<AidokuPage> {
        val downloads = coroutineScope {
            prepared.map { item ->
                async {
                    @Suppress("UNCHECKED_CAST")
                    val request = (item as? Pair<HttpRequest, Map<String, String>?>)?.first ?: return@async null
                    runCatching { fetcher(request) }.getOrNull()?.takeIf { it.status in 200..299 }
                }
            }.map { it.await() }
        }
        return host.run {
            prepared.mapIndexed { index, item ->
                if (item is AidokuPage) return@mapIndexed item
                @Suppress("UNCHECKED_CAST")
                val (request, context) = item as Pair<HttpRequest, Map<String, String>?>
                val response = downloads[index] ?: return@mapIndexed AidokuPage.Remote(request)
                val rid = add(AidokuHost.Picture(response.body, null, platform))
                try {
                    val response1 = PostcardWriter.write {
                        unsigned(response.status)
                        Aidoku.writeMap(response.headers, this)
                        option(request.url) { string(it) }
                        Aidoku.writeMap(request.headers, this)
                        signed(rid)
                    }
                    val contextArgument: Any = context?.let { map -> PostcardWriter.write { Aidoku.writeMap(map, this) } } ?: -1
                    val drawn = runCatching { PostcardReader(call("process_page_image", response1, contextArgument)).signed().toInt() }.getOrNull()
                        ?: return@mapIndexed AidokuPage.Remote(request)
                    try {
                        val png = (item(drawn) as? AidokuHost.Picture)?.fileData
                        if (png != null && platform?.decode(png) != null) AidokuPage.Drawn(png) else AidokuPage.Remote(request)
                    } finally {
                        if (drawn != rid) destroy(drawn)
                    }
                } finally {
                    destroy(rid)
                }
            }
        }
    }

    override fun close() = host.close()

    /** The close of the source, with no reference to the source. */
    fun closer(): AutoCloseable = host.closer()
}
