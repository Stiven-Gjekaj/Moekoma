package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KotatsuPluginTest {
    @Test fun findsTheReleasesOfARepositoryOnGitHub() {
        assertEquals("https://api.github.com/repos/Gekkoushi/plugin/releases/latest", KotatsuPlugin.latestReleaseApi("https://github.com/Gekkoushi/plugin"))
        assertEquals("https://api.github.com/repos/Gekkoushi/plugin/releases/latest", KotatsuPlugin.latestReleaseApi("https://github.com/Gekkoushi/plugin/releases"))
        assertNull(KotatsuPlugin.latestReleaseApi("https://github.com/Gekkoushi"))
        assertNull(KotatsuPlugin.latestReleaseApi("https://example.org/a/b"))
    }

    @Test fun takesTheJarOfARelease() {
        val json = """{"tag_name":"75af884","assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
            {"name":"gekkoushi.jar","browser_download_url":"https://github.com/Gekkoushi/plugin/releases/download/75af884/gekkoushi.jar"}]}"""
        assertEquals(
            KotatsuPlugin.Jar("https://github.com/Gekkoushi/plugin/releases/download/75af884/gekkoushi.jar", "75af884"),
            KotatsuPlugin.release(json.toByteArray()),
        )
        assertNull(KotatsuPlugin.release("""{"tag_name":"1","assets":[]}""".toByteArray()))
        assertEquals(KotatsuPlugin.Jar("https://x.test/p/parsers-12.jar", "parsers-12"), KotatsuPlugin.direct("https://x.test/p/parsers-12.jar"))
    }

    @Test fun leavesOutBrokenSourcesAndNovels() {
        val jar = KotatsuPlugin.Jar("https://x/p.jar", "7")
        val info = KotatsuPlugin.info("MANGADEX", "MangaDex", "", "MANGA", broken = false, jar)!!
        assertEquals(listOf("MANGADEX", "MangaDex", "multi", "7", "https://x/p.jar"), listOf(info.id, info.name, info.language, info.version, info.code))
        assertEquals(true, KotatsuPlugin.info("NH", "NH", "en", "HENTAI", false, jar)?.adult)
        assertNull(KotatsuPlugin.info("OLD", "Old", "en", "MANGA", broken = true, jar))
        assertNull(KotatsuPlugin.info("NOV", "Novels", "en", "NOVEL", broken = false, jar))
    }

    @Test fun knowsAPluginOfTheParsersAsAKind() {
        assertEquals(SourceKind.Kotatsu, SourceKind.guess("https://github.com/Gekkoushi/plugin"))
        assertEquals(SourceKind.Kotatsu, SourceKind.guess("https://example.org/kotatsu/parsers.jar"))
        assertEquals(false, SourceKind.Kotatsu.shared)
    }
}
