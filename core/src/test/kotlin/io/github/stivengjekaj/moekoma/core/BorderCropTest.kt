package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BorderCropTest {
    /** A small copy of a page: [background] with a block of [ink] from (x0, y0) to (x1, y1), with the origin at the top left. */
    private fun page(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int, background: Int = 255, ink: Int = 50): (Int, Int) -> Int =
        { x, y -> if (x in x0 until x1 && y in y0 until y1) ink else background }

    @Test fun findsTheWhiteBordersOfAPage() {
        // A copy of 200 by 300 pixels of a page of 1000 by 1500 pixels.
        val box = BorderCrop.content(200, 300, 1000, 1500, gray = page(200, 300, 20, 40, 160, 240))
        assertTrue(abs(box.left - 100) < 15 && abs(box.top - 200) < 15)
        assertTrue(abs(box.right - 800) < 15 && abs(box.bottom - 1200) < 15)
    }

    @Test fun findsABlackBorder() {
        // The block is near the top, so a crop with the rows in the wrong order fails.
        val box = BorderCrop.content(160, 240, 800, 1200, gray = page(160, 240, 10, 20, 150, 100, background = 0, ink = 230))
        assertTrue(abs(box.left - 50) < 12 && abs(box.right - 750) < 12)
        assertTrue(abs(box.top - 100) < 12 && abs(box.bottom - 500) < 12)
    }

    @Test fun keepsAPageWithNoBorderOrWithOneColor() {
        val full = BorderCrop.Box(0, 0, 600, 900)
        assertEquals(full, BorderCrop.content(120, 180, 600, 900, gray = page(120, 180, 0, 0, 120, 180)))
        assertEquals("A white page has nothing to crop.", full, BorderCrop.content(120, 180, 600, 900, gray = { _, _ -> 255 }))
        assertEquals("A thin border is no crop.", full, BorderCrop.content(120, 180, 600, 900, gray = page(120, 180, 1, 1, 119, 179)))
    }
}
