package io.github.stivengjekaj.moekoma.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stivengjekaj.moekoma.MoekomaApp
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.ChapterDownload
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.UuidSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The downloads of the chapters of online series, one chapter at a time, as in Asabunko. The queue stays in a file, and
 * a worker of WorkManager runs it, so the downloads go on when the app closes.
 */
class Downloads(private val context: Context, private val model: AppModel, private val sources: Sources, root: File) {
    @Serializable
    data class Job(@Serializable(with = UuidSerializer::class) val book: UUID, val chapter: Int)

    data class State(
        /** The chapters that wait, in order. The first one downloads now. */
        val queue: List<Job> = emptyList(),
        /** The pages of the chapter that downloads now. */
        val progress: Pair<Int, Int>? = null,
        /** The chapters that did not download, with the reason. */
        val failed: Map<Job, String> = emptyMap(),
        /** A number that changes when a chapter finishes, so that the screens read the files again. */
        val changes: Int = 0,
    )

    private val file = File(root, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val stateFlow = MutableStateFlow(State(queue = load()))
    val state: StateFlow<State> = stateFlow.asStateFlow()
    private val running = Mutex()
    @Volatile private var current: kotlinx.coroutines.Job? = null

    private fun load(): List<Job> = runCatching { json.decodeFromString(ListSerializer(Job.serializer()), file.readText()) }.getOrDefault(emptyList())

    private fun save(queue: List<Job>) {
        runCatching {
            file.parentFile?.mkdirs()
            val part = File(file.path + ".part")
            part.writeText(json.encodeToString(ListSerializer(Job.serializer()), queue))
            part.renameTo(file)
        }
    }

    private fun change(body: (State) -> State) {
        val next = stateFlow.updateAndGet(body)
        save(next.queue)
    }

    private fun MutableStateFlow<State>.updateAndGet(body: (State) -> State): State {
        while (true) {
            val before = value
            val after = body(before)
            if (compareAndSet(before, after)) return after
        }
    }

    fun enqueue(jobs: List<Job>) {
        if (jobs.isEmpty()) return
        change { state -> state.copy(queue = state.queue + jobs.filter { it !in state.queue }, failed = state.failed - jobs.toSet()) }
        start()
    }

    /** Starts the worker, which runs the queue. A worker that runs already goes on. */
    fun start() {
        if (state.value.queue.isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build(),
        )
    }

    /** Stops the downloads of a book: the chapters that wait, and the chapter that downloads now. */
    fun cancel(book: UUID) {
        val first = state.value.queue.firstOrNull()
        change { state -> state.copy(queue = state.queue.filter { it.book != book || it == first }, failed = state.failed.filterKeys { it.book != book }) }
        if (first?.book == book) current?.cancel()
    }

    /** Downloads the chapters of a book that did not download, again. */
    fun retry(book: UUID) = enqueue(state.value.failed.keys.filter { it.book == book }.sortedBy { it.chapter })

    fun failedCount(book: UUID) = state.value.failed.keys.count { it.book == book }

    /** Runs the queue until it is empty. The worker calls it, and one run goes at a time. */
    suspend fun run(progress: suspend (String, Int, Int) -> Unit) = running.withLock {
        coroutineScope {
            while (true) {
                val job = state.value.queue.firstOrNull() ?: break
                stateFlow.update { it.copy(progress = 0 to 0) }
                val book = model.library.value.books.firstOrNull { it.id == job.book }
                var fault: String? = null
                // The job runs in its own coroutine, so that a cancel stops only this chapter.
                val work = launch {
                    if (book != null) fault = download(book, job.chapter) { done, total ->
                        stateFlow.update { it.copy(progress = done to total) }
                        launch { progress(book.title, done, total) }
                    }
                }
                current = work
                work.join()
                current = null
                val failure = fault
                // A book that the user deleted while its chapter downloaded leaves no folder.
                if (book != null && model.library.value.books.none { it.id == book.id }) withContext(Dispatchers.IO) { model.folder(book).deleteRecursively() }
                change { state ->
                    state.copy(
                        queue = state.queue.drop(1), progress = null, changes = state.changes + 1,
                        failed = if (failure != null) state.failed + (job to failure) else state.failed,
                    )
                }
            }
        }
    }

    /**
     * Asks the source for the pages of a chapter, and keeps them on the device. A chapter of a novel is one file. It gives
     * the fault, or null when the chapter is on the device.
     */
    private suspend fun download(book: Book, index: Int, progress: (Int, Int) -> Unit): String? {
        try {
            withContext(Dispatchers.IO) {
                val folder = model.folder(book)
                val novel = Novel.load(folder)
                val chapter = novel.chapters.getOrNull(index) ?: return@withContext
                if (book.format == BookFormat.Novel) {
                    sources.chapterFile(book, index)
                    progress(1, 1)
                    return@withContext
                }
                if (ChapterDownload.isDownloaded(chapter, folder)) return@withContext
                val remote = chapter.remote ?: return@withContext
                val origin = book.origin ?: return@withContext
                val installed = sources.installed(book) ?: throw IOException("The source of this book is not installed.")
                val source = sources.load(installed)
                val content = source.content(OnlineChapter(remote, chapter.title.orEmpty()), OnlineSeries(origin.series, book.title))
                val pages = (content as? ChapterContent.Pages)?.pages ?: throw IOException("The source gave text for this chapter, not pages.")
                ChapterDownload.download(pages.size, chapter, folder, { page ->
                    source.image(pages[page]).use { response ->
                        ChapterDownload.Page(response.body.bytes(), response.header("Content-Type"), response.request.url.toString())
                    }
                }, progress)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        }
        return null
    }

    // The chapters on the device

    /** The pages of a chapter on the device, or null when it did not download. */
    fun pages(book: Book, index: Int): List<File>? {
        val folder = model.folder(book)
        val chapter = model.novel(book)?.chapters?.getOrNull(index) ?: return null
        return ChapterDownload.pages(chapter, folder).ifEmpty { null }
    }

    /** One check of the disk for one chapter: the file of a chapter of a novel, or the pages of a comic chapter. */
    fun isDownloaded(book: Book, folder: File, chapter: Novel.Chapter): Boolean =
        if (book.format == BookFormat.Novel) File(folder, chapter.path).isFile else ChapterDownload.isDownloaded(chapter, folder)

    /** The chapters of a book that are on the device, by their index. The book and its folder come one time. */
    fun downloaded(book: Book): Set<Int> {
        val folder = model.folder(book)
        val novel = model.novel(book) ?: return emptySet()
        return novel.chapters.indices.filter { isDownloaded(book, folder, novel.chapters[it]) }.toSet()
    }

    /** True when a chapter of an online book is on the device. It stops at the first one. */
    fun hasDownloads(book: Book): Boolean {
        if (book.origin == null) return false
        val folder = model.folder(book)
        val novel = model.novel(book) ?: return false
        return novel.chapters.any { isDownloaded(book, folder, it) }
    }

    /** Downloads each chapter of a book from [start] that is not on the device. */
    fun downloadChapters(book: Book, start: Int = 0) {
        val folder = model.folder(book)
        val novel = model.novel(book) ?: return
        enqueue(novel.chapters.indices.filter { it >= start && !isDownloaded(book, folder, novel.chapters[it]) }.map { Job(book.id, it) })
    }

    /** Removes the downloaded chapters of a book before [end], which are the chapters that the user read. */
    fun remove(book: Book, end: Int = Int.MAX_VALUE) {
        val folder = model.folder(book)
        val novel = model.novel(book) ?: return
        novel.chapters.forEachIndexed { index, chapter ->
            if (index >= end) return@forEachIndexed
            if (book.format == BookFormat.Novel) File(folder, chapter.path).delete() else ChapterDownload.remove(chapter, folder)
        }
        stateFlow.update { it.copy(changes = it.changes + 1) }
    }

    companion object {
        const val WORK = "chapter-downloads"
        const val CHANNEL = "downloads"
    }
}

/** Runs the downloads of the chapters, with a notification of the progress, also when the app closes. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun info(title: String, done: Int, total: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(NotificationChannel(Downloads.CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, Downloads.CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(if (total > 0) "Page $done of $total" else "Getting the pages")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION, notification)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Downloading chapters", 0, 0)

    override suspend fun doWork(): Result {
        val downloads = (applicationContext as MoekomaApp).container.downloads
        runCatching { setForeground(getForegroundInfo()) }
        downloads.run { title, done, total -> runCatching { setForeground(info(title, done, total)) } }
        return Result.success()
    }

    private companion object {
        const val NOTIFICATION = 7
    }
}
