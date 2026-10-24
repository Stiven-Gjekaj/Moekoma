package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OpdsTest {
    private val base = "https://komga.local:25600/opds/v1.2/catalog"

    @Test fun readsTheNavigationAndTheSearchOfAnAtomFeed() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Komga</title>
              <link rel="search" type="application/opensearchdescription+xml" href="/opds/v1.2/search"/>
              <link rel="next" href="catalog?page=1"/>
              <entry><title>Series</title><id>series</id><content>All series</content>
                <link rel="subsection" type="application/atom+xml;profile=opds-catalog;kind=navigation" href="/opds/v1.2/series"/></entry>
            </feed>
        """.trimIndent()
        val feed = Opds.parse(xml.toByteArray(), base)
        assertEquals("Komga", feed.title)
        assertEquals(listOf(OpdsLink("Series", "https://komga.local:25600/opds/v1.2/series", "All series")), feed.navigation)
        assertEquals("https://komga.local:25600/opds/v1.2/catalog?page=1", feed.next)
        assertEquals(OpdsSearch.Description("https://komga.local:25600/opds/v1.2/search"), feed.search)
    }

    @Test fun readsAPublicationWithItsFilesItsCoverAndItsStream() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:pse="http://vaemendis.net/opds-pse/ns">
              <title>Berserk</title>
              <entry><title>Volume 1</title><id>urn:uuid:1</id><author><name>Kentaro Miura</name></author>
                <summary>&lt;p&gt;Guts.&lt;/p&gt;</summary>
                <link rel="http://opds-spec.org/image/thumbnail" type="image/jpeg" href="/api/v1/books/1/thumbnail"/>
                <link rel="http://opds-spec.org/acquisition" type="application/zip" href="/api/v1/books/1/file"/>
                <link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="/b/1.epub"/>
                <link rel="http://vaemendis.net/opds-pse/stream" type="image/jpeg" pse:count="226"
                      href="/api/v1/books/1/pages/{pageNumber}?zero_based=true&amp;width={maxWidth}"/></entry>
            </feed>
        """.trimIndent()
        val book = Opds.parse(xml.toByteArray(), base).publications.first()
        assertEquals("Volume 1", book.title)
        assertEquals("Kentaro Miura", book.author)
        assertEquals("Guts.", book.summary)
        assertEquals("https://komga.local:25600/api/v1/books/1/thumbnail", book.image)
        assertEquals("cbz", book.download?.second)
        assertEquals(226, book.stream?.count)
        assertEquals("https://komga.local:25600/api/v1/books/1/pages/3?zero_based=true&width=800", book.stream?.url(3, 800))
    }

    @Test fun skipsAFileThatTheAppCannotImport() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom"><title>x</title><entry><title>A</title><id>a</id>
            <link rel="http://opds-spec.org/acquisition" type="application/x-ms-reader" href="a.lit"/>
            <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="a.pdf"/></entry></feed>
        """.trimIndent()
        val book = Opds.parse(xml.toByteArray(), base).publications.first()
        assertEquals("pdf", book.download?.second)
        assertEquals("https://komga.local:25600/opds/v1.2/a.pdf", book.download?.first)
    }

    @Test fun knowsTheKindleTypesOfProjectGutenberg() {
        val link = "https://www.gutenberg.org/ebooks/11.kf8.images"
        assertEquals("mobi", Opds.fileExtension("application/x-mobipocket-ebook", link))
        assertEquals("azw3", Opds.fileExtension("application/vnd.amazon.mobi8-ebook", link))
        assertNull(Opds.fileExtension("application/octet-stream", link))
    }

    @Test fun readsAnOpds2FeedWithGroups() {
        val json = """
            {"metadata": {"title": "Library"},
             "links": [{"rel": "search", "href": "/opds/v2/search{?query}", "templated": true}, {"rel": "next", "href": "?page=2"}],
             "navigation": [{"href": "/opds/v2/libraries", "title": "Libraries", "type": "application/opds+json"}],
             "groups": [{"metadata": {"title": "Keep reading"},
               "publications": [{"metadata": {"title": "Saga 1", "author": [{"name": "B. K. Vaughan"}], "identifier": "id-1"},
                 "links": [{"rel": "http://opds-spec.org/acquisition", "href": "/b/1/file", "type": "application/vnd.comicbook+zip"}],
                 "images": [{"href": "/b/1/thumb", "type": "image/jpeg"}]}]}]}
        """.trimIndent()
        val feed = Opds.parse(json.toByteArray(), "https://komga.local/opds/v2/catalog")
        assertEquals("Library", feed.title)
        assertEquals(listOf("Libraries"), feed.navigation.map { it.title })
        assertEquals("Keep reading", feed.groups.first().title)
        val book = feed.groups.first().publications.first()
        assertEquals("B. K. Vaughan", book.author)
        assertEquals("cbz", book.download?.second)
        assertEquals("https://komga.local/b/1/thumb", book.image)
        assertEquals(OpdsSearch.Template("https://komga.local/opds/v2/search{?query}"), feed.search)
        assertEquals("https://komga.local/opds/v2/catalog?page=2", feed.next)
    }

    @Test fun fillsTheTemplatesOfBothVersions() {
        assertEquals("https://x.org/search?q=one%20piece%20%26%20co&start=", Opds.searchUrl("https://x.org/search?q={searchTerms}&start={startPage?}", "one piece & co"))
        assertEquals("https://x.org/opds/v2/search?query=saga", Opds.searchUrl("https://x.org/opds/v2/search{?query}", "saga"))
    }

    @Test fun readsTheTemplateOfAnOpenSearchDescription() {
        val xml = """
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
            <Url type="text/html" template="https://x.org/html?q={searchTerms}"/>
            <Url type="application/atom+xml;profile=opds-catalog" template="https://x.org/opds/search/{searchTerms}"/>
            </OpenSearchDescription>
        """.trimIndent()
        assertEquals("https://x.org/opds/search/{searchTerms}", Opds.openSearchTemplate(xml.toByteArray()))
    }

    @Test fun refusesAPageThatIsNotAFeed() {
        assertThrows(NotAFeedException::class.java) { Opds.parse("<html><body>Login</body></html>".toByteArray(), base) }
    }

    @Test fun opensAStreamAtTheLastPageThatTheServerKeeps() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:pse="http://vaemendis.net/opds-pse/ns"><title>x</title>
              <entry><title>A</title><id>a</id>
                <link rel="http://vaemendis.net/opds-pse/stream" type="image/jpeg" pse:count="20" pse:lastRead="7"
                      href="/komga/opds/v1.2/books/0B1/pages/{pageNumber}?zero_based=true&amp;width={maxWidth}"/></entry>
              <entry><title>B</title><id>b</id>
                <link rel="http://vaemendis.net/opds-pse/stream" type="image/jpeg" pse:count="3" pse:lastRead="9"
                      href="/api/opds/key/series/1/volume/2/chapter/3/image?pageNumber={pageNumber}"/></entry>
            </feed>
        """.trimIndent()
        val list = Opds.parse(xml.toByteArray(), base).publications
        val komga = list.first().stream!!
        val kavita = list.last().stream!!
        assertEquals(7, komga.startPage)
        assertEquals("A last page after the end opens the last page.", 2, kavita.startPage)
        val request = komga.komgaProgress(7)!!
        assertEquals("PATCH", request.method)
        assertEquals("The base path of the server stays.", "https://komga.local:25600/komga/api/v1/books/0B1/read-progress", request.url)
        assertEquals("{\"page\":8,\"completed\":false}", request.body!!.decodeToString())
        assertEquals("{\"page\":20,\"completed\":true}", komga.komgaProgress(19)!!.body!!.decodeToString())
        assertEquals("Kavita keeps the progress by itself.", null, kavita.komgaProgress(1))
    }
}
