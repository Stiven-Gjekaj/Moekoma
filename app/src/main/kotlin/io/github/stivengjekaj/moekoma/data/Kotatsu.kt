package io.github.stivengjekaj.moekoma.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.KotatsuPlugin
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlinePage
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONTokener
import tsuki.MangaLoaderContext
import tsuki.MangaParser
import tsuki.bitmap.Rect
import tsuki.config.ConfigKey
import tsuki.config.MangaSourceConfig
import tsuki.model.Manga
import tsuki.model.MangaChapter
import tsuki.model.MangaListFilter
import tsuki.model.MangaPage
import tsuki.model.MangaSource
import tsuki.model.MangaState
import tsuki.model.SortOrder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import android.graphics.Bitmap as AndroidBitmap
import tsuki.bitmap.Bitmap as ParserBitmap

/**
 * Runs a plugin of the Kotatsu parsers, such as the jar of Gekkoushi/plugin. The plugin holds only the parsers. Tsuki,
 * their API, is in the app (module ":tsuki"), and the app gives them the network client, the cookies, a web view for
 * JavaScript, and bitmaps for the images that a site mixes.
 */
class KotatsuHost(private val context: Context, private val http: OkHttpClient) {
    private val folder = File(context.filesDir, "Kotatsu")
    private val plugins = mutableMapOf<String, Plugin>()

    /** One jar that loaded: its sources, by name, and the context of its parsers. */
    private inner class Plugin(file: File) {
        val loader = PathClassLoader(file.path, context.classLoader)
        val sourceClass: Class<*> = Class.forName("tsuki.model.MangaParserSource", true, loader)
        val sources: Map<String, MangaSource> = sourceClass.enumConstants.orEmpty().map { it as MangaSource }.associateBy { it.name }
        private val factory = Class.forName("tsuki.MangaParserFactoryKt", true, loader).getMethod("newParser", sourceClass, MangaLoaderContext::class.java)
        val parserContext = ParserContext(this::make)

        fun make(source: MangaSource): MangaParser = try {
            factory.invoke(null, source, parserContext) as MangaParser
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // The fault of the parser is the cause. The reflection only wraps it.
            throw IOException("${source.title} did not start: ${e.cause?.let { it.message ?: it.javaClass.name }}", e.cause)
        }
    }

    private fun jarFile(url: String): File = File(folder, url.substringBefore('?').substringAfterLast('/').ifEmpty { "plugin.jar" }.let { name ->
        // Two releases of one repository have the same file name, so the name has the hash of the URL.
        "${url.hashCode().toUInt().toString(16)}-$name"
    })

    /** Finds the jar of a line of the source list. */
    suspend fun jar(url: String, get: suspend (String) -> ByteArray): KotatsuPlugin.Jar {
        KotatsuPlugin.direct(url)?.let { return it }
        val api = KotatsuPlugin.latestReleaseApi(url) ?: throw IOException("The URL is not a jar or a repository on GitHub.")
        return KotatsuPlugin.release(get(api)) ?: throw IOException("The latest release of the repository has no jar.")
    }

    /** The sources of a plugin. The jar downloads the first time. */
    suspend fun sources(jar: KotatsuPlugin.Jar): List<SourceInfo> = withContext(Dispatchers.IO) {
        val plugin = plugin(jar.url)
        plugin.sources.values.mapNotNull { source ->
            KotatsuPlugin.info(source.name, source.title, source.locale, source.contentType.name, source.isBroken, jar)
        }
    }

