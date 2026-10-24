package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NovelCacheTest {
    private val folder: File = createTempDirectory("novel").toFile().apply { deleteOnExit() }

    @Test fun givesTheSameNovelUntilTheFileChanges() {
        val cache = NovelCache()
        Novel("Dawn Tea", chapters = listOf(Novel.Chapter("Arrival", "chapters/00001.html"))).save(folder)
        val first = cache.get(folder)!!
        assertSame(first, cache.get(folder))

        Novel("Dawn Tea", chapters = listOf(Novel.Chapter("Arrival", "chapters/00001.html"), Novel.Chapter("Harbor", "chapters/00002.html"))).save(folder)
        val file = File(folder, Novel.FILE_NAME)
        file.setLastModified(file.lastModified() + 2_000)
        assertEquals(listOf("Arrival", "Harbor"), cache.get(folder)!!.chapters.map { it.title })
    }

    @Test fun seesAChangeOfTheSizeInTheSameMillisecond() {
        val cache = NovelCache()
        Novel("A", chapters = listOf(Novel.Chapter("One", "1.html"))).save(folder)
        val file = File(folder, Novel.FILE_NAME)
        val time = file.lastModified()
        cache.get(folder)
        Novel("A", chapters = listOf(Novel.Chapter("One", "1.html"), Novel.Chapter("Two", "2.html"))).save(folder)
        file.setLastModified(time)
        assertEquals(2, cache.get(folder)!!.chapters.size)
    }

    @Test fun givesNullForAFolderWithNoNovel() {
        assertNull(NovelCache().get(File(folder, "none")))
    }
}
