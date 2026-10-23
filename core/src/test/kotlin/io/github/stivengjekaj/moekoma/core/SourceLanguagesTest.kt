package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLanguagesTest {
    private fun info(id: String, language: String, adult: Boolean = false) =
        SourceInfo(id = id, name = id, language = language, version = "1", code = "https://list.test/code", novels = false, adult = adult)

    @Test fun languagesMatchCodesNamesAndAll() {
        assertTrue(SourceLanguages.matches("en", listOf("en-US")))
        assertTrue(SourceLanguages.matches("English", listOf("en-GB")))
        assertTrue(SourceLanguages.matches("multi", listOf("ja")))
        assertTrue(SourceLanguages.matches("", listOf("ja")))
        assertFalse(SourceLanguages.matches("fr", listOf("en-US")))
        assertFalse(SourceLanguages.matches("Français", listOf("en")))
        assertTrue(SourceLanguages.matches("pt-BR", listOf("pt-PT")))
    }

    @Test fun pickLeavesOutAdultSourcesUntilAllowed() {
        val sources = listOf(info("1", "en"), info("2", "en", adult = true), info("3", "de"))
        assertEquals(listOf("1"), SourceLanguages.pick(sources, listOf("en"), adult = false).map { it.id })
        assertEquals(listOf("1", "2"), SourceLanguages.pick(sources, listOf("en"), adult = true).map { it.id })
    }

    @Test fun aSaveInstallsTheMissingEnglishSourcesAndRemovesTheSourcesThatLeftTheList() {
        val available = listOf(info("kept", "en"), info("new", "English"), info("all", "multi"), info("japanese", "ja"), info("adult", "en", adult = true))
        // "gone" left the list. "japanese-old" is still in the list, so it stays.
        val plan = SourceLanguages.sync(available + info("japanese-old", "ja"), setOf("kept", "gone", "japanese-old"), adult = false)
        assertEquals("Only the English sources and the sources of all languages go in.", listOf("new", "all"), plan.install.map { it.id })
        assertEquals(setOf("gone"), plan.remove)
        assertEquals(listOf("kept", "new", "all", "adult"), SourceLanguages.sync(available, emptySet(), adult = true).install.map { it.id })
        assertTrue("A save with no change installs nothing.", SourceLanguages.sync(available, setOf("kept", "new", "all"), adult = false).install.isEmpty())
    }
}
