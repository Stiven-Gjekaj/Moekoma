package io.github.stivengjekaj.moekoma.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.github.stivengjekaj.moekoma.core.ComicFiles
import java.io.InputStream

/** A folder of the device that the user chose with the Storage Access Framework. */
class DocumentTreeFiles(
    private val resolver: ContentResolver,
    private val tree: Uri,
    /** The folder in the tree. With null, it is the root of the tree. */
    private val document: String? = null,
) : ComicFiles {
    override fun forEach(body: (path: String, open: () -> InputStream) -> Unit) {
        walk(document ?: DocumentsContract.getTreeDocumentId(tree), "", body)
    }

    private fun walk(parent: String, prefix: String, body: (path: String, open: () -> InputStream) -> Unit) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val entries = mutableListOf<Triple<String, String, String>>()
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += Triple(cursor.getString(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "")
            }
        }
        for ((id, name, mime) in entries) {
            val path = prefix + name
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                walk(id, "$path/", body)
            } else {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                body(path) { resolver.openInputStream(uri) ?: throw java.io.IOException("$name cannot be opened.") }
            }
        }
    }

    /** The name of the folder, for the title of the book. */
    fun name(): String {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
        }
        return id.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }
    }
}
