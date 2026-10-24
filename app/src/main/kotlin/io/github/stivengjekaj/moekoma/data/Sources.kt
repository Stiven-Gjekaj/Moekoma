package io.github.stivengjekaj.moekoma.data

import android.content.Context
import io.github.stivengjekaj.moekoma.core.AidokuIndex
import io.github.stivengjekaj.moekoma.core.AidokuSource
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.BookOrigin
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.LnReaderIndex
import io.github.stivengjekaj.moekoma.core.LnReaderPlugin
import io.github.stivengjekaj.moekoma.core.MihonRepo
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.Xhtml
import io.github.stivengjekaj.moekoma.core.MihonStore
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.SourceInfo
import io.github.stivengjekaj.moekoma.core.SourceKind
import io.github.stivengjekaj.moekoma.core.SourceLanguages
import io.github.stivengjekaj.moekoma.core.SuwayomiServer
import io.github.stivengjekaj.moekoma.core.SuwayomiSource
import io.github.stivengjekaj.moekoma.core.SourceLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

/** The install of the sources of the lists: the sources that installed, of all of them. */
data class SourceSetup(val done: Int, val total: Int)

/**
 * The online sources of the app: the lists of the source list, the sources that install from them, and the series of
 * the sources in the library. Asabunko does the same in its model.
 */
