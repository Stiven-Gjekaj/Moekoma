package io.github.stivengjekaj.moekoma.core

import java.io.File

/** A file or a folder in a folder that the app watches. The app reads the folders of the device with the Storage Access Framework. */
interface TreeNode {
    /** The ID that stays the same for the same file, such as its document URI. */
    val id: String
    val name: String
    val isFolder: Boolean
    fun children(): List<TreeNode>
}

/** A folder on the disk, for the tests. */
class FileNode(private val file: File) : TreeNode {
    override val id: String get() = file.path
    override val name: String get() = file.name
    override val isFolder: Boolean get() = file.isDirectory
    override fun children(): List<TreeNode> = (file.listFiles() ?: emptyArray()).sortedBy { it.name }.map(::FileNode)
}

/** Finds the books in a folder that the app watches. */
object FolderScan {
    sealed interface Found {
        val node: TreeNode

        /** A book file, such as an EPUB or a CBZ. */
        data class Book(override val node: TreeNode) : Found

        /** A folder of images with no book file in it, which imports as one comic. */
        data class Images(override val node: TreeNode) : Found
    }

    /**
     * The books below [root] that the app did not see before, in the natural order of their paths.
     * A file or a folder with a name that starts with a dot is hidden.
     */
    fun find(root: TreeNode, seen: Set<String>): List<Found> {
        val found = mutableListOf<Found>()
        fun walk(folder: TreeNode) {
            val children = folder.children().filter { !it.name.startsWith(".") }
            val books = children.filter { !it.isFolder && BookImporter.extension(it.name) in BookImporter.supportedExtensions }
            val images = children.filter { !it.isFolder && ComicImporter.isPage(it.name) }
            val folders = children.filter { it.isFolder }
            if (folder !== root && books.isEmpty() && folders.isEmpty() && images.isNotEmpty()) {
                found += Found.Images(folder)
                return
            }
            books.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }.forEach { found += Found.Book(it) }
            folders.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }.forEach(::walk)
        }
        walk(root)
        return found.filter { it.node.id !in seen }
    }
}
