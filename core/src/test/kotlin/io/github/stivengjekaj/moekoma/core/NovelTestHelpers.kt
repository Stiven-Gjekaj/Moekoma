package io.github.stivengjekaj.moekoma.core

import java.io.File

/** A file of the tests as the app gives it: a ZIP for the archives, and its bytes for the text. */
class FileSource(private val file: File) : ImportSource {
    override val fileName: String get() = file.name
    override fun files(): ComicFiles = ZipFiles(file)
    override fun bytes(): ByteArray = file.readBytes()
}

fun epub(opf: String, extra: List<Pair<String, ByteArray>> = emptyList(), opfPath: String = "OEBPS/content.opf"): List<Pair<String, ByteArray>> =
    listOf(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to """
            <?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles></container>
        """.trimIndent().toByteArray(),
        opfPath to opf.toByteArray(),
    ) + extra

fun chapter(text: String): ByteArray =
    "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>$text</p></body></html>".toByteArray()
