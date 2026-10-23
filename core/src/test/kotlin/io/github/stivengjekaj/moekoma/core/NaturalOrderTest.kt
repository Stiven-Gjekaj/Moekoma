package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    @Test fun putsTheNumbersInTheirOrder() {
        assertEquals(listOf("Page1.jpg", "page2.jpg", "page10.jpg"), NaturalOrder.sorted(listOf("page10.jpg", "page2.jpg", "Page1.jpg")))
    }

    @Test fun comparesOneFolderAtATime() {
        val paths = listOf("Ch 10/01.jpg", "Ch 2/10.jpg", "Ch 2/9.jpg", "Ch 2 extra/01.jpg")
        assertEquals(listOf("Ch 2/9.jpg", "Ch 2/10.jpg", "Ch 2 extra/01.jpg", "Ch 10/01.jpg"), NaturalOrder.sorted(paths))
    }

    @Test fun readsFullWidthDigitsAsDigits() {
        assertEquals(listOf("２.jpg", "10.jpg"), NaturalOrder.sorted(listOf("10.jpg", "２.jpg")))
    }

    @Test fun putsZerosInFrontOfANumberAtTheSamePlace() {
        assertEquals(listOf("p1.jpg", "p02.jpg", "p10.jpg"), NaturalOrder.sorted(listOf("p10.jpg", "p02.jpg", "p1.jpg")))
    }
}
