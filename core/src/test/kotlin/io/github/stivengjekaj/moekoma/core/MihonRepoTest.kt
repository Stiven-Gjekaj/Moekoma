@file:OptIn(ExperimentalSerializationApi::class)

package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.GZIPOutputStream

class MihonRepoTest {
    private val legacyRepo = """{"meta":{"name":"Shelf","shortName":"S","website":"https://shelf.test","signingKeyFingerprint":"ab12"}}"""

    private val legacyIndex = """
        [{"name":"Tachiyomi: Sunrise","pkg":"eu.kanade.tachiyomi.extension.all.sunrise","apk":"tachiyomi-all.sunrise-v1.4.3.apk","lang":"all","code":3,
          "version":"1.4.3","nsfw":1,"sources":[{"name":"Sunrise","lang":"en","id":11,"baseUrl":"https://sun.test"},
          {"name":"Sunrise","lang":"fr","id":12,"baseUrl":"https://sun.test/fr"}]},
         {"name":"Tachiyomi: Old","pkg":"eu.kanade.tachiyomi.extension.en.old","apk":"old-v1.2.1.apk","lang":"en","code":1,"version":"1.2.1","nsfw":0,
          "sources":[{"name":"Old","lang":"en","id":5,"baseUrl":"https://old.test"}]}]
    """.trimIndent()

    @Test fun readsTheOldIndexWithItsRepoFile() {
        val files = mapOf("https://shelf.test/repo/index.min.json" to legacyIndex, "https://shelf.test/repo/repo.json" to legacyRepo)
        val store = MihonRepo.load("https://shelf.test/repo/index.min.json") { files.getValue(it).toByteArray() }
        assertEquals("ab12", store.signingKey)
        val sunrise = store.extensions.first()
        assertEquals("Sunrise", sunrise.name)
        assertEquals("https://shelf.test/repo/apk/tachiyomi-all.sunrise-v1.4.3.apk", sunrise.apkUrl)
        assertEquals("https://shelf.test/repo/icon/eu.kanade.tachiyomi.extension.all.sunrise.png", sunrise.iconUrl)
        assertEquals(1.4, sunrise.lib, 0.0)
        // One source for each language. An extension for an old library is not there.
        val infos = MihonRepo.sourceInfos(store)
        assertEquals(listOf("11" to "en", "12" to "fr"), infos.map { it.id to it.language })
        assertEquals(listOf(true, true), infos.map { it.adult })
        assertEquals(sunrise.apkUrl, infos.first().code)
    }

    @Test fun followsTheNewIndexOfARepoThatMoved() {
        val store = MihonRepo.Store(
            name = "Shelf", signingKey = "cd34",
            extensionList = MihonRepo.ExtensionList(listOf(
                MihonRepo.Extension(
                    name = "Moon", packageName = "x.moon", resources = MihonRepo.Resources("https://cdn.test/moon.apk", "https://cdn.test/moon.png"),
                    extensionLib = "1.6", versionCode = 9, versionName = "1.6.9", contentWarning = 1,
                    sources = listOf(MihonRepo.Source(77, "Moon", "en", "https://moon.test")),
                ),
            )),
        )
        val pb = gzip(ProtoBuf.encodeToByteArray(MihonRepo.Store.serializer(), store))
        val moved = """{"index_v2":"https://cdn.test/index.pb","meta":{"name":"Shelf","website":"w","signingKeyFingerprint":"cd34"}}"""
        val files = mapOf(
            "https://shelf.test/repo/index.min.json" to """[{"name":"Outdated App","pkg":"x.outdated","apk":"o.apk","lang":"all","code":1,"version":"1.4.1","nsfw":0}]""".toByteArray(),
            "https://shelf.test/repo/repo.json" to moved.toByteArray(),
            "https://cdn.test/index.pb" to pb,
        )
        val read = MihonRepo.load("https://shelf.test/repo/index.min.json") { files.getValue(it) }
        assertEquals("cd34", read.signingKey)
        assertEquals(listOf("Moon"), read.extensions.map { it.name })
        assertEquals(listOf("77"), MihonRepo.sourceInfos(read).map { it.id })
        assertEquals("The user can paste the repo.json too.", read, MihonRepo.load("https://shelf.test/repo/repo.json") { files.getValue(it) })
    }

    @Test fun readsTheRealRepositoryOfKeiyoushi() {
        assumeTrue(System.getenv("MOEKOMA_LIVE") == "1")
        val store = MihonRepo.load("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json") { URI(it).toURL().readBytes() }
        val infos = MihonRepo.sourceInfos(store)
        println("Keiyoushi: ${store.extensions.size} extensions, ${infos.size} sources, key ${store.signingKey}")
        assertEquals("9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2", store.signingKey)
        assert(infos.size > 500)
    }

    private fun gzip(data: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()
}
