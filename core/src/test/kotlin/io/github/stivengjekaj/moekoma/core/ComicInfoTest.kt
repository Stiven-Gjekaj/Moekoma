package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicInfoTest {
    @Test fun readsTheFieldsAndTheDirection() {
        val info = ComicInfo.parse(comicInfo(mapOf(
            "Series" to "Yotsuba&amp;!", "Number" to "3", "Title" to "Yotsuba and Rain", "Manga" to "YesAndRightToLeft", "Writer" to "Azuma",
        )))!!
        assertEquals("Yotsuba&!", info.series)
        assertEquals("Azuma", info.writer)
        assertEquals(ReadingMode.RightToLeft, info.readingMode)
        assertEquals("Yotsuba&! 3: Yotsuba and Rain", info.displayTitle)
    }

    @Test fun givesNoDirectionWhenTheMangaFieldIsOnlyYes() {
        assertNull(ComicInfo.parse(comicInfo(mapOf("Manga" to "Yes")))!!.readingMode)
    }

    @Test fun doesNotRepeatATitleThatIsTheSeries() {
        assertEquals("Saga 1", ComicInfo.parse(comicInfo(mapOf("Series" to "Saga", "Number" to "1", "Title" to "saga")))!!.displayTitle)
    }

    @Test fun returnsNullForDataThatIsNotXml() {
        assertNull(ComicInfo.parse("<ComicInfo><Title>".toByteArray()))
    }

    @Test fun doesNotLoadAnExternalEntity() {
        val secret = temporaryFolder().resolve("secret.txt").apply { writeText("secret") }
        val xml = "<?xml version=\"1.0\"?><!DOCTYPE c [<!ENTITY x SYSTEM \"${secret.toURI()}\">]><ComicInfo><Title>&x;</Title></ComicInfo>"
        val info = ComicInfo.parse(xml.toByteArray())
        assertNull(info?.title)
    }
}
