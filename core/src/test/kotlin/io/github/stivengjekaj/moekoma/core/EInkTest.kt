package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkTest {
    @Test fun findsTheMakersOfEInkReaders() {
        assertTrue(EInk.isEInkDevice("ONYX", "MaxSeries"))
        assertTrue(EInk.isEInkDevice("Bigme", "Bigme"))
    }

    @Test fun doesNotFindAPhone() {
        assertFalse(EInk.isEInkDevice("Google", "google"))
        assertFalse(EInk.isEInkDevice("Hisense", "Hisense"))
    }
}
