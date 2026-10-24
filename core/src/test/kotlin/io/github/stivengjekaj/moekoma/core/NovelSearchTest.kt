package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelSearchTest {
    private val chapters = listOf(
        "<p>The <b>Harbor</b> lights came on.</p>",
        null,
        "<p>A café by the harbor, and a quiet sea.</p>",
    )

    @Test fun findsTheWordsWithNoTagsCaseOrAccents() {
        val found = NovelSearch.search("harbor", chapters.size) { chapters[it] }
        assertEquals(listOf(0, 2), found.results.map { it.chapter })
        assertEquals("One chapter is not on the device.", 1, found.missing)
        assertTrue(found.results[0].snippet, "Harbor lights" in found.results[0].snippet)
        assertEquals(listOf(2), NovelSearch.search("CAFE", chapters.size) { chapters[it] }.results.map { it.chapter })
    }

    @Test fun needsTwoLettersAndKeepsALimit() {
        assertEquals(emptyList<NovelSearch.Result>(), NovelSearch.search("a", chapters.size) { chapters[it] }.results)
        assertEquals(1, NovelSearch.search("the", chapters.size, limit = 1) { chapters[it] }.results.size)
    }
}
