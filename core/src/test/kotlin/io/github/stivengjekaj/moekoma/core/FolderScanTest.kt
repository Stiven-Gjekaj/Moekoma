package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FolderScanTest {
    private fun tree(vararg paths: String): File {
        val root = temporaryFolder()
        for (path in paths) File(root, path).apply { parentFile.mkdirs(); writeText("x") }
        return root
    }

    private fun names(found: List<FolderScan.Found>) = found.map { (if (it is FolderScan.Found.Images) "images:" else "book:") + it.node.name }

    @Test fun findsTheBooksAndTheFoldersOfImagesInOrder() {
        val root = tree(
            "Novel 10.epub", "Novel 2.epub", "notes.txt.bak", "cover.jpg",
            "Manga/Vol 2.cbz", "Manga/Vol 10.cbz",
            "Webtoon/Ch 1/001.jpg", "Webtoon/Ch 1/002.jpg", "Webtoon/Ch 2/001.png",
            ".hidden/secret.epub", "Empty/readme.md",
        )
        assertEquals(
            // The folder Empty has no book and no image, so it is not a book.
            listOf("book:Novel 2.epub", "book:Novel 10.epub", "book:Vol 2.cbz", "book:Vol 10.cbz", "images:Ch 1", "images:Ch 2"),
            names(FolderScan.find(FileNode(root), emptySet())),
        )
    }

    @Test fun skipsTheBooksThatTheAppSawBefore() {
        val root = tree("a.epub", "b.pdf")
        val seen = setOf(File(root, "a.epub").path)
        assertEquals(listOf("book:b.pdf"), names(FolderScan.find(FileNode(root), seen)))
    }

    @Test fun readsTheImagesOfTheRootAsNoBook() {
        val root = tree("001.jpg", "002.jpg")
        assertEquals(emptyList<String>(), names(FolderScan.find(FileNode(root), emptySet())))
    }

    @Test fun readsAFolderWithImagesAndABookAsTheBookOnly() {
        val root = tree("Vol 1/cover.jpg", "Vol 1/book.epub")
        assertEquals(listOf("book:book.epub"), names(FolderScan.find(FileNode(root), emptySet())))
    }
}
