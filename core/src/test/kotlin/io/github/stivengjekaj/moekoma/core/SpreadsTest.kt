package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsTest {
    @Test fun pairsThePagesAfterTheCover() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), Spreads.make(6, { false }, shift = false))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)), Spreads.make(6, { false }, shift = true))
        assertEquals(emptyList<List<Int>>(), Spreads.make(0, { false }, shift = false))
        assertEquals(listOf(listOf(0)), Spreads.make(1, { false }, shift = true))
    }

    @Test fun keepsAWidePageAlone() {
        // The page 3 is two pages in one image.
        val spreads = Spreads.make(7, { it == 3 }, shift = false)
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5), listOf(6)), spreads)
        assertEquals(3, Spreads.index(5, spreads))
        assertEquals(2, Spreads.index(3, spreads))
        assertEquals(4, Spreads.index(99, spreads))
        assertEquals(0, Spreads.index(-1, spreads))
    }
}
