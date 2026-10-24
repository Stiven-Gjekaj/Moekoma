package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Makes a file of a book in the library, to share it, as in Asabunko: a CBZ of the pages of a comic, the PDF of a PDF,
 * or the EPUB of a novel that came from an EPUB. Other books have no file to share.
 */
object BookExport {
    /** The name of the PDF file in the folder of a PDF book, as the app keeps it. */
    const val PDF_NAME = "book.pdf"

    fun canShare(format: BookFormat, folder: File): Boolean = when (format) {
        BookFormat.Comic -> ComicImporter.pages(folder).isNotEmpty()
        BookFormat.Pdf -> File(folder, PDF_NAME).isFile
        BookFormat.Novel -> File(folder, "content/META-INF/container.xml").isFile
        BookFormat.Series -> false
    }

    /** Writes the file into [directory] with the title as its name, and gives it. Null when the book has no file to share. */
    fun file(format: BookFormat, title: String, folder: File, directory: File): File? {
        if (!canShare(format, folder)) return null
        directory.mkdirs()
        val base = title.split(Regex("[/\\\\:?%*|\"<>]")).joinToString(" ").trim().ifEmpty { "Book" }
        return when (format) {
            BookFormat.Comic -> File(directory, "$base.cbz").also { target ->
                zip(target, ComicImporter.pages(folder).map { it.name to it })
            }
            BookFormat.Pdf -> File(directory, "$base.pdf").also { target -> File(folder, PDF_NAME).copyTo(target, overwrite = true) }
            BookFormat.Novel -> File(directory, "$base.epub").also { target ->
                val content = File(folder, "content")
                val files = content.walkTopDown().filter { it.isFile }.map { it.relativeTo(content).invariantSeparatorsPath to it }
                    .filter { it.first != "mimetype" }.sortedBy { it.first }.toList()
                // An EPUB starts with its mimetype file, with no compression.
                zip(target, files, first = "mimetype" to "application/epub+zip".toByteArray())
            }
            BookFormat.Series -> null
        }
    }

    /**
     * Writes a ZIP archive with no compression. The pages of a comic and the files of an EPUB are compressed already, so
     * a stored archive is almost as small, and it is fast. The mimetype of an EPUB must be stored too.
     */
    private fun zip(target: File, files: List<Pair<String, File>>, first: Pair<String, ByteArray>? = null) {
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            out.setMethod(ZipOutputStream.STORED)
            fun put(name: String, data: ByteArray) {
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                }
                out.putNextEntry(entry)
                out.write(data)
                out.closeEntry()
            }
            first?.let { (name, data) -> put(name, data) }
            for ((name, file) in files) put(name, file.readBytes())
        }
    }
}
