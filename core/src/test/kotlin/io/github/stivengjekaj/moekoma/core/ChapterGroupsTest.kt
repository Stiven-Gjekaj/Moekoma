package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterGroupsTest {
    @Test fun findsTheVolumeInTheFormsOfTheSources() {
        assertEquals("2", ChapterGroups.volume("Vol. 2 Ch. 10"))
        assertEquals("3", ChapterGroups.volume("Volume 03: The Sea"))
        assertEquals("1.5", ChapterGroups.volume("vol 1.5 extra"))
        assertEquals("4", ChapterGroups.volume("V.4 Chapter 2"))
        assertNull(ChapterGroups.volume("Chapter 12: Revolution"))
        assertNull(ChapterGroups.volume("Chapter 7"))
    }

    @Test fun groupsTheChaptersByVolumeInTheirOrder() {
        val volumes = ChapterGroups.volumes(listOf("Vol. 1 Ch. 1", "Vol. 1 Ch. 2", "Vol. 2 Ch. 3", "Ch. 4", "Vol. 2 Ch. 5"))
        assertEquals(listOf("Volume 1", "Volume 2", "No volume"), volumes?.map { it.name })
        assertEquals(listOf(listOf(0, 1), listOf(2, 4), listOf(3)), volumes?.map { it.chapters })
    }

    @Test fun givesNoVolumesWhenNoTitleHasOne() {
        assertNull(ChapterGroups.volumes(listOf("Chapter 1", "Chapter 2")))
        assertNull(ChapterGroups.volumes(emptyList()))
    }

    @Test fun findsTheNumberOfAChapterInItsTitle() {
        assertEquals(12.0, ChapterGroups.number("Vol. 2 Ch. 12: The Sea"))
        assertEquals(7.5, ChapterGroups.number("Chapter 7.5"))
        assertEquals(40.0, ChapterGroups.number("Episode 40 - Rain"))
        assertEquals(21.0, ChapterGroups.number("Volume 3 - 21"))
        assertNull(ChapterGroups.number("The Last Bell"))
    }

    @Test fun findsThePlaceOfAChapterInTheListOfAnotherSource() {
        val old = listOf("Ch. 1", "Ch. 2", "Ch. 3", "Ch. 4")
        val new = listOf("Chapter 0", "Chapter 1", "Chapter 2", "Chapter 3", "Chapter 3.5", "Chapter 4")
        assertEquals("The same number wins.", 3, ChapterGroups.place(2, old, new))
        assertEquals("With no numbers, the same part of the list.", 2, ChapterGroups.place(3, listOf("A", "B", "C", "D"), listOf("W", "X", "Y")))
        assertEquals(0, ChapterGroups.place(0, old, emptyList()))
    }

    @Test fun splitsALongListIntoRangesOfFifty() {
        val ranges = ChapterGroups.ranges(120)
        assertEquals(listOf(0 until 50, 50 until 100, 100 until 120), ranges)
        assertEquals(listOf("1-50", "51-100", "101-120"), ranges.map(ChapterGroups::name))
        assertEquals(listOf(0 until 30), ChapterGroups.ranges(30))
        assertTrue(ChapterGroups.ranges(0).isEmpty())
        assertEquals("101", ChapterGroups.name(100 until 101))
    }
}
