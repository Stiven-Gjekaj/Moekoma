package io.github.stivengjekaj.moekoma.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import io.github.stivengjekaj.moekoma.core.FolderScan
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.BookImporter
import io.github.stivengjekaj.moekoma.core.ImportSource
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.core.ComicFiles
import io.github.stivengjekaj.moekoma.core.ComicImporter
import io.github.stivengjekaj.moekoma.core.ImportException
import io.github.stivengjekaj.moekoma.core.Library
import io.github.stivengjekaj.moekoma.core.LibraryStore
import io.github.stivengjekaj.moekoma.core.NewerFormException
import io.github.stivengjekaj.moekoma.core.ReadingMode
import io.github.stivengjekaj.moekoma.core.SourceKind
import io.github.stivengjekaj.moekoma.core.SourceLink
import io.github.stivengjekaj.moekoma.core.SourceList
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.zhanghai.android.libarchive.ArchiveException
import java.io.File
import java.util.UUID

/** The number of the file that the app imports now, and the number of all files. */
data class ImportProgress(val done: Int, val total: Int)

/** The state of the app: the library and the files of each book. */
class AppModel(
    private val context: Context,
    private val scope: CoroutineScope,
    val root: File = File(context.filesDir, "Moekoma"),
    private val http: OkHttpClient = OkHttpClient(),
    private val secrets: Secrets = Secrets(context),
) {
    private val libraryFile = File(root, "library.json")
    private val booksFolder = File(root, "Books")
    private val state = MutableStateFlow(Library())
    private val saving = Mutex()
    private var saveJob: Job? = null

    val library: StateFlow<Library> = state.asStateFlow()

    /** The fault of the last load or import, for an alert. */
    val fault = MutableStateFlow<String?>(null)

    private val importState = MutableStateFlow<ImportProgress?>(null)
    val importing: StateFlow<ImportProgress?> = importState.asStateFlow()

    private val lockedState = MutableStateFlow(false)

    /**
     * True when the library file did not load. Then no save writes over it, until the user
     * restores a backup or starts a new library.
     */
    val libraryLocked: StateFlow<Boolean> = lockedState.asStateFlow()

    init {
        try {
            state.value = LibraryStore.load(libraryFile)
        } catch (e: NewerFormException) {
            lockedState.value = true
            fault.value = "A newer version of Moekoma wrote this library. Update the app to open it. Moekoma does not change the file."
        } catch (e: Exception) {
            lockedState.value = true
            val copy = runCatching { LibraryStore.keepBroken(libraryFile) }.getOrNull()
            fault.value = "The library file cannot be read. Moekoma does not change it" + (copy?.let { ", and keeps a copy as ${it.name}" } ?: "") +
                ". Start a new library in Settings."
        }
    }

    /** Starts an empty library in place of a file that did not load. The copy of the old file stays. */
    fun startNewLibrary() {
        lockedState.value = false
        change { Library() }
    }

    // Books

    fun folder(book: Book) = File(booksFolder, book.id.toString().uppercase())

    fun cover(book: Book) = File(folder(book), "cover.jpg")

    fun pages(book: Book): List<File> = ComicImporter.pages(folder(book))

    /** The PDF of a book, which the reader opens and closes. */
    fun pdf(book: Book): File = File(folder(book), PdfImporter.FILE_NAME)

    fun update(id: UUID, change: (Book) -> Book) = change { it.update(id, change) }

    fun setDefaultMode(mode: ReadingMode) = change { it.copy(defaultMode = mode) }

    fun delete(book: Book) {
        change { library -> library.copy(books = library.books.filter { it.id != book.id }) }
        scope.launch(Dispatchers.IO) { folder(book).deleteRecursively() }
    }

    // Import

    /** Imports each file. A fault stops only its own file. */
    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importAll(uris.map { uri -> { importDocument(uri) } })
    }

    /** Imports a folder of images that the user chose. */
    fun importFolder(tree: Uri) {
        importAll(listOf {
            val files = DocumentTreeFiles(context.contentResolver, tree)
            val name = files.name()
            name to runCatching { importComic(files, name) }
        })
    }

    private fun importAll(jobs: List<suspend () -> Pair<String, Result<Book>>>) = scope.launch {
        importState.value = ImportProgress(0, jobs.size)
        val failed = mutableListOf<String>()
        jobs.forEachIndexed { index, job ->
            val (name, result) = withContext(Dispatchers.IO) { job() }
            result.onSuccess { book -> change { it.copy(books = it.books + book) } }
                .onFailure { failed += "$name: ${describe(it)}" }
            importState.value = ImportProgress(index + 1, jobs.size)
        }
        importState.value = null
        if (failed.isNotEmpty()) fault.value = "Some files were not imported.\n\n" + failed.joinToString("\n")
    }

    private fun importDocument(uri: Uri): Pair<String, Result<Book>> {
        val name = displayName(uri)
        val ext = BookImporter.extension(name)
        return name to runCatching {
            if (ext !in BookImporter.supportedExtensions) throw ImportException.Unsupported(ext.ifEmpty { "?" })
            val resolver = context.contentResolver
            val source = object : ImportSource {
                override val fileName = name
                override fun files() = ArchiveFiles { resolver.openFileDescriptor(uri, "r") ?: throw java.io.IOException("$name cannot be opened.") }
                override fun bytes() = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("$name cannot be opened.")
            }
            try {
                importBook(source)
            } catch (e: ArchiveException) {
                // A file that another app shares can come as a stream, which libarchive cannot seek.
                // A copy in the cache can.
                val copy = File(context.cacheDir, "import-${UUID.randomUUID()}.$ext")
                try {
                    resolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } ?: throw e
                    importBook(object : ImportSource {
                        override val fileName = name
                        override fun files() = ArchiveFiles { ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY) }
                        override fun bytes() = copy.readBytes()
                    })
                } finally {
                    copy.delete()
                }
            }
        }
    }

    private fun importBook(source: ImportSource): Book {
        val id = UUID.randomUUID()
        val folder = File(booksFolder, id.toString().uppercase())
        if (BookImporter.extension(source.fileName) == BookImporter.PDF) {
            // Most PDFs are western documents and scans, so they open from the left.
            val pdf = PdfImporter.importBook(source.bytes(), source.fileName, folder)
            return Book(
                id = id, title = pdf.title, author = pdf.author, format = BookFormat.Pdf,
                fileName = source.fileName, pageCount = pdf.pageCount, mode = ReadingMode.LeftToRight,
            )
        }
        val imported = BookImporter.importBook(source, folder)
        imported.cover?.let { runCatching { makeCover(it, File(folder, "cover.jpg")) } }
        return Book(
            id = id, title = imported.title, author = imported.author, summary = imported.summary,
            format = imported.format, fileName = source.fileName, pageCount = imported.pageCount, mode = imported.mode,
        )
    }

    /** Imports a book that a scan of a watched folder found. The book goes into the library at once. */
    suspend fun importFound(found: FolderScan.Found, tree: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val node = found.node as SafNode
        val result = when (found) {
            is FolderScan.Found.Book -> importDocument(DocumentsContract.buildDocumentUriUsingTree(tree, node.document)).second
            is FolderScan.Found.Images -> runCatching { importComic(DocumentTreeFiles(context.contentResolver, tree, node.document), node.name) }
        }
        withContext(Dispatchers.Main) { result.onSuccess { book -> change { it.copy(books = it.books + book) } } }
        result
    }

    // Sources

    /**
     * Replaces the source list with the text of the paste box. A password goes to the encrypted store.
     * A line with a user name and no password keeps the password that the store has.
     */
    fun saveSources(text: String): List<SourceLink> {
        val old = state.value.sources
        val lines = SourceList.parse(text, old)
        val kept = lines.map { it.link.id }.toSet()
        for (link in old) if (link.id !in kept) secrets.setPassword(link.id, null)
        for (line in lines) line.password?.let { secrets.setPassword(line.link.id, it) }
        change { library -> library.copy(sources = lines.map { it.link }, installed = library.installed.filter { it.list in kept }) }
        return lines.map { it.link }
    }

    /** Keeps the kind and the name that the app found when it read a source. */
    fun learn(id: UUID, kind: SourceKind?, name: String?) = change { library ->
        library.copy(sources = library.sources.map { link ->
            if (link.id != id) link else link.copy(kind = kind ?: link.kind, name = name?.ifEmpty { null } ?: link.name)
        })
    }

    /**
     * The headers of a request to a source: the user name and the password of its line.
     * The password goes only to the host of the source. A link to another host gets no password.
     */
    fun headers(url: String, source: SourceLink?): Map<String, String> {
        val user = source?.username?.ifEmpty { null } ?: return emptyMap()
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (host == null || host != source.host) return emptyMap()
        return mapOf("Authorization" to Credentials.basic(user, secrets.password(source.id) ?: ""))
    }

    /** The bytes of a URL of a source, with its password. */
    suspend fun fetch(url: String, source: SourceLink?): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Accept", "application/atom+xml, application/opds+json, application/json;q=0.9, */*;q=0.8")
            .apply { headers(url, source).forEach { (key, value) -> header(key, value) } }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("The server answered ${response.code}.")
            response.body.bytes()
        }
    }

    /** Downloads a book from a source and imports it into the library. */
    suspend fun download(url: String, source: SourceLink?, title: String, fileExtension: String): Boolean {
        val bytes = try {
            fetch(url, source)
        } catch (e: Exception) {
            fault.value = "The book was not downloaded: ${e.message}"
            return false
        }
        val safe = title.replace('/', '-').replace(':', '-').ifEmpty { "Book" }
        val name = "$safe.$fileExtension"
        val file = File(context.cacheDir, "download-${UUID.randomUUID()}.$fileExtension").apply { writeBytes(bytes) }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                importBook(object : ImportSource {
                    override val fileName = name
                    override fun files() = ArchiveFiles { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
                    override fun bytes() = file.readBytes()
                })
            }.also { file.delete() }
        }
        result.onSuccess { book -> change { it.copy(books = it.books + book) } }
            .onFailure { fault.value = "$name: ${describe(it)}" }
        return result.isSuccess
    }

    /** The chapters of a novel, or null when its file does not load. */
    fun novel(book: Book): Novel? = runCatching { Novel.load(folder(book)) }.getOrNull()

    /** Imports a folder of images as a comic. */
    private fun importComic(files: ComicFiles, name: String): Book {
        val id = UUID.randomUUID()
        val folder = File(booksFolder, id.toString().uppercase())
        val comic = ComicImporter.importComic(files, name, folder)
        ComicImporter.pages(folder).firstOrNull()?.let { runCatching { makeCover(it, File(folder, "cover.jpg")) } }
        return Book(
            id = id, title = comic.title, author = comic.info?.writer, summary = comic.info?.summary,
            format = BookFormat.Comic, fileName = name, pageCount = comic.pageCount, mode = comic.info?.readingMode,
        )
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private fun describe(error: Throwable): String = when (error) {
        is ImportException -> error.message ?: "The file cannot be imported."
        is ArchiveException -> "The archive is damaged or locked with a password."
        is java.io.IOException -> "The archive is damaged or locked with a password."
        else -> error.message ?: error.javaClass.simpleName
    }

    // Store

    /**
     * Writes the library a short time after the last change, off the main thread. A page turn changes
     * the library, so many changes become one write.
     */
    internal fun change(change: (Library) -> Library) {
        state.value = change(state.value)
        if (lockedState.value) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY)
            write(state.value)
        }
    }

    /** Writes the library now, such as when the app goes to the background. */
    fun flush() {
        val job = saveJob ?: return
        if (lockedState.value || !job.isActive) return
        job.cancel()
        saveJob = null
        val snapshot = state.value
        scope.launch { write(snapshot) }
    }

    private suspend fun write(snapshot: Library) = withContext(Dispatchers.IO) {
        saving.withLock {
            try {
                LibraryStore.save(snapshot, libraryFile)
            } catch (e: Exception) {
                fault.value = "The library was not saved: ${e.message}"
            }
        }
    }

    companion object {
        private const val SAVE_DELAY = 400L

        /** Writes a JPEG of the first page with the long side at most [maxPixels]. */
        fun makeCover(page: File, cover: File, maxPixels: Int = 600) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(page.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPixels) sample *= 2
            val bitmap = BitmapFactory.decodeFile(page.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            val scale = maxPixels.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
            cover.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
    }
}
