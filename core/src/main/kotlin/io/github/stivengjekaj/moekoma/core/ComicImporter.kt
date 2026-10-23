package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.io.InputStream

/**
 * The files of a comic: an archive or a folder. The app reads an archive with libarchive
 * and a folder of the device with the Storage Access Framework.
 */
fun interface ComicFiles {
    /** Calls [body] for each regular file, in the order of the source. [open] operates only during the call. */
    fun forEach(body: (path: String, open: () -> InputStream) -> Unit)
}

/** A folder on the disk, for the tests and for a folder that the app already has. */
class FolderFiles(private val root: File) : ComicFiles {
    override fun forEach(body: (path: String, open: () -> InputStream) -> Unit) {
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            body(file.relativeTo(root).invariantSeparatorsPath) { file.inputStream() }
        }
    }
}

/** The result of the import of one comic. */
data class ImportedComic(val title: String, val pageCount: Int, val info: ComicInfo?)

/** The import failed. */
open class ImportException(message: String) : Exception(message) {
    /** The file is not a kind that the app can read. */
    class Unsupported(val extension: String) : ImportException("Moekoma cannot read .$extension files yet.")

    /** The archive or the folder has no images. */
    class NoPages : ImportException("The file has no images.")
}

/**
 * Copies the pages of a comic into a folder of the library.
 * The pages get the names 0001.jpg, 0002.png, and so on, in the natural order
 * of their paths. A name in an archive can hold "..", so the app never uses it as a path.
 */
object ComicImporter {
    val archiveExtensions = setOf("cbz", "zip", "cbr", "rar", "cb7", "7z", "cbt", "tar")
    val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "heif", "bmp", "tif", "tiff", "jxl",
    )

    /** Imports the files of a comic into [folder], which must not exist. On a fault, [folder] does not stay. */
    fun importComic(files: ComicFiles, fileName: String, folder: File): ImportedComic {
        val staging = File(folder, ".staging")
        try {
            if (!staging.mkdirs()) throw java.io.IOException("The folder of the book was not made.")
            val staged = mutableListOf<Pair<String, File>>()
            var info: ComicInfo? = null
            files.forEach { path, open ->
                when {
                    isComicInfo(path) -> info = open().use { ComicInfo.parse(it) }
                    isPage(path) -> {
                        val file = File(staging, staged.size.toString())
                        open().use { input -> file.outputStream().use { input.copyTo(it) } }
                        staged += path to file
                    }
                }
            }
            if (staged.isEmpty()) throw ImportException.NoPages()
            val order = staged.sortedWith { a, b -> NaturalOrder.compare(a.first, b.first) }
            order.forEachIndexed { index, (path, file) ->
                val target = File(folder, pageName(index, path.substringAfterLast('.', "")))
                if (!file.renameTo(target)) throw java.io.IOException("A page was not moved.")
            }
            staging.deleteRecursively()
            val title = info?.displayTitle ?: title(fromFileName = fileName)
            return ImportedComic(title, order.size, info)
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
    }

    /** The pages in a book folder, in the order of reading. */
    fun pages(folder: File): List<File> =
        (folder.list() ?: emptyArray())
            .filter { isPage(it) && !it.startsWith(".") && !it.startsWith("cover.") }
            .sorted()
            .map { File(folder, it) }

    internal fun pageName(index: Int, extension: String) = "%04d.%s".format(index + 1, extension.lowercase())

    fun isPage(path: String): Boolean =
        !isHidden(path) && path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in imageExtensions

    fun isComicInfo(path: String): Boolean =
        !isHidden(path) && path.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)

    /**
     * A file of macOS, such as __MACOSX/ or ._page.jpg, is not a page.
     * A path with a ".." part is not a page either, because it points out of the archive.
     */
    fun isHidden(path: String): Boolean =
        path.replace('\\', '/').split('/').any { it.startsWith(".") || it == "__MACOSX" }

    /** "My_Comic_v01.cbz" gives "My Comic v01". */
    fun title(fromFileName: String): String {
        val base = if ('.' in fromFileName) fromFileName.substringBeforeLast('.') else fromFileName
        return base.replace('_', ' ').trim().ifEmpty { fromFileName }
    }
}
