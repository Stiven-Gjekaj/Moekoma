package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSearchTest {
    @Test fun keyIgnoresCaseAccentsAndMarks() {
        assertEquals(TitleMatch.key("cafe the story"), TitleMatch.key("Café: The Story!"))
        assertEquals("onepunchman", TitleMatch.key("One-Punch Man"))
        assertEquals("abc", TitleMatch.key("ＡＢＣ"))
    }

    @Test fun scoreOrdersSameStartAndPart() {
        assertEquals(3, TitleMatch.score("Blue Lock", "blue lock"))
        assertEquals(2, TitleMatch.score("Blue Lock: Episode Nagi", "Blue Lock"))
        assertEquals(1, TitleMatch.score("The Blue Lock Fan Book", "Blue Lock"))
        assertEquals(0, TitleMatch.score("Red Lock", "Blue Lock"))
        assertEquals(0, TitleMatch.score("Anything", "  "))
    }

    @Test fun groupsJoinTheSameTitleOfTwoSources() {
        val results = SearchResults("sunrise")
        results.add(listOf(OnlineSeries("a1", "Sunrise Tale"), OnlineSeries("a2", "Sunrise")), "A")
        results.add(listOf(OnlineSeries("b1", "SUNRISE", "https://b.test/c.png")), "B")
        val groups = results.sorted
        assertEquals(listOf("Sunrise", "Sunrise Tale"), groups.map { it.title })
        assertEquals(listOf("A", "B"), groups[0].hits.map { it.source })
        assertEquals("B", groups[0].cover?.source)
        assertNull(groups[1].cover)
    }

    @Test fun moreSourcesComeFirstWhenTheScoreIsTheSame() {
        val results = SearchResults("moon")
        results.add(listOf(OnlineSeries("1", "Moon One"), OnlineSeries("2", "Moon Two")), "A")
        results.add(listOf(OnlineSeries("3", "Moon Two")), "B")
        assertEquals(listOf("Moon Two", "Moon One"), results.sorted.map { it.title })
    }

    @Test fun aSourceCountsOneTimeInAGroup() {
        val results = SearchResults("x")
        results.add(listOf(OnlineSeries("1", "Same"), OnlineSeries("2", "same")), "A")
        assertEquals(1, results.sorted.first().hits.size)
        assertEquals("1", results.sorted.first().hits.first().series.id)
    }

    @Test fun theSourceWithMostChaptersWins() {
        assertEquals("B", SourceChoice.best(listOf("A" to 10, "B" to 42, "C" to 42)))
        assertEquals("B", SourceChoice.best(listOf("A" to null, "B" to 3)))
        assertEquals("A", SourceChoice.best(listOf("A" to null, "B" to 0)))
        assertNull(SourceChoice.best(emptyList()))
    }

    @Test fun mixTakesOneOfEachSourceInTurnWithNoSameTitle() {
        val a = listOf(SearchHit("A", OnlineSeries("1", "One")), SearchHit("A", OnlineSeries("2", "Two")))
        val b = listOf(SearchHit("B", OnlineSeries("3", "one")), SearchHit("B", OnlineSeries("4", "Four")), SearchHit("B", OnlineSeries("5", "Five")))
        assertEquals(listOf("1", "2", "4", "5"), TitleMix.roundRobin(listOf(a, b)).map { it.series.id })
        assertEquals(listOf("1", "2"), TitleMix.roundRobin(listOf(a, b), limit = 2).map { it.series.id })
        assertTrue(TitleMix.roundRobin(emptyList()).isEmpty())
    }

    @Test fun aChapterOfTwoGroupsCountsOneTime() {
        val chapters = listOf(
            OnlineChapter("1a", "Ch. 1", 1.0, group = "A"), OnlineChapter("1b", "Ch. 1", 1.0, group = "B"),
            OnlineChapter("2", "Ch. 2", 2.0), OnlineChapter("x", "Extra"), OnlineChapter("y", "Extra 2"),
        )
        assertEquals(4, SourceChoice.distinctChapters(chapters))
        assertEquals(0, SourceChoice.distinctChapters(emptyList()))
    }
}
