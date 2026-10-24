package io.github.stivengjekaj.moekoma.data

import android.content.Context
import io.github.stivengjekaj.moekoma.core.ChapterGroups
import io.github.stivengjekaj.moekoma.core.AidokuIndex
import io.github.stivengjekaj.moekoma.core.AidokuSource
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.BookOrigin
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.LnReaderIndex
import io.github.stivengjekaj.moekoma.core.LnReaderPlugin
import io.github.stivengjekaj.moekoma.core.MihonBackup
import io.github.stivengjekaj.moekoma.core.MihonRepo
import io.github.stivengjekaj.moekoma.core.BookCategory
import io.github.stivengjekaj.moekoma.core.SourceList
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.Xhtml
import io.github.stivengjekaj.moekoma.core.MihonStore
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.PaperbackExtension
import io.github.stivengjekaj.moekoma.core.PaperbackIndex
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
import kotlinx.coroutines.awaitAll
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
    private val paperbackWebView by lazy { PaperbackWebView(this.context) }

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
            // A series of a Mihon backup comes in when its source installs.
            runCatching { importPendingMihon() }
        }
        return links
    }

    /** The result of the import of a Mihon backup: the series that came in, and the sources that the others wait for. */
    data class MihonImport(val added: Int, val waiting: Map<String, Int>)

    private val pendingMihon = File(model.root, "mihon-pending.tachibk")

    /**
     * Imports a backup of Mihon: its categories, its series with the chapters that the user read, and its repositories of
     * extensions, which go into the source list. A series of a source that is not installed waits in a file, and comes in
     * when its source installs.
     */
    suspend fun importMihon(bytes: ByteArray): MihonImport {
        val content = withContext(Dispatchers.IO) { MihonBackup.parse(bytes) }
        withContext(Dispatchers.IO) {
            // A new library has no folder yet.
            pendingMihon.parentFile?.mkdirs()
            pendingMihon.writeBytes(bytes)
        }
        // The repositories of the backup go into the source list, and their sources install.
        val known = model.library.value.sources.map { it.url.trimEnd('/') }.toSet()
        val repositories = content.repositories.map { it.trimEnd('/') }.map { if (it.endsWith(".json")) it else "$it/index.min.json" }.filter { it !in known }
        if (repositories.isNotEmpty()) {
            val text = (SourceList.format(model.library.value.sources).lines() + repositories).filter { it.isNotBlank() }.joinToString("\n")
            save(text)
        }
        return importPendingMihon()
    }

    /** Imports the series of the waiting Mihon backup whose sources are installed now. */
    suspend fun importPendingMihon(): MihonImport = withContext(Dispatchers.IO) {
        if (!pendingMihon.isFile) return@withContext MihonImport(0, emptyMap())
        val content = runCatching { MihonBackup.parse(pendingMihon.readBytes()) }.getOrElse {
            pendingMihon.delete()
            return@withContext MihonImport(0, emptyMap())
        }
        // The categories of the backup that the library does not have yet come in, in their order.
        val names = model.library.value.categories.map { it.name }.toSet()
        val newCategories = content.categories.filter { it !in names }.map { BookCategory(name = it) }
        if (newCategories.isNotEmpty()) withContext(Dispatchers.Main) { model.change { it.copy(categories = it.categories + newCategories) } }
        val categories = model.library.value.categories
        var added = 0
        val waiting = linkedMapOf<String, Int>()
        for (entry in content.entries) {
            val source = installed.firstOrNull { it.info.id == entry.source && kind(it) == SourceKind.Mihon }
            if (source == null) {
                waiting.merge(entry.sourceName, 1, Int::plus)
                continue
            }
            if (model.library.value.book(originOf(source, entry.url)) != null) continue
            val (book, novel) = MihonBackup.book(entry, source.list, source.info.name, categories)
            val folder = model.folder(book)
            File(folder, "chapters").mkdirs()
            novel.save(folder)
            withContext(Dispatchers.Main) { model.change { it.copy(books = it.books + book) } }
            added++
            entry.cover?.let { url ->
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val original = File(folder, "cover-original")
                        load(source).image(RemoteImage.cover(source.id, url)).use { response -> original.outputStream().use { response.body.byteStream().copyTo(it) } }
                        AppModel.makeCover(original, File(folder, "cover.jpg"))
                        original.delete()
                    }
                }
            }
        }
        if (waiting.isEmpty()) pendingMihon.delete()
        MihonImport(added, waiting)
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
        // A book of a line that the user removed and added again finds its source.
        model.change { it.relinkOrphans().first }
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
        SourceKind.Paperback -> PaperbackIndex.parse(model.fetch(link.url, link), link.url)
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

    /**
     * Restores a backup, and installs the code of each source of the backup again. It gives the plan of the restore and
     * the names of the sources that did not install, such as with no network. A source of a kind that Moekoma cannot
     * run, or the source of a server, stays as it is.
     */
    suspend fun restore(backup: io.github.stivengjekaj.moekoma.core.Backup): Pair<io.github.stivengjekaj.moekoma.core.Backup.Plan, List<String>> {
        val plan = model.restore(backup)
        val failed = mutableListOf<String>()
        for (source in plan.library.installed) {
            val link = plan.library.sources.firstOrNull { it.id == source.list } ?: continue
            if (link.kind == null || link.kind == SourceKind.Suwayomi || link.kind == SourceKind.Opds) continue
            try {
                if (link.kind == SourceKind.Mihon) store(link)
                install(source.info, link)
            } catch (e: Exception) {
                failed += source.info.name
            }
        }
        return plan to failed
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
            SourceKind.LnReader, SourceKind.Aidoku, SourceKind.Paperback -> code.install(InstalledSource(link.id, info), http)
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
            SourceKind.LnReader, SourceKind.Aidoku, SourceKind.Paperback -> code.remove(source)
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
        SourceKind.Paperback -> {
            val file = code.file(source)
            if (!file.isFile) throw MihonException("The code of ${source.info.name} is not on this device. Save the source list again.")
            val extension = PaperbackExtension.load(
                source.info, file.readText(), code.settings(source), BrowserChecks.userAgent(context), networkFetcher(), paperbackWebView::run,
            )
            PaperbackSource(source, extension, http)
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

    /** Gets each book with new chapters and the index of its first new chapter, such as for the downloads of new chapters. */
    var onNewChapters: (Book, Int) -> Unit = { _, _ -> }

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
            // The new chapters are at the end of the book. They go into the updates.
            val first = novel.chapters.size - added
            model.change { library -> library.record((first until novel.chapters.size).map { it to novel.chapters[it].title }, book.id) }
            model.library.value.book(book.id)?.let { onNewChapters(it, first) }
        }
        added
    }

    /**
     * Moves a book of the library to another source of the same title. The place of the user goes to the chapter with
     * the same number in the new source. The chapters of the old source leave the device. The caller removes the
     * downloads of the book first.
     */
    suspend fun move(book: Book, to: InstalledSource, details: OnlineDetails): Book = withContext(Dispatchers.IO) {
        val folder = model.folder(book)
        val old = runCatching { Novel.load(folder) }.getOrNull()
        val (fresh, _) = Novel(
            title = old?.title ?: book.title, author = details.author ?: old?.author, summary = details.summary ?: old?.summary, chapters = emptyList(),
        ).merge(details.chapters)
        File(folder, "chapters").deleteRecursively()
        File(folder, "chapters").mkdirs()
        fresh.save(folder)
        val place = ChapterGroups.place(book.page, old?.chapters.orEmpty().map { it.title.orEmpty() }, details.chapters.map { it.title })
        model.update(book.id) { moved ->
            moved.copy(
                origin = originOf(to, details.series.id), fileName = to.info.name, pageCount = fresh.chapters.size, finished = false,
                page = if (moved.lastRead != null) place else moved.page, position = if (moved.lastRead != null) 0.0 else moved.position,
            )
        }
        model.library.value.books.first { it.id == book.id }
    }

    /** The result of a check for new chapters: the new chapters, the titles of the books whose source did not answer, and each book with new chapters. */
    data class Check(val added: Int, val failedTitles: List<String>, val found: List<Pair<Book, Int>>) {
        val failed: Int get() = failedTitles.size

        /** The text for the user. With [quiet], a check with no news and no faults gives null. */
        fun message(quiet: Boolean = false): String? {
            if (added == 0 && failed == 0) return if (quiet) null else "No new chapters."
            val news = "$added new chapter${if (added == 1) "" else "s"}."
            if (failed == 0) return news
            // The names show which source to look at. A long list names three books.
            val names = failedTitles.take(3).joinToString(", ") + if (failed > 3) ", and ${failed - 3} more" else ""
            return "$news $failed book${if (failed == 1) "" else "s"} did not update: $names."
        }
    }

    /**
     * Asks the source of each online book for new chapters. Four books ask their sources at the same time, so a large
     * library does not take minutes. With [listedOnly], only the books of the read list ask.
     */
    suspend fun refreshAll(listedOnly: Boolean = false): Check = coroutineScope {
        val books = model.library.value.books.filter { it.origin != null && (!listedOnly || it.listed) }
        val gate = Semaphore(4)
        val results = books.map { book -> async { gate.withPermit { book to runCatching { refresh(book) }.getOrNull() } } }.awaitAll()
        Check(
            added = results.sumOf { it.second ?: 0 },
            failedTitles = results.filter { it.second == null }.map { it.first.title },
            found = results.mapNotNull { (book, added) -> if (added != null && added > 0) book to added else null },
        )
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
