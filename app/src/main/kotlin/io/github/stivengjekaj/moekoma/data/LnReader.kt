package io.github.stivengjekaj.moekoma.data

import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.stivengjekaj.moekoma.core.Fetcher
import io.github.stivengjekaj.moekoma.core.HttpResponse
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.LnReaderPlugin
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

/**
 * The fetcher of the JavaScript sources: the client of the Mihon extensions, with the cookies, the user agent of the
 * web view, and the browser check of Cloudflare. A request with a body and no type gets none, as in a browser.
 */
fun networkFetcher(client: OkHttpClient = Injekt.get<NetworkHelper>().client): Fetcher = { request ->
    withContext(Dispatchers.IO) {
        val type = request.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
        val body = request.body?.toRequestBody(type?.toMediaTypeOrNull())
            ?: if (request.method in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
        val call = Request.Builder().url(request.url).headers(request.headers.toHeaders()).method(request.method, body).build()
        client.newCall(call).execute().use { response ->
            HttpResponse(response.code, response.request.url.toString(), response.headers.associate { (k, v) -> k.lowercase() to v }, response.body.bytes())
        }
    }
}

/** An LNReader plugin as an [OnlineSource]. A chapter is HTML, which the reader of novels shows. */
class LnReaderSource(private val installed: InstalledSource, val plugin: LnReaderPlugin, private val client: OkHttpClient) : OnlineSource {
    override val info: SourceInfo get() = installed.info
    override val canShowLatest: Boolean get() = plugin.canShowLatest
    override suspend fun popular(page: Int) = plugin.popular(page)
    override suspend fun latest(page: Int) = plugin.latest(page)
    override suspend fun search(query: String, page: Int) = plugin.search(query, page)
    override suspend fun details(series: OnlineSeries) = plugin.details(series)
    override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent = ChapterContent.Html(plugin.chapter(chapter.id))
    override suspend fun filterOptions() = plugin.filterOptions()
    override suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>) = plugin.browse(page, query, filters)
    override suspend fun settingOptions() = plugin.settingOptions()
    override suspend fun settingValues() = plugin.settingValues()
    override suspend fun setSetting(id: String, value: OptionValue) = plugin.setSetting(id, value)

    override suspend fun image(image: RemoteImage): Response = withContext(Dispatchers.IO) {
        val url = image.item as? String ?: throw IOException("The image is not of this source.")
        val response = client.newCall(Request.Builder().url(url).headers(plugin.imageHeaders.toHeaders()).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("The image did not load (HTTP ${response.code}).")
        }
        response
    }
}

/** Keeps the code of the JavaScript sources on the device: one file for each source, and a file of its settings. */
class SourceCode(root: File) {
    private val folder = File(root, "Sources")

    fun file(installed: InstalledSource): File =
        File(File(folder, installed.list.toString().uppercase()), installed.info.id.replace('/', '_') + ".code")

    fun settings(installed: InstalledSource): File = File(file(installed).path.removeSuffix(".code") + ".settings.json")

    suspend fun install(installed: InstalledSource, client: OkHttpClient) = withContext(Dispatchers.IO) {
        val bytes = client.newCall(Request.Builder().url(installed.info.code).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The code did not download (HTTP ${response.code}).")
            response.body.bytes()
        }
        val target = file(installed)
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(target)) throw IOException("The code was not saved.")
    }

    fun remove(installed: InstalledSource) {
        file(installed).delete()
        settings(installed).delete()
    }
}
