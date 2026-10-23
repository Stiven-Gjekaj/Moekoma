package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** The kinds of source that a line of the source list can be. The names are the names of Asabunko. */
@Serializable
enum class SourceKind(val label: String) {
    /** An OPDS catalog, such as Komga, Kavita, Calibre-web, or Suwayomi. */
    @SerialName("opds") Opds("OPDS catalog"),

    /** A list of LNReader plugins, for novels on the web. */
    @SerialName("lnreader") LnReader("LNReader plugins"),

    /** A list of Aidoku sources. */
    @SerialName("aidoku") Aidoku("Aidoku sources"),

    /** A repository of Paperback 0.9 extensions. */
    @SerialName("paperback") Paperback("Paperback extensions"),

    /** A Suwayomi server, which runs the extensions of Mihon. */
    @SerialName("suwayomi") Suwayomi("Suwayomi server");

    companion object {
        /** The kind that the URL tells, or null when only its content can tell. */
        fun guess(url: String): SourceKind? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val text = url.lowercase()
            val path = (uri.rawPath ?: "").lowercase()
            if (path.endsWith("plugins.min.json") || path.endsWith("plugins.json") || "lnreader" in text) return LnReader
            if (path.endsWith("versioning.json") || "paperback" in text || "inkdex" in text) return Paperback
            if (path.endsWith("index.min.json") || "aidoku" in text) return Aidoku
            if ("/opds" in path) return Opds
            if ("suwayomi" in text || "tachidesk" in text || uri.port == 4567 || path.endsWith("/api/graphql")) return Suwayomi
            return null
        }

        /** The kind that the content of the URL tells. */
        fun detect(data: ByteArray): SourceKind? {
            val head = String(data, 0, minOf(data.size, 4096), Charsets.UTF_8)
            if ("<feed" in head || ("opds" in head && "\"metadata\"" in head)) return Opds
            val json = runCatching { Json.parseToJsonElement(String(data, Charsets.UTF_8)) }.getOrNull() ?: return null
            if (json is JsonObject) {
                if ("builtWith" in json || ("repository" in json && "sources" in json)) return Paperback
                if (json["sources"] is JsonArray) return Aidoku
                if ("navigation" in json || "publications" in json) return Opds
            }
            val first = (json as? JsonArray)?.firstOrNull() as? JsonObject
            if (first != null) {
                val url = (first["url"] as? JsonPrimitive)?.contentOrNull
                if ("site" in first || url?.endsWith(".js") == true) return LnReader
                if ("file" in first || "downloadURL" in first) return Aidoku
            }
            return null
        }
    }
}

/** One line of the source list. A password is not here. It is in the encrypted store of the device. */
@Serializable
data class SourceLink(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    /** The URL with no user name and no password. */
    val url: String,
    val username: String? = null,
    /** The kind, or null until the app reads the content of the URL. */
    val kind: SourceKind? = null,
    /** The name that the source gives, such as the title of a catalog. */
    val name: String? = null,
) {
    /** The scheme and the host only. A URL can hold a key in its path. */
    val shortAddress: String
        get() {
            val uri = runCatching { URI(url) }.getOrNull()
            val port = uri?.port?.takeIf { it >= 0 }?.let { ":$it" } ?: ""
            return "${uri?.scheme ?: "https"}://${uri?.host ?: ""}$port/..."
        }

    val host: String? get() = runCatching { URI(url).host }.getOrNull()
}

/** The source list as text, one URL on each line, as in ZettaStream. "https://user:password@host/opds" gives a user name and a password. */
object SourceList {
    data class Line(val link: SourceLink, val password: String?)

    /**
     * Reads the text. Empty lines, lines that start with "#", and lines that are not URLs are skipped.
     * A line that is two times in the text is one source. [existing] keeps the ID, the kind, and the name
     * of each source that stays.
     */
    fun parse(text: String, existing: List<SourceLink> = emptyList()): List<Line> {
        val lines = mutableListOf<Line>()
        val seen = mutableSetOf<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val full = if ("://" in line) line else "https://$line"
            val uri = runCatching { URI(full) }.getOrNull() ?: continue
            if (uri.scheme != "http" && uri.scheme != "https" || uri.host.isNullOrEmpty()) continue
            val info = uri.rawUserInfo
            val username = info?.substringBefore(':')?.let(::decode)
            val password = info?.takeIf { ':' in it }?.substringAfter(':')?.let(::decode)
            val url = if (info == null) full else full.replaceFirst("$info@", "")
            val key = "$url|${username ?: ""}"
            if (!seen.add(key)) continue
            val old = existing.firstOrNull { it.url == url && it.username == username }
            val link = SourceLink(
                id = old?.id ?: UUID.randomUUID(), url = url, username = username,
                kind = old?.kind ?: SourceKind.guess(url), name = old?.name,
            )
            lines += Line(link, password)
        }
        return lines
    }

    /** The text of the list. A user name stays in its line, but a password does not. */
    fun format(links: List<SourceLink>): String = links.joinToString("\n") { link ->
        val user = link.username ?: return@joinToString link.url
        val scheme = link.url.substringBefore("://")
        "$scheme://${encode(user)}@${link.url.substringAfter("://")}"
    }

    private fun decode(text: String) = runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    private fun encode(text: String) = text.replace("%", "%25").replace("@", "%40").replace(":", "%3A").replace("/", "%2F")
}
