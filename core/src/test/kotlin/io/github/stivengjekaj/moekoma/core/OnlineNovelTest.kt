package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineNovelTest {
    @Test fun addsOnlyTheNewChaptersAndKeepsTheOldOnes() {
        val (first, added) = Novel("Dawn", chapters = emptyList()).merge(listOf(OnlineChapter("c/1", "One"), OnlineChapter("c/2", "Two")))
        assertEquals(2, added)
        assertEquals(listOf("chapters/00001.html", "chapters/00002.html"), first.chapters.map { it.path })
        // The source removed the first chapter and added a third.
        val (second, more) = first.merge(listOf(OnlineChapter("c/2", "Two"), OnlineChapter("c/3", "Three")))
        assertEquals(1, more)
        assertEquals(listOf("c/1", "c/2", "c/3"), second.chapters.map { it.remote })
        assertEquals(Novel.Chapter("Three", "chapters/00003.html", "c/3"), second.chapters[2])
    }
}