    private fun plugin(url: String): Plugin = synchronized(plugins) {
        plugins[url]?.let { return it }
        val file = jarFile(url)
        if (!file.isFile) {
            folder.mkdirs()
            val part = File(folder, file.name + ".part")
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("The plugin did not download (HTTP ${response.code}).")
                part.outputStream().use { response.body.byteStream().copyTo(it) }
            }
            if (!part.renameTo(file)) throw IOException("The plugin was not saved.")
            // Android 14 and later load code only from a file that the app cannot write.
            file.setReadOnly()
        }
        try {
            Plugin(file).also { plugins[url] = it }
        } catch (e: LinkageError) {
            throw IOException("The plugin did not start: ${e.message}. Moekoma runs plugins for Tsuki 1.0.5.")
        } catch (e: ReflectiveOperationException) {
            throw IOException("The file is not a plugin of the Kotatsu parsers.")
        }
    }

    /** Removes the jar of a source when no other installed source uses it. */
    fun uninstall(info: SourceInfo, others: List<SourceInfo>) {
        if (others.any { it.code == info.code }) return
        synchronized(plugins) { plugins.remove(info.code) }
        jarFile(info.code).apply { setWritable(true); delete() }
    }

    suspend fun load(installed: InstalledSource): OnlineSource = withContext(Dispatchers.IO) {
        val plugin = plugin(installed.info.code)
        val source = plugin.sources[installed.info.id] ?: throw IOException("The plugin does not have the source ${installed.info.name}.")
        KotatsuSource(installed, plugin.parserContext.parser(source), plugin.parserContext.httpClient)
    }

    /** What the parsers of one plugin get from the app. */
    private inner class ParserContext(private val make: (MangaSource) -> MangaParser) : MangaLoaderContext() {
        private val parsers = ConcurrentHashMap<MangaSource, MangaParser>()
        private val network: NetworkHelper = Injekt.get()

        fun parser(source: MangaSource): MangaParser = parsers.getOrPut(source) { make(source) }

        override val cookieJar: CookieJar get() = network.cookieJar

        /**
         * The client of the Mihon extensions, with the cookies and the Cloudflare check, and two interceptors more:
         * the headers of the parser of a request, then the interceptor of that parser, as in Kotatsu.
         */
        override val httpClient: OkHttpClient by lazy {
            network.client.newBuilder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    val source = request.tag(MangaSource::class.java) ?: return@Interceptor chain.proceed(request)
                    val builder = request.newBuilder()
                    for ((name, value) in parser(source).getRequestHeaders()) {
                        if (request.header(name) == null || name.equals("User-Agent", ignoreCase = true)) builder.header(name, value)
                    }
                    chain.proceed(builder.build())
                })
                .addInterceptor(Interceptor { chain ->
                    val source = chain.request().tag(MangaSource::class.java) ?: return@Interceptor chain.proceed(chain.request())
                    parser(source).intercept(chain)
                })
                .build()
        }

        override fun newParserInstance(source: MangaSource): MangaParser = parser(source)

        override fun getParserSources(): List<MangaSource> = synchronized(plugins) { plugins.values.flatMap { it.sources.values } }

        @Deprecated("Provide a base url")
        override suspend fun evaluateJs(script: String): String? = evaluateJs("about:blank", script)

        /** Runs a script in a web view with no window, in the page of [baseUrl]. */
        @SuppressLint("SetJavaScriptEnabled")
        override suspend fun evaluateJs(baseUrl: String, script: String): String? = withTimeoutOrNull(30_000) {
            withContext(Dispatchers.Main) {
                val view = WebView(context)
                try {
                    suspendCancellableCoroutine { done ->
                        view.settings.javaScriptEnabled = true
                        view.settings.domStorageEnabled = true
                        view.settings.userAgentString = getDefaultUserAgent()
                        view.webViewClient = object : WebViewClient() {
                            private var ran = false

                            override fun onPageFinished(page: WebView, url: String) {
                                if (ran) return
                                ran = true
                                page.evaluateJavascript(script) { result ->
                                    // The web view gives the result as JSON, such as a string in quotes.
                                    val text = runCatching { JSONTokener(result).nextValue()?.toString() }.getOrNull()
                                    if (done.isActive) done.resume(text?.takeIf { it != "null" })
                                }
                            }
                        }
                        view.loadUrl(baseUrl)
                    }
                } finally {
                    view.destroy()
                }
            }
        }

        override fun requestBrowserAction(parser: MangaParser, url: String): Nothing {
            BrowserChecks.report(url)
            throw IOException("${parser.source.title} needs a page in a browser. Open it from the banner.")
        }

        override fun getConfig(source: MangaSource): MangaSourceConfig = SourceConfig(context, source)

        override fun getDefaultUserAgent(): String = BrowserChecks.userAgent(context)

        override fun redrawImageResponse(response: Response, redraw: (image: ParserBitmap) -> ParserBitmap): Response {
            val bytes = response.body.bytes()
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("The image did not decode.")
            val drawn = redraw(PageBitmap(image)) as PageBitmap
            val out = ByteArrayOutputStream()
            drawn.bitmap.compress(AndroidBitmap.CompressFormat.PNG, 100, out)
            return response.newBuilder().body(out.toByteArray().toResponseBody("image/png".toMediaType())).build()
        }

        override fun createBitmap(width: Int, height: Int): ParserBitmap =
            PageBitmap(AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888))
    }
}

/** A bitmap of Android for a parser that mixes the parts of an image. */
private class PageBitmap(val bitmap: AndroidBitmap) : ParserBitmap {
    private val canvas by lazy { android.graphics.Canvas(bitmap) }
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun drawBitmap(sourceBitmap: ParserBitmap, src: Rect, dst: Rect) {
        val from = (sourceBitmap as PageBitmap).bitmap
        canvas.drawBitmap(
            from, android.graphics.Rect(src.left, src.top, src.right, src.bottom),
            android.graphics.Rect(dst.left, dst.top, dst.right, dst.bottom), null,
        )
    }
}

/** The settings of a parser. Moekoma has no screen for them yet, so each key has the value that the parser gives. */
private class SourceConfig(context: Context, private val source: MangaSource) : MangaSourceConfig {
    private val prefs = context.getSharedPreferences("kotatsu_${source.name}", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T {
        if (key is ConfigKey.Domain) prefs.getString(key.key, null)?.let { return it as T }
        return key.defaultValue
    }
}

/**
 * A parser of a Kotatsu plugin as an [OnlineSource]. The ID of a series is its URL, and its public URL follows after a
 * line break. The parsers count by offset, so the source keeps the offset of each page of each list.
 */
class KotatsuSource(private val installed: InstalledSource, private val parser: MangaParser, private val client: OkHttpClient) : OnlineSource {
    override val info: SourceInfo get() = installed.info
    override val canShowLatest: Boolean get() = SortOrder.UPDATED in parser.availableSortOrders

