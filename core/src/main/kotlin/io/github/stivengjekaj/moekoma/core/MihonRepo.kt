@file:OptIn(ExperimentalSerializationApi::class)

package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/** One extension of a repository of Mihon extensions. One APK can hold many sources, such as one for each language. */
data class MihonExtension(
    val name: String,
    val pkg: String,
    val apkUrl: String,
    val iconUrl: String?,
    /** The version of the extensions library, such as 1.4. */
    val lib: Double,
    val versionCode: Long,
    val versionName: String,
    val adult: Boolean,
    val sources: List<Source>,
) {
    data class Source(val id: Long, val name: String, val language: String, val baseUrl: String)
}

/** The extensions of a repository, and the SHA-256 of the key that signs them. */
data class MihonStore(val name: String, val signingKey: String, val extensions: List<MihonExtension>)

class MihonRepoException(message: String) : Exception(message)

/**
 * Reads a repository of Mihon extensions, as Mihon does. A repository has three forms:
 * the old index.min.json with a repo.json next to it, a repo.json that points to an index in Protocol Buffers,
 * and the index in Protocol Buffers or JSON.
 */
object MihonRepo {
    /** The versions of the extensions library that the app runs. These are the versions of Mihon. */
    val supportedLibs = listOf(1.4, 1.6)

    private val json = Json { ignoreUnknownKeys = true }

    /** Reads the repository at [url]. [fetch] gets the bytes of a URL. */
    fun load(url: String, fetch: (String) -> ByteArray): MihonStore = load(url, fetch, depth = 0)

    private fun load(url: String, fetch: (String) -> ByteArray, depth: Int): MihonStore {
        if (depth > 3) throw MihonRepoException("The repository points to itself.")
        val data = gunzip(fetch(url))
        return when (data.firstOrNull { it > 0x20 }?.toInt()?.toChar()) {
            '[' -> {
                if (!url.substringBefore('?').endsWith("/index.min.json")) throw MihonRepoException("An old index must be at index.min.json.")
                val base = url.substringBefore('?').removeSuffix("/index.min.json")
                val repo = json.decodeFromString(LegacyRepo.serializer(), String(gunzip(fetch("$base/repo.json")), Charsets.UTF_8))
                // A repository that moved to the new index keeps a stub in its old index.
                repo.indexV2?.let { return load(it, fetch, depth + 1) }
                val list = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LegacyExtension.serializer()), String(data, Charsets.UTF_8))
                MihonStore(repo.meta.name, repo.meta.signingKeyFingerprint, list.map { it.extension(base) })
            }
            '{' -> {
                val text = String(data, Charsets.UTF_8)
                val repo = runCatching { json.decodeFromString(LegacyRepo.serializer(), text) }.getOrNull()
                if (repo != null) {
                    repo.indexV2?.let { return load(it, fetch, depth + 1) }
                    val base = url.substringBefore('?').substringBeforeLast('/')
                    return load("$base/index.min.json", fetch, depth + 1)
                }
                store(json.decodeFromString(Store.serializer(), text), fetch)
            }
            else -> store(ProtoBuf.decodeFromByteArray(Store.serializer(), data), fetch)
        }
    }

    private fun store(store: Store, fetch: (String) -> ByteArray): MihonStore {
        val list = store.extensionList ?: store.extensionListUrl?.let { listUrl ->
            val data = gunzip(fetch(listUrl))
            if (data.firstOrNull()?.toInt() == '{'.code) json.decodeFromString(ExtensionList.serializer(), String(data, Charsets.UTF_8))
            else ProtoBuf.decodeFromByteArray(ExtensionList.serializer(), data)
        } ?: throw MihonRepoException("The repository has no list of extensions.")
        return MihonStore(store.name, store.signingKey, list.extensions.map { it.extension() })
    }

    /** The sources that the app can install, one for each source of each extension that the app can run. */
    fun sourceInfos(store: MihonStore): List<SourceInfo> = store.extensions.filter { it.lib in supportedLibs }.flatMap { extension ->
        extension.sources.map { source ->
            SourceInfo(
                id = source.id.toString(), name = source.name, language = source.language, version = extension.versionName,
                icon = extension.iconUrl, code = extension.apkUrl, novels = false, adult = extension.adult,
            )
        }
    }

    private fun gunzip(data: ByteArray): ByteArray =
        if (data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(data)).readBytes() else data

    @Serializable
    private class LegacyRepo(@SerialName("index_v2") val indexV2: String? = null, val meta: Meta) {
        @Serializable
        class Meta(val name: String, val signingKeyFingerprint: String)
    }

    @Serializable
    private class LegacyExtension(
        val name: String, val pkg: String, val apk: String, val lang: String, val code: Long, val version: String,
        val nsfw: Int = 0, val sources: List<Source>? = null,
    ) {
        @Serializable
        class Source(val id: Long, val lang: String, val name: String, val baseUrl: String = "")

        fun extension(base: String) = MihonExtension(
            name = name.removePrefix("Tachiyomi: "), pkg = pkg, apkUrl = "$base/apk/$apk", iconUrl = "$base/icon/$pkg.png",
            lib = version.substringBeforeLast('.').toDoubleOrNull() ?: 0.0, versionCode = code, versionName = version, adult = nsfw == 1,
            sources = sources.orEmpty().map { MihonExtension.Source(it.id, it.name, it.lang, it.baseUrl) },
        )
    }

    /** The index of Mihon, in Protocol Buffers or in JSON. The numbers are the numbers of Mihon. */
    @Serializable
    internal class Store(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val badgeLabel: String = "",
        @ProtoNumber(3) val signingKey: String,
        @ProtoNumber(101) val extensionList: ExtensionList? = null,
        @ProtoNumber(102) val extensionListUrl: String? = null,
    )

    @Serializable
    internal class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList())

    @Serializable
    internal class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        /** 0 is not given, 1 safe, 2 mixed, and 3 for adults. */
        @ProtoNumber(7) val contentWarning: Int = 0,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    ) {
        fun extension() = MihonExtension(
            name = name, pkg = packageName, apkUrl = resources.apkUrl, iconUrl = resources.iconUrl.ifEmpty { null },
            lib = extensionLib.toDoubleOrNull() ?: 0.0, versionCode = versionCode, versionName = versionName,
            adult = contentWarning == 3, sources = sources.map { MihonExtension.Source(it.id, it.name, it.language, it.homeUrl) },
        )
    }

    @Serializable
    internal class Resources(@ProtoNumber(1) val apkUrl: String, @ProtoNumber(2) val iconUrl: String = "")

    @Serializable
    internal class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
    )
}
