package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfMetadataTest {
    private fun pdf(info: String) = """
        %PDF-1.4
        1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
        2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj
        3 0 obj << $info >> endobj
        trailer << /Root 1 0 R /Info 3 0 R >>
        %%EOF
    """.trimIndent().toByteArray(Charsets.ISO_8859_1)

    @Test fun readsALiteralTitleAndAuthorWithEscapes() {
        val info = PdfMetadata.read(pdf("/Title (Night \\(1\\) Notes) /Author (Mira)"))
        assertEquals("Night (1) Notes", info.title)
        assertEquals("Mira", info.author)
    }

    @Test fun readsAHexTitleInUtf16() {
        val hex = "FEFF" + "白夜".toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }
        assertEquals("白夜", PdfMetadata.read(pdf("/Title <$hex>")).title)
    }

    @Test fun takesTheLastInfoOfAnUpdatedFile() {
        val data = pdf("/Title (Old)") + "\n4 0 obj << /Title (New) >> endobj\n".toByteArray()
        assertEquals("New", PdfMetadata.read(data).title)
    }

    @Test fun givesNothingForAnEmptyTitleOrAnotherKey() {
        val info = PdfMetadata.read(pdf("/Title () /TitleSort (x)"))
        assertNull(info.title)
        assertNull(info.author)
    }

    @Test fun readsAnOctalEscape() {
        assertEquals("Café", PdfMetadata.read(pdf("/Title (Caf\\351)")).title)
    }
}
