package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

/** Finds the jar of a plugin of the Kotatsu parsers. A line of the source list is a GitHub repository, its releases, or the URL of a jar. */
object KotatsuPlugin {
    /** The jar of one release. [version] is the tag of the release, or the name of the file. */
    data class Jar(val url: String, val version: String)

    /** The URL of the API of the latest release of a GitHub repository, or null when the URL is not of a repository on GitHub. */
    fun latestReleaseApi(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.host != "github.com" && uri.host != "www.github.com") return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        return "https://api.github.com/repos/${parts[0]}/${parts[1]}/releases/latest"
    }

    /** The jar of a URL that points to a jar. */
    fun direct(url: String): Jar? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        if (!path.endsWith(".jar")) return null
        return Jar(url, path.substringAfterLast('/').removeSuffix(".jar"))
    }

    /** The first jar of a release of the API of GitHub. */
    fun release(data: ByteArray): Jar? {
        val release = runCatching { Json.parseToJsonElement(String(data, Charsets.UTF_8)) as JsonObject }.getOrNull() ?: return null
        val tag = (release["tag_name"] as? JsonPrimitive)?.contentOrNull ?: ""
        val asset = (release["assets"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().firstOrNull { asset ->
            (asset["name"] as? JsonPrimitive)?.contentOrNull?.endsWith(".jar") == true
        } ?: return null
        val download = (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: return null
        return Jar(download, tag.ifEmpty { download.substringAfterLast('/').removeSuffix(".jar") })
    }

    /**
     * The facts of a source of the plugin, or null when the app does not show it. A broken source and a source of
     * novels are not shown, because the parsers give pages, and a broken source does not answer.
     */
    fun info(name: String, title: String, locale: String, contentType: String, broken: Boolean, jar: Jar): SourceInfo? {
        if (broken || contentType == "NOVEL") return null
        return SourceInfo(
            id = name, name = title, language = locale.ifEmpty { "multi" }, version = jar.version,
            code = jar.url, novels = false, adult = contentType == "HENTAI",
        )
    }
}
