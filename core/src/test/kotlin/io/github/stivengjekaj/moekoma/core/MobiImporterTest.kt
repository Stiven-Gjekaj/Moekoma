package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MobiImporterTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test fun palmDocReadsLiteralsSpacesAndCopies() {
        // "ab", then a copy of 3 bytes from 2 back ("aba"), then " c" as one byte, then 2 raw bytes.
        val pair = ((2 shl 3) or (3 - 3)) or 0x8000
        val compressed = bytes(0x61, 0x62, pair shr 8, pair and 0xFF, 0x63 xor 0x80, 0x02, 0x00, 0xFF)
        assertArrayEquals(bytes(0x61, 0x62, 0x61, 0x62, 0x61, 0x20, 0x63, 0x00, 0xFF), MobiImporter.palmDoc(compressed))
    }

    @Test fun trailingEntriesComeOffTheEnd() {
        // One entry of 3 bytes, with its size in its last byte. Before it, the multibyte mark 0x01
        // tells 1 + 1 bytes: the mark and one byte of the text.
        val record = bytes(0x41, 0x42, 0x43, 0x01, 0x58, 0x59, 0x83)
        assertEquals(5, MobiImporter.trailingSize(record, 0b11))
        assertEquals(3, MobiImporter.trailingSize(record, 0b10))
        assertEquals(0, MobiImporter.trailingSize(record, 0))
    }

    @Test fun fragmentsGoIntoTheirSkeleton() {
        val text = "<html><body></body></html><p>One</p><p>Two</p><html><body></body></html><p>Three</p>".toByteArray()
        val first = "<html><body></body></html>".length
        val skeletons = listOf(MobiImporter.Skeleton(2, 0, first), MobiImporter.Skeleton(1, first + 20, first))
        val fragments = listOf(
            MobiImporter.Fragment(12, 0, 10),
            MobiImporter.Fragment(22, 10, 10),
            MobiImporter.Fragment(first + 20 + 12, 0, 12),
        )
        val parts = MobiImporter.assemble(text, skeletons, fragments).map { String(it) }
        assertEquals(listOf("<html><body><p>One</p><p>Two</p></body></html>", "<html><body><p>Three</p></body></html>"), parts)
    }

    @Test fun indexValuesFollowTheControlByte() {
        // Tag 1 has one value, and tag 6 has two values in each entry. The control byte marks both.
        val tags = listOf(MobiIndex.Tag(1, 1, 0x01, false), MobiIndex.Tag(6, 2, 0x02, false), MobiIndex.Tag(0, 0, 0, true))
        // 300 is 0x02 0xAC in 7-bit bytes: 0b10 and 0b0101100.
        val values = MobiIndex.values(listOf(0x83, 0x02, 0xAC, 0x8A), tags, listOf(0x03))
        assertEquals(listOf(3), values[1])
        assertEquals(listOf(300, 10), values[6])
    }

    @Test fun kf8LinksPointToTheFiles() {
        val images = MobiImporter.Resources().apply { byNumber[11] = "00011.jpg" }
        val html = """<img src="kindle:embed:000B?mime=image/jpeg"/><link href="kindle:flow:0001?mime=text/css"/><a href="kindle:pos:fid:0004:off:0000000000">x</a>"""
        assertEquals(
            """<img src="../images/00011.jpg"/><link href="../styles/flow0001.css"/><a href="#">x</a>""",
            MobiImporter.links(html, images, listOf("flow0001.css")),
        )
    }

    @Test fun putsAnSvgFlowIntoThePage() {
        val svg = "<?xml version='1.0' encoding='utf-8'?><svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"../images/00001.jpg\"/></svg>"
        val html = "<div><img src=\"../styles/flow0004.svg\"/></div><img src=\"../images/00002.jpg\"/>"
        assertEquals(
            "<div><svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"../images/00001.jpg\"/></svg></div><img src=\"../images/00002.jpg\"/>",
            MobiImporter.inlineSvg(html, mapOf("flow0004.svg" to svg)),
        )
    }

    @Test fun importsAMobi6BookWithChaptersAnImageAndTheFacts() {
        val html = "<html><head></head><body><h1>The Sea</h1><p>Waves came.</p><img recindex=\"00001\"><mbp:pagebreak/><p><font size=\"7\">Part Two</font></p><p>The shore.</p></body></html>"
        val image = bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(12)
        val folder = File(temporaryFolder(), "book")
        val imported = MobiImporter.importBook(mobiFile(html.toByteArray(), listOf(image), "Mina Ko", "The Long Sea"), "sea.mobi", folder)
        val novel = imported.novel
        assertEquals("The Long Sea", novel.title)
        assertEquals("Mina Ko", novel.author)
        assertEquals(listOf("The Sea", "Part Two"), novel.chapters.map { it.title })
        val first = File(folder, novel.chapters[0].path).readText()
        assertTrue(first, "src=\"../images/00001.jpg\"" in first)
        assertTrue(File(folder, "images/00001.jpg").exists())
    }

    @Test fun refusesABookWithDrm() {
        val folder = File(temporaryFolder(), "book")
        assertThrows(MobiException.Encrypted::class.java) {
            MobiImporter.importBook(mobiFile("<p>x</p>".toByteArray(), emptyList(), null, null, encryption = 2), "locked.azw", folder)
        }
        assertFalse(folder.exists())
    }

    @Test fun refusesAFileThatIsNotMobi() {
        assertThrows(MobiException.NotMobi::class.java) {
            MobiImporter.importBook(ByteArray(200) { 0x20 }, "not.mobi", File(temporaryFolder(), "b"))
        }
    }

    /** A MOBI 6 file with no compression: record 0, one text record, and the image records. */
    private fun mobiFile(text: ByteArray, images: List<ByteArray>, author: String?, title: String?, encryption: Int = 0): ByteArray {
        val exthBody = ByteArrayOutputStream()
        var count = 0
        for ((type, value) in listOf(100 to author, 503 to title)) {
            value ?: continue
            exthBody.write(be32(type.toLong()) + be32((8 + value.toByteArray().size).toLong()) + value.toByteArray())
            count++
        }
        val exth = "EXTH".toByteArray() + be32((12 + exthBody.size()).toLong()) + be32(count.toLong()) + exthBody.toByteArray()

        val mobi = ByteArray(0xE8)
        fun put(value: Long, offset: Int) = be32(value).copyInto(mobi, offset)
        "MOBI".toByteArray().copyInto(mobi, 0)
        put(0xE8, 4)
        put(65001, 12)
        put(6, 20)
        put(if (images.isEmpty()) 0xFFFF_FFFFL else 2, 0x5C)
        put(0x40, 0x70)
        put(0xFFFF_FFFFL, 0x44)
        val record0 = be16(1) + be16(0) + be32(text.size.toLong()) + be16(1) + be16(4096) + be16(encryption) + be16(0) + mobi + exth
        // The name of the book is not set, so the title comes from the EXTH header.
        val records = listOf(record0, text) + images
        var offset = 78 + records.size * 8 + 2
        val file = ByteArrayOutputStream()
        file.write(ByteArray(60) + "BOOKMOBI".toByteArray() + ByteArray(8) + be16(records.size))
        for (record in records) {
            file.write(be32(offset.toLong()) + ByteArray(4))
            offset += record.size
        }
        file.write(ByteArray(2))
        records.forEach { file.write(it) }
        return file.toByteArray()
    }

    private fun be32(value: Long) = bytes((value shr 24).toInt() and 0xFF, (value shr 16).toInt() and 0xFF, (value shr 8).toInt() and 0xFF, value.toInt() and 0xFF)
    private fun be16(value: Int) = bytes((value shr 8) and 0xFF, value and 0xFF)
}