    private val offsets = ConcurrentHashMap<String, MutableMap<Int, Int>>()

    private fun order(vararg wanted: SortOrder): SortOrder =
        wanted.firstOrNull { it in parser.availableSortOrders } ?: parser.availableSortOrders.first()

    private suspend fun list(order: SortOrder, filter: MangaListFilter, page: Int): OnlinePage = io {
        val key = "$order|${filter.query.orEmpty()}"
        val pages = offsets.getOrPut(key) { ConcurrentHashMap(mapOf(1 to 0)) }
        val offset = pages[page] ?: return@io OnlinePage(emptyList(), false)
        val items = parser.getList(offset, order, filter)
        pages[page + 1] = offset + items.size
        OnlinePage(items.map { OnlineSeries(it.url + "\n" + it.publicUrl, it.title, it.coverUrl?.ifEmpty { null }) }, items.isNotEmpty())
    }

    override suspend fun popular(page: Int) = list(order(SortOrder.POPULARITY, SortOrder.POPULARITY_WEEK, SortOrder.POPULARITY_MONTH), MangaListFilter(), page)
    override suspend fun latest(page: Int) = list(order(SortOrder.UPDATED), MangaListFilter(), page)
    override suspend fun search(query: String, page: Int): OnlinePage {
        if (!parser.filterCapabilities.isSearchSupported) return OnlinePage(emptyList(), false)
        return list(order(SortOrder.RELEVANCE, SortOrder.POPULARITY), MangaListFilter(query = query), page)
    }

    private fun manga(series: OnlineSeries): Manga {
        val url = series.id.substringBefore('\n')
        val public = series.id.substringAfter('\n', "https://${parser.domain}$url")
        return Manga(
            id = (parser.source.name + url).hashCode().toLong(), title = series.title, altTitles = emptySet(), url = url, publicUrl = public,
            rating = -1f, contentRating = null, coverUrl = series.cover, tags = emptySet(), state = null, authors = emptySet(), source = parser.source,
        )
    }

    override suspend fun details(series: OnlineSeries): OnlineDetails = io {
        val manga = parser.getDetails(manga(series))
        OnlineDetails(
            series = OnlineSeries(series.id, manga.title.ifEmpty { series.title }, manga.largeCoverUrl ?: manga.coverUrl ?: series.cover),
            author = manga.authors.joinToString(", ").ifEmpty { null },
            summary = manga.description?.let { org.jsoup.Jsoup.parse(it).wholeText().trim() }?.ifEmpty { null },
            status = when (manga.state) {
                MangaState.ONGOING -> "Ongoing"
                MangaState.FINISHED -> "Completed"
                MangaState.ABANDONED -> "Cancelled"
                MangaState.PAUSED -> "On hiatus"
                MangaState.UPCOMING -> "Upcoming"
                else -> null
            },
            genres = manga.tags.map { it.title }.sorted(),
            // The parsers give the first chapter first.
            chapters = manga.chapters.orEmpty().map { chapter ->
                OnlineChapter(
                    id = chapter.url, title = chapter.title?.ifEmpty { null } ?: chapter.name,
                    number = chapter.number.takeIf { it > 0f }?.toDouble(),
                    date = chapter.uploadDate.takeIf { it > 0 }?.let { dateFormat.format(Date(it)) },
                    group = chapter.branch ?: chapter.scanlator,
                )
            },
            isNovel = false,
        )
    }

    override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent = io {
        val item = MangaChapter(
            id = (parser.source.name + chapter.id).hashCode().toLong(), title = chapter.title, number = chapter.number?.toFloat() ?: 0f,
            volume = 0, url = chapter.id, scanlator = null, uploadDate = 0, branch = chapter.group, source = parser.source,
        )
        val pages = parser.getPages(item)
        ChapterContent.Pages(pages.mapIndexed { index, page -> RemoteImage(installed.id, page, "${installed.id}|${chapter.id}|$index") })
    }

    override suspend fun image(image: RemoteImage): Response = io {
        val url = when (val item = image.item) {
            is MangaPage -> parser.getPageUrl(item)
            is String -> item
            else -> throw IOException("The image is not of this source.")
        }
        // Kotatsu sends the site as the referer of an image, and many sites refuse an image with no referer.
        val request = Request.Builder().url(url).header("Referer", "https://${parser.domain}/").tag(MangaSource::class.java, parser.source).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("The image did not load (HTTP ${response.code}).")
        }
        response
    }

    /** Runs the code of the parser. An error of the plugin becomes a fault of this source. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: LinkageError) {
            throw IOException("${info.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
