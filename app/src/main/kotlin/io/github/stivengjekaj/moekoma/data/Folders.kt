package io.github.stivengjekaj.moekoma.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stivengjekaj.moekoma.MoekomaApp
import io.github.stivengjekaj.moekoma.core.FolderScan
import io.github.stivengjekaj.moekoma.core.TreeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/** A folder of the device that the app watches. */
@Serializable
data class WatchedFolder(val uri: String, val name: String)

/** The folders and the documents that the app imported or tried, so that a scan imports a book one time. */
@Serializable
data class FolderState(val folders: List<WatchedFolder> = emptyList(), val seen: List<String> = emptyList())

/** The result of the last scan, for Settings. */
data class ScanResult(val added: Int, val failed: List<String>, val missing: List<String>)

/**
 * Watches the folders that the user adds. A scan imports each new book of a folder. A book that the user
 * deletes does not come back, because the app keeps each document that it saw.
 */
class Folders(private val context: Context, private val model: AppModel, root: File, private val scope: CoroutineScope) {
    private val file = File(root, "folders.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())
    private val scanning = Mutex()
    private val last = MutableStateFlow<ScanResult?>(null)
    private var lastScan = 0L

    val folders: StateFlow<FolderState> = state.asStateFlow()
    val lastResult: StateFlow<ScanResult?> = last.asStateFlow()
    val busy = MutableStateFlow(false)

    private fun load(): FolderState = runCatching { json.decodeFromString(FolderState.serializer(), file.readText()) }.getOrDefault(FolderState())

    private fun save(next: FolderState) {
        state.value = next
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(FolderState.serializer(), next))
    }

    /** Adds a folder that the user chose, with the permission to read it after a restart, and scans it. */
    fun add(tree: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (state.value.folders.any { it.uri == tree.toString() }) return
        val name = runCatching { DocumentTreeFiles(context.contentResolver, tree).name() }.getOrDefault("Folder")
        save(state.value.copy(folders = state.value.folders + WatchedFolder(tree.toString(), name)))
        schedule()
        scope.launch { scan() }
    }

    /** Stops the watch of a folder. Its books stay in the library. */
    fun remove(folder: WatchedFolder) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        save(state.value.copy(folders = state.value.folders - folder))
        schedule()
    }

    /** Scans when the app opens, at most one time in ten minutes. */
    suspend fun scanSoon() {
        if (System.currentTimeMillis() - lastScan < 10 * 60 * 1000) return
        scan(report = false)
    }

    /**
     * Imports each new book of each folder. A scan that the user did not start, such as the scan of the
     * worker, shows its result only when it found something, so that it does not hide the result of the user.
     */
    suspend fun scan(report: Boolean = true): ScanResult = scanning.withLock {
        busy.value = true
        lastScan = System.currentTimeMillis()
        try {
            var added = 0
            val failed = mutableListOf<String>()
            val missing = mutableListOf<String>()
            for (folder in state.value.folders) {
                val tree = Uri.parse(folder.uri)
                val found = withContext(Dispatchers.IO) {
                    runCatching {
                        val root = DocumentsContract.getTreeDocumentId(tree)
                        FolderScan.find(SafNode(context, tree, root, folder.name, true), state.value.seen.toSet())
                    }.getOrNull()
                }
                if (found == null) {
                    missing += folder.name
                    continue
                }
                for (item in found) {
                    val result = model.importFound(item, tree)
                    result.onSuccess { added++ }.onFailure { failed += "${item.node.name}: ${it.message}" }
                    // A file that fails stays seen, so that a scan does not try it each time. The user can import it by hand.
                    save(state.value.copy(seen = state.value.seen + item.node.id))
                }
            }
            ScanResult(added, failed, missing).also { if (report || added > 0 || failed.isNotEmpty()) last.value = it }
        } finally {
            busy.value = false
        }
    }

    /** Scans in the background two times a day while the app watches a folder. */
    fun schedule() {
        val work = WorkManager.getInstance(context)
        if (state.value.folders.isEmpty()) {
            work.cancelUniqueWork(WORK)
        } else {
            work.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FolderScanWorker>(12, TimeUnit.HOURS)
                    // The app scans when it opens, so the first scan of the worker waits too.
                    .setInitialDelay(12, TimeUnit.HOURS)
                    .build(),
            )
        }
    }

    private companion object {
        const val WORK = "folder-scan"
    }
}

/** A document of a folder of the device. */
class SafNode(
    private val context: Context,
    val tree: Uri,
    val document: String,
    override val name: String,
    override val isFolder: Boolean,
) : TreeNode {
    override val id: String get() = DocumentsContract.buildDocumentUriUsingTree(tree, document).toString()

    override fun children(): List<TreeNode> {
        if (!isFolder) return emptyList()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, document)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val children = mutableListOf<TreeNode>()
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                children += SafNode(context, tree, cursor.getString(0), cursor.getString(1) ?: "", cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return children
    }
}

/** The scan of the folders in the background. */
class FolderScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MoekomaApp).container
        container.folders.scan(report = false)
        return Result.success()
    }
}
