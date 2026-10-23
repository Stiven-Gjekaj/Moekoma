package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun comparesEachNumber() {
        assertTrue(AppVersion.isNewer("v0.10.0", "0.9.3"))
        assertTrue(AppVersion.isNewer("1.0.1", "1.0"))
        assertFalse(AppVersion.isNewer("v0.1.0", "0.1.0"))
        assertFalse(AppVersion.isNewer("0.2.0", "0.10.0"))
    }

    @Test fun putsATestVersionBeforeItsRelease() {
        assertTrue(AppVersion.isNewer("0.2.0", "0.2.0-rc1"))
        assertTrue(AppVersion.isNewer("0.2.0-rc10", "0.2.0-rc2"))
    }
}
