package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationTest {
    private val table = Translation(
        mapOf(
            "Library" to "ライブラリ",
            "Page %d of %d" to "%1\$d / %2\$d ページ",
            "Delete %@?" to "%@ を削除しますか?",
            "Chapter %d" to "第 %d 話",
            "Register a client on %@ with the redirect URI %@." to "%2\$@ で %1\$@ を登録",
            "%@ · %d chapters" to "%1\$@ · %2\$d 話",
        ),
    )

    @Test fun givesTheExactText() {
        assertEquals("ライブラリ", table.of("Library"))
        assertEquals("Libraries", table.of("Libraries"))
        assertEquals("", table.of(""))
    }

    @Test fun putsTheValuesBackInOrder() {
        assertEquals("3 / 20 ページ", table.of("Page 3 of 20"))
        assertEquals("第 12 話", table.of("Chapter 12"))
        assertEquals("Night Tale · 4 話", table.of("Night Tale · 4 chapters"))
    }

    @Test fun putsTheValuesAtTheirPositions() {
        assertEquals("moekoma://x で AniList を登録", table.of("Register a client on AniList with the redirect URI moekoma://x."))
    }

    @Test fun keepsAValueWithMarksOfAPattern() {
        assertEquals("A (1) * b? を削除しますか?", table.of("Delete A (1) * b??"))
    }

    @Test fun aNumberValueDoesNotTakeText() {
        assertEquals("Chapter one", table.of("Chapter one"))
        assertEquals("Page 3 of many", table.of("Page 3 of many"))
    }

    @Test fun readsATableOfJson() {
        val parsed = Translation.parse("""{"Back": "戻る", "Page %d": "%d ページ"}""")
        assertEquals("戻る", parsed.of("Back"))
        assertEquals("7 ページ", parsed.of("Page 7"))
    }
}