class Sources(
    context: Context,
    private val model: AppModel,
    private val settings: Settings,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val mihon by lazy { MihonHost(context, http) }
    private val kotatsu by lazy { KotatsuHost(context, http) }
    private val code = SourceCode(model.root)
    private val context = context.applicationContext
    private val aidokuPlatform by lazy { AndroidAidokuPlatform(this.context) }

    init {
        // The Kotatsu parsers use the network helper of the Mihon extensions, so Injekt is ready before each kind.
        MihonHost.setUpInjekt(context.applicationContext as android.app.Application)
    }

    private val setupState = MutableStateFlow<SourceSetup?>(null)
    val setup: StateFlow<SourceSetup?> = setupState.asStateFlow()

    /** The key of each repository of Mihon extensions, from its last read. */
    private val keys = mutableMapOf<UUID, String>()
    private var setUpJob: Job? = null
    private val loading = mutableMapOf<String, Deferred<OnlineSource>>()
    private val adding = mutableMapOf<BookOrigin, Deferred<Book>>()

    private val installed get() = model.library.value.installed

    /**
     * Replaces the source list and brings each list up to date. The sources of a line that the user removed go too.
     * A save waits for the save before it, so a source does not install two times.
     */
    fun save(text: String): List<SourceLink> {
        val before = installed
        val links = model.saveSources(text)
        val kept = links.map { it.id }.toSet()
        for (gone in before.filter { it.list !in kept }) removeCode(gone)
        val previous = setUpJob
        setUpJob = scope.launch {
            previous?.join()
            setUp(links)
        }
        return links
    }

    /**
     * Finds the kind of each line and brings the sources of each list up to date, with no question. The English sources
     * that are not on the device go in, and the sources that left a list go out. The other sources stay as they are.
     */
    private suspend fun setUp(links: List<SourceLink>) {
        val failed = mutableListOf<String>()
        for (start in links) {
            var link = start
            if (link.kind == null) {
                val kind = runCatching { SourceKind.detect(model.fetch(link.url, link)) }.getOrNull()
                if (kind == null) {
                    failed += link.shortAddress
                    continue
                }
                model.learn(link.id, kind, null)
                link = link.copy(kind = kind)
            }
            if (link.kind == SourceKind.Opds || link.kind == null) continue
            val infos = try {
                sources(link)
            } catch (e: Exception) {
                failed += link.name ?: link.shortAddress
                continue
            }
            val mine = installed.filter { it.list == link.id }
            val plan = SourceLanguages.sync(infos, mine.map { it.info.id }.toSet(), settings.preferences.value.adult)
            mine.filter { it.info.id in plan.remove }.forEach(::uninstall)
            installAll(plan.install, link)
        }
        if (failed.isNotEmpty()) model.fault.value = "Moekoma could not read " + failed.joinToString(", ") + "."
    }

    /** The sources in a list of the source list. */
    suspend fun sources(link: SourceLink): List<SourceInfo> = when (link.kind) {
        SourceKind.Mihon -> {
            val store = store(link)
            model.learn(link.id, null, store.name)
            MihonRepo.sourceInfos(store)
        }
        SourceKind.LnReader -> LnReaderIndex.parse(model.fetch(link.url, link))
        SourceKind.Suwayomi -> server(link).sources()
        SourceKind.Aidoku -> AidokuIndex.parse(model.fetch(link.url, link), link.url)
        SourceKind.Kotatsu -> {
            val jar = kotatsu.jar(link.url) { url -> model.fetch(url, null) }
            // The list has no name, so it takes the name of the owner of the repository, such as "Gekkoushi".
            if (link.name == null) model.learn(link.id, null, java.net.URI(link.url).path.split('/').firstOrNull { it.isNotEmpty() })
            kotatsu.sources(jar)
        }
        else -> emptyList()
    }

    /** A Suwayomi server. Each request to the host of the server gets its password. */
    private fun server(link: SourceLink) = SuwayomiServer(link.url, networkFetcher(http).withHeaders { url -> model.headers(url, link) })

    private suspend fun store(link: SourceLink): MihonStore = withContext(Dispatchers.IO) {
        val store = MihonRepo.load(link.url) { url ->
            // Only the URL of the line gets its password. The index can point to another host.
            val request = Request.Builder().url(url).apply { model.headers(url, link).forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("The server answered ${response.code}.")
                response.body.bytes()
            }
        }
        synchronized(keys) { keys[link.id] = store.signingKey }
        store
    }

    fun isInstalled(info: SourceInfo, link: SourceLink) = installed.any { it.list == link.id && it.info.id == info.id }

    /** Installs some sources, four at the same time, and counts them. A source that does not install stays out. */
    private suspend fun installAll(infos: List<SourceInfo>, link: SourceLink) {
        if (infos.isEmpty()) return
        setupState.value = SourceSetup(0, infos.size)
        // The sources of one APK install one after the other, so that the APK downloads one time.
        val byApk = infos.groupBy { it.code }.values.toList()
        var done = 0
        val gate = Semaphore(4)
        coroutineScope {
            byApk.map { group ->
                async {
                    gate.withPermit {
                        for (info in group) {
                            runCatching { install(info, link) }
                            synchronized(this@Sources) { done++ }
                            setupState.value = SourceSetup(done, infos.size)
                        }
                    }
                }
            }.forEach { it.await() }
        }
        setupState.value = null
    }

    /** Downloads the code of a source and keeps it on the device. */
    suspend fun install(info: SourceInfo, link: SourceLink) {
        when (link.kind) {
            SourceKind.Mihon -> {
                val key = synchronized(keys) { keys[link.id] } ?: store(link).signingKey
                mihon.install(info, key)
            }
            SourceKind.Kotatsu -> kotatsu.sources(io.github.stivengjekaj.moekoma.core.KotatsuPlugin.Jar(info.code, info.version))
            SourceKind.LnReader, SourceKind.Aidoku -> code.install(InstalledSource(link.id, info), http)
            // The server runs the source, so the app keeps no code for it.
            SourceKind.Suwayomi -> {}
            else -> throw MihonException("Moekoma cannot install this kind of source yet.")
        }
        val made = InstalledSource(link.id, info)
        LoadedSources.remove(made.id)
        model.change { library -> library.copy(installed = library.installed.filter { it.id != made.id } + made) }
    }

    fun uninstall(source: InstalledSource) {
        model.change { library -> library.copy(installed = library.installed.filter { it.id != source.id }) }
        removeCode(source)
    }

    private fun removeCode(source: InstalledSource) {
        LoadedSources.remove(source.id)
        val others = installed.filter { it.id != source.id }.map { it.info }
        when (kind(source)) {
            SourceKind.Kotatsu -> kotatsu.uninstall(source.info, others)
            SourceKind.LnReader, SourceKind.Aidoku -> code.remove(source)
            SourceKind.Mihon, null -> mihon.uninstall(source.info, others)
            else -> {}
        }
    }

    fun kind(source: InstalledSource): SourceKind? = model.library.value.sources.firstOrNull { it.id == source.list }?.kind

    /** The source, loaded one time in each session. Two screens that ask for one source get one load. */
    suspend fun load(source: InstalledSource): OnlineSource {
        LoadedSources.get(source.id)?.let { return it }
        val running = synchronized(loading) {
            loading[source.id] ?: scope.async(Dispatchers.IO) { make(source) }.also { loading[source.id] = it }
        }
        try {
            return running.await().also { LoadedSources.set(source.id, it) }
        } finally {
            synchronized(loading) { loading.remove(source.id) }
        }
    }

    /** The source of an installed ID, for the image loader. */
    suspend fun load(id: String): OnlineSource {
        LoadedSources.get(id)?.let { return it }
        val source = installed.firstOrNull { it.id == id } ?: throw MihonException("The source is not installed.")
        return load(source)
    }

    private suspend fun make(source: InstalledSource): OnlineSource = when (kind(source)) {
        SourceKind.Mihon -> mihon.load(source)
        SourceKind.Kotatsu -> kotatsu.load(source)
        SourceKind.LnReader -> {
            val file = code.file(source)
            if (!file.isFile) throw MihonException("The code of ${source.info.name} is not on this device. Save the source list again.")
            LnReaderSource(source, LnReaderPlugin.load(source.info, file.readText(), networkFetcher(), code.settings(source)), http)
        }
        SourceKind.Aidoku -> {
            // The code file of an Aidoku source holds its .aix package.
            val file = code.file(source)
            if (!file.isFile) throw MihonException("The code of ${source.info.name} is not on this device. Save the source list again.")
            val loaded = AidokuSource.load(source.info, file, code.settings(source), networkFetcher(), aidokuPlatform)
            AidokuOnlineSource(source, loaded, http, File(context.cacheDir, "aidoku-pages/" + source.info.id.replace('/', '_')))
        }
        SourceKind.Suwayomi -> {
            val link = model.library.value.sources.firstOrNull { it.id == source.list } ?: throw MihonException("The server of ${source.info.name} is not in the source list.")
            SuwayomiOnlineSource(source, SuwayomiSource(source.info, server(link)), http) { url -> model.headers(url, link) }
        }
        else -> throw MihonException("Moekoma cannot run this kind of source yet.")
    }

    /** The source of a book, when the book comes from an installed source. */
    fun installed(book: Book): InstalledSource? {
        val origin = book.origin ?: return null
        return installed.firstOrNull { it.list == origin.list && it.info.id == origin.source }
    }

    fun originOf(source: InstalledSource, series: String) = BookOrigin(source.list, source.info.id, series)

    // Online books

    /**
     * Adds a series of a source to the library, with its chapters and its cover. With [listed] false, the book only
     * keeps the place of the user. It is not in the read list.
     */
    suspend fun add(details: OnlineDetails, source: InstalledSource, listed: Boolean): Book {
        val origin = originOf(source, details.series.id)
        model.library.value.book(origin)?.let { return list(it, listed) }
        val waiting = CompletableDeferred<Book>()
        val running = synchronized(adding) { adding[origin] ?: waiting.also { adding[origin] = it } }
        if (running !== waiting) return list(running.await(), listed)
        try {
            val book = addNew(details, origin, source, listed)
            waiting.complete(book)
            return book
        } catch (e: Exception) {
            waiting.completeExceptionally(e)
            throw e
        } finally {
            synchronized(adding) { adding.remove(origin) }
        }
    }

    private fun list(book: Book, listed: Boolean): Book {
        if (listed && !book.listed) model.update(book.id) { it.copy(listed = true) }
        return model.library.value.books.firstOrNull { it.id == book.id } ?: book
    }

    private suspend fun addNew(details: OnlineDetails, origin: BookOrigin, source: InstalledSource, listed: Boolean): Book = withContext(Dispatchers.IO) {
        val loaded = load(source)
        val id = UUID.randomUUID()
        val shell = Book(id = id, title = details.series.title, fileName = "", pageCount = 0)
        val folder = model.folder(shell)
        File(folder, "chapters").mkdirs()
        val (novel, _) = Novel(title = details.series.title, author = details.author, summary = details.summary, chapters = emptyList()).merge(details.chapters)
        novel.save(folder)
        details.series.cover?.let { url ->
            runCatching {
                val original = File(folder, "cover-original")
                loaded.image(RemoteImage.cover(source.id, url)).use { response -> original.outputStream().use { response.body.byteStream().copyTo(it) } }
                AppModel.makeCover(original, File(folder, "cover.jpg"))
                original.delete()
            }
        }
        val book = shell.copy(
            author = details.author, summary = details.summary,
            format = if (details.isNovel ?: source.info.novels) BookFormat.Novel else BookFormat.Series,
            fileName = source.info.name, pageCount = novel.chapters.size, origin = origin, listed = listed,
        )
        model.change { it.copy(books = it.books + book) }
        book
    }

    /** Asks the source for new chapters of a book. It gives the number of new chapters. */
    suspend fun refresh(book: Book): Int = withContext(Dispatchers.IO) {
        val source = installed(book) ?: return@withContext 0
        val origin = book.origin ?: return@withContext 0
        val details = load(source).details(OnlineSeries(origin.series, book.title))
        val folder = model.folder(book)
        val (novel, added) = Novel.load(folder).merge(details.chapters)
        if (added > 0) {
            novel.save(folder)
            model.update(book.id) { it.copy(pageCount = novel.chapters.size, finished = false) }
        }
        added
    }

    /**
     * The file of a chapter of an online novel. It downloads the chapter the first time, and keeps it in the folder of
     * the book, as in Asabunko.
     */
    suspend fun chapterFile(book: Book, index: Int): File = withContext(Dispatchers.IO) {
        val folder = model.folder(book)
        val novel = Novel.load(folder)
        val chapter = novel.chapters[index]
        val file = File(folder, chapter.path)
        if (file.isFile) return@withContext file
        val remote = chapter.remote ?: throw IOException("The file of the chapter is not on this device.")
        val origin = book.origin ?: throw IOException("The file of the chapter is not on this device.")
        val source = installed(book) ?: throw IOException("The source of this book is not installed. Add its list again in Settings, Sources.")
        val content = load(source).content(OnlineChapter(remote, chapter.title.orEmpty()), OnlineSeries(origin.series, book.title))
        val html = (content as? ChapterContent.Html)?.html ?: throw IOException("The source gave images for this chapter, not text.")
        val heading = chapter.title?.let { "<h2>${Xhtml.escape(it)}</h2>\n" }.orEmpty()
        val page = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>${Xhtml.escape(chapter.title.orEmpty())}</title></head><body>\n$heading$html\n</body></html>"
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.writeText(page)
        part.renameTo(file)
        file
    }

    // Search in all sources

    /** Searches some sources, six at the same time. [found] gets the first results of each source when it answers. */
    suspend fun search(query: String, sources: List<InstalledSource>, limit: Int = 12, found: suspend (String, List<OnlineSeries>) -> Unit) {
        val gate = Semaphore(6)
        coroutineScope {
            for (source in sources) {
                launch {
                    val items = gate.withPermit { runCatching { load(source).search(query, 1).items.take(limit) }.getOrDefault(emptyList()) }
                    found(source.id, items)
                }
            }
        }
    }
}
