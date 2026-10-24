package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * The pages of a chapter of an online series on the device, so that the chapter opens with no network. As in Asabunko.
 * The pages of a chapter are in a folder next to the file of its chapter: chapters/00001/0001.jpg.
 */
object ChapterDownload {
    /** One page that came from the source: its bytes and the type of its answer. */
    class Page(val bytes: ByteArray, val mimeType: String?, val url: String)

    private const val COMPLETE = ".complete"

    /** The folder of the pages of a chapter, from its path in novel.json. */
    fun folder(chapter: Novel.Chapter, book: File): File = File(book, chapter.path.substringBeforeLast('.'))

    /** The pages of a chapter that downloaded, in their order. A chapter that did not finish has no pages. */
    fun pages(chapter: Novel.Chapter, book: File): List<File> {
        val folder = folder(chapter, book)
        if (!File(folder, COMPLETE).isFile) return emptyList()
        return ComicImporter.pages(folder)
    }

    fun isDownloaded(chapter: Novel.Chapter, book: File): Boolean = File(folder(chapter, book), COMPLETE).isFile

    /** The name of a page: its number from 1, and the extension of its kind of image. */
    fun pageName(index: Int, mimeType: String?, url: String): String {
        val ext = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            "image/heic" -> "heic"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()
                .takeIf { it in ComicImporter.imageExtensions } ?: "jpg"
        }
        return "%04d.%s".format(index + 1, ext)
    }

    /** True when the bytes start as an image: JPEG, PNG, GIF, WebP, BMP, or an AVIF or HEIC file. A page of HTML is not one. */
    fun isImage(bytes: ByteArray): Boolean {
        fun at(offset: Int, vararg values: Int) = bytes.size >= offset + values.size && values.indices.all { (bytes[offset + it].toInt() and 0xFF) == values[it] }
        return at(0, 0xFF, 0xD8, 0xFF) || at(0, 0x89, 0x50, 0x4E, 0x47) || at(0, 0x47, 0x49, 0x46, 0x38) ||
            (at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50)) || at(0, 0x42, 0x4D) || at(4, 0x66, 0x74, 0x79, 0x70)
    }

    /**
     * Downloads the [count] pages that [fetch] gives into the folder of the chapter. The folder gets its mark of
     * completion only when each page came, so a chapter that stops half way does not open as a downloaded chapter.
     * Each page has three attempts.
     */
    suspend fun download(
        count: Int, chapter: Novel.Chapter, book: File, fetch: suspend (Int) -> Page, progress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        val folder = folder(chapter, book)
        val staging = File(folder.parentFile, folder.name + ".part")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw java.io.IOException("The folder of the chapter cannot be made.")
        try {
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                var attempt = 0
                var page: Page? = null
                while (page == null) {
                    try {
                        val got = fetch(index)
                        // A site can answer with a page of HTML, such as a check or a refusal of hotlinks.
                        if (!isImage(got.bytes)) throw java.io.IOException("Page ${index + 1} is not an image.")
                        page = got
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        attempt++
                        if (attempt >= 3) throw e
                        delay(attempt * 500L)
                    }
                }
                File(staging, pageName(index, page.mimeType, page.url)).writeBytes(page.bytes)
                progress(index + 1, count)
            }
            File(staging, COMPLETE).writeBytes(ByteArray(0))
            folder.deleteRecursively()
            if (!staging.renameTo(folder)) throw java.io.IOException("The chapter was not saved.")
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** Removes the pages of a chapter. */
    fun remove(chapter: Novel.Chapter, book: File) {
        folder(chapter, book).deleteRecursively()
    }
}
