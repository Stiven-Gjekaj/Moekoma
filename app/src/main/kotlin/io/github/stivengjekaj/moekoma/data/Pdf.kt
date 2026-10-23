package io.github.stivengjekaj.moekoma.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import io.github.stivengjekaj.moekoma.core.ComicImporter
import io.github.stivengjekaj.moekoma.core.ImportException
import io.github.stivengjekaj.moekoma.core.PdfMetadata
import java.io.Closeable
import java.io.File
import java.io.IOException

/** The import of a PDF failed. */
sealed class PdfException(message: String) : ImportException(message) {
    class NotPdf : PdfException("The file is not a PDF.")

    /** The PDF needs a password to open. */
    class Locked : PdfException("The PDF needs a password. Remove the password, then import it again.")

    class NoPages : PdfException("The PDF has no pages.")
}

/** Copies a PDF into the folder of a book as book.pdf, with a cover from its first page. */
object PdfImporter {
    const val FILE_NAME = "book.pdf"

    data class Info(val title: String, val author: String?, val pageCount: Int)

    /** Imports the bytes of a PDF into [folder], which must not exist. On a fault, [folder] does not stay. */
    fun importBook(data: ByteArray, fileName: String, folder: File): Info {
        try {
            folder.mkdirs()
            val file = File(folder, FILE_NAME)
            file.writeBytes(data)
            val count = PdfDocument(file).use { document ->
                if (document.pageCount == 0) throw PdfException.NoPages()
                runCatching { document.writeCover(File(folder, "cover.jpg")) }
                document.pageCount
            }
            val meta = PdfMetadata.read(data)
            return Info(meta.title ?: ComicImporter.title(fileName), meta.author, count)
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
    }
}

/**
 * A PDF that the reader shows. The renderer of Android draws one page at a time,
 * so each draw waits for the one before it.
 */
class PdfDocument(val file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(descriptor)
    } catch (e: SecurityException) {
        descriptor.close()
        throw PdfException.Locked()
    } catch (e: IOException) {
        descriptor.close()
        throw PdfException.NotPdf()
    }

    val pageCount: Int get() = renderer.pageCount

    /** Draws the page on white, inside a box of [maxWidth] by [maxHeight] pixels. */
    fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap = synchronized(this) {
        renderer.openPage(index).use { page ->
            val scale = minOf(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Writes the first page as a JPEG with the long side at most [maxPixels]. */
    fun writeCover(cover: File, maxPixels: Int = 600) {
        val bitmap = render(0, maxPixels, maxPixels)
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    override fun close() = synchronized(this) {
        renderer.close()
        descriptor.close()
    }
}

/** One page of a PDF, for the image loader of the reader. */
data class PdfPage(val document: PdfDocument, val index: Int)

/** Draws a page of a PDF at the size that the reader asks for. */
class PdfPageFetcher(private val page: PdfPage, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { 1440 }
        val height = options.size.height.pxOrElse { 2048 }
        val bitmap = page.document.render(page.index, width, height)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<PdfPage> {
        override fun create(data: PdfPage, options: Options, imageLoader: ImageLoader) = PdfPageFetcher(data, options)
    }
}

/** The key of a page in the memory cache of the images. */
class PdfPageKeyer : Keyer<PdfPage> {
    override fun key(data: PdfPage, options: Options) = "${data.document.file.path}#${data.index}"
}
