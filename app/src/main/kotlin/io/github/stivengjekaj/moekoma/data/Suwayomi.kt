package io.github.stivengjekaj.moekoma.data

import io.github.stivengjekaj.moekoma.core.Fetcher
import io.github.stivengjekaj.moekoma.core.HttpRequest
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SourceInfo
import io.github.stivengjekaj.moekoma.core.SuwayomiSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** A fetcher that adds the headers of the line of the server, such as its password, to each request. */
fun Fetcher.withHeaders(headers: (String) -> Map<String, String>): Fetcher = { request ->
    this(HttpRequest(request.url, request.method, request.headers + headers(request.url), request.body))
}

/**
 * A source of a Suwayomi server as an [OnlineSource]. The server runs the source, so the app keeps no code for it. The
 * [headers] give the password of the server, which only the host of the server gets.
 */
class SuwayomiOnlineSource(
    private val installed: InstalledSource,
    private val source: SuwayomiSource,
    private val client: OkHttpClient,
    private val headers: (String) -> Map<String, String>,
) : OnlineSource {
    override val info: SourceInfo get() = installed.info
    override val canShowLatest: Boolean get() = source.canShowLatest
    override suspend fun popular(page: Int) = source.popular(page)
    override suspend fun latest(page: Int) = source.latest(page)
    override suspend fun search(query: String, page: Int) = source.search(query, page)
    override suspend fun details(series: OnlineSeries) = source.details(series)
    override suspend fun filterOptions() = source.filterOptions()
    override suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>) = source.browse(page, query, filters)
    override suspend fun settingOptions() = source.settingOptions()
    override suspend fun settingValues() = source.settingValues()
    override suspend fun setSetting(id: String, value: OptionValue) = source.setSetting(id, value)

    override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent =
        ChapterContent.Pages(source.pages(chapter).map { RemoteImage.cover(installed.id, it) })

    override suspend fun image(image: RemoteImage): Response = withContext(Dispatchers.IO) {
        val url = image.item as? String ?: throw IOException("The image is not of this source.")
        val response = client.newCall(Request.Builder().url(url).headers(headers(url).toHeaders()).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("The image did not load (HTTP ${response.code}).")
        }
        response
    }
}
