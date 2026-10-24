package io.github.stivengjekaj.moekoma.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The text that a check for new chapters shows. */
class CheckMessageTest {
    private fun check(added: Int, vararg failed: String) = Sources.Check(added, failed.toList(), emptyList())

    @Test fun saysNothingAfterAQuietCheckWithNoNews() {
        assertNull(check(0).message(quiet = true))
        assertEquals("No new chapters.", check(0).message())
        assertEquals("1 new chapter.", check(1).message(quiet = true))
    }

    @Test fun namesTheBooksThatDidNotUpdate() {
        assertEquals("2 new chapters. 1 book did not update: Tide.", check(2, "Tide").message())
        assertEquals(
            "0 new chapters. 5 books did not update: A, B, C, and 2 more.",
            check(0, "A", "B", "C", "D", "E").message(),
        )
    }
}
