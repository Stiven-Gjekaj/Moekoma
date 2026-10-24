package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class FolderSizeTest {
    @Test fun measuresTheFilesOfAFolder() {
        val folder = Files.createTempDirectory("size").toFile()
        assertEquals(0L, FolderSize.bytes(java.io.File(folder, "none")))
        java.io.File(folder, "chapters/0001").mkdirs()
        java.io.File(folder, "chapters/0001/1.png").writeBytes(ByteArray(10_000))
        java.io.File(folder, "book.json").writeBytes(ByteArray(5_000))
        assertEquals(15_000L, FolderSize.bytes(folder))
        assertEquals(10_000L, FolderSize.bytes(java.io.File(folder, "chapters")))
        folder.deleteRecursively()
    }

    @Test fun writesTheSizeForTheUser() {
        assertEquals("900 bytes", FolderSize.text(900))
        assertEquals("10 KB", FolderSize.text(10_240))
        assertEquals("12.5 MB", FolderSize.text(13_107_200))
        assertEquals("2.00 GB", FolderSize.text(2L * 1024 * 1024 * 1024))
    }
}
