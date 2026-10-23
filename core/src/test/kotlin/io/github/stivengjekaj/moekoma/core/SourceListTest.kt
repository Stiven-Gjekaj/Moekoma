package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SourceListTest {
    @Test fun readsOneSourceOnEachLineWithItsKind() {
        val text = """
            # My sources
            https://komga.local:25600/opds/v1.2/catalog

            https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json
            https://aidoku-community.github.io/sources/index.min.json
            https://inkdex.github.io/extensions/0.9/stable/versioning.json
            http://192.168.1.20:4567
            not a url
            https://komga.local:25600/opds/v1.2/catalog
        """.trimIndent()
        assertEquals(
            listOf(SourceKind.Opds, SourceKind.LnReader, SourceKind.Aidoku, SourceKind.Paperback, SourceKind.Suwayomi),
            SourceList.parse(text).map { it.link.kind },
        )
    }

    @Test fun takesTheUserAndThePasswordOutOfTheUrl() {
        val line = SourceList.parse("https://asa:sun%40rise@kavita.local/api/opds/key").first()
        assertEquals("https://kavita.local/api/opds/key", line.link.url)
        assertEquals("asa", line.link.username)
        assertEquals("sun@rise", line.password)
        assertEquals("https://kavita.local/...", line.link.shortAddress)
    }

    @Test fun writesTheUserButNotThePassword() {
        val links = SourceList.parse("https://asa:secret@komga.local/opds\nhttps://example.org/opds").map { it.link }
        assertEquals("https://asa@komga.local/opds\nhttps://example.org/opds", SourceList.format(links))
    }

    @Test fun keepsTheIdentityOfASourceThatStays() {
        val old = SourceLink(url = "https://komga.local/opds", username = "asa", kind = SourceKind.Opds, name = "Home")
        val line = SourceList.parse("https://asa@komga.local/opds", existing = listOf(old)).first()
        assertEquals(old, line.link)
        assertNull(line.password)
    }

    @Test fun detectsTheKindFromTheContent() {
        assertEquals(SourceKind.Opds, SourceKind.detect("""<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>""".toByteArray()))
        assertEquals(SourceKind.LnReader, SourceKind.detect("""[{"id":"a","name":"A","site":"https://a","url":"https://x/a.js"}]""".toByteArray()))
        assertEquals(SourceKind.Aidoku, SourceKind.detect("""{"name":"List","sources":[{"id":"a","downloadURL":"sources/a.aix"}]}""".toByteArray()))
        assertEquals(SourceKind.Paperback, SourceKind.detect("""{"buildTime":"x","builtWith":{},"sources":[]}""".toByteArray()))
        assertNull(SourceKind.detect("<html></html>".toByteArray()))
    }

    /** The sources, the installed sources, and the origin of a book, as Asabunko writes them. */
    @Test fun readsTheSourcesOfALibraryOfAsabunko() {
        val text = """
            {"books":[{"added":"2026-10-23T09:30:00Z","fileName":"Quiet","finished":false,"format":"novel","id":"0F5B5E1C-2C4E-4D4B-9C8A-5B0E8E1F2A3B",
             "listed":true,"origin":{"list":"11111111-2222-3333-4444-555555555555","series":"novel/dawn","source":"quiet"},"page":0,"pageCount":3,"position":0,"title":"Dawn"}],
             "defaultMode":"rightToLeft","form":1,
             "installed":[{"info":{"code":"https://x/quiet.js","hasLatest":true,"id":"quiet","language":"en","name":"Quiet","novels":true,"version":"1.0.0"},"list":"11111111-2222-3333-4444-555555555555"}],
             "sources":[{"id":"11111111-2222-3333-4444-555555555555","kind":"lnreader","url":"https://x/plugins.min.json"}]}
        """.trimIndent()
        val library = LibraryStore.load(File(temporaryFolder(), "library.json").apply { writeText(text) })
        assertEquals(SourceKind.LnReader, library.sources.single().kind)
        assertEquals("quiet", library.installed.single().info.id)
        assertEquals("novel/dawn", library.books.single().origin?.series)
        assertEquals(library.books.single(), library.book(library.books.single().origin!!))
    }
}
