package io.github.stivengjekaj.moekoma.core

import java.io.File

/** The result of the import of one book of any format. */
data class ImportedBook(
    val format: BookFormat,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    /** The number of pages of a comic or a PDF, or the number of chapters of a novel. */
    val pageCount: Int,
    /** The mode that the book asks for, or null. */
    val mode: ReadingMode? = null,
    /** The image for the cover. The app makes a small cover of it. */
    val cover: File? = null,
)

/** A file that the user imports. The app opens it as an archive or reads its bytes. */
interface ImportSource {
    val fileName: String
    /** The files in the archive, or in the folder. */
    fun files(): ComicFiles
    fun bytes(): ByteArray
}

/** Chooses the importer from the kind of the file. */
object BookImporter {
    val novelExtensions = setOf("epub", "txt", "text", "fb2", "html", "htm", "xhtml")

    val supportedExtensions: Set<String> get() = ComicImporter.archiveExtensions + novelExtensions

    fun extension(fileName: String) = fileName.substringAfterLast('.', "").lowercase()

    /** Imports the file into [folder], which must not exist. On a fault, [folder] does not stay. */
    fun importBook(source: ImportSource, folder: File): ImportedBook {
        val name = source.fileName
        return when (val ext = extension(name)) {
            "epub" -> novel(EpubImporter.importBook(source.files(), name, folder))
            "txt", "text" -> novel(TextImporter.importText(source.bytes(), name, folder))
            "html", "htm", "xhtml" -> novel(TextImporter.importHtml(source.bytes(), name, folder))
            "fb2" -> novel(Fb2Importer.importBook(source.bytes(), name, folder))
            in ComicImporter.archiveExtensions -> {
                val comic = ComicImporter.importComic(source.files(), name, folder)
                ImportedBook(
                    BookFormat.Comic, comic.title, comic.info?.writer, comic.info?.summary, comic.pageCount,
                    comic.info?.readingMode, ComicImporter.pages(folder).firstOrNull(),
                )
            }
            else -> throw ImportException.Unsupported(ext.ifEmpty { "?" })
        }
    }

    private fun novel(imported: ImportedNovel) = ImportedBook(
        BookFormat.Novel, imported.novel.title, imported.novel.author, imported.novel.summary,
        imported.novel.chapters.size, null, imported.cover,
    )
}
