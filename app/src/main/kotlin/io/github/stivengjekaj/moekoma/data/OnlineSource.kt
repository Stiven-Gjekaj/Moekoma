package io.github.stivengjekaj.moekoma.data

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlinePage
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SourceOption
import io.github.stivengjekaj.moekoma.core.SourceInfo
import okhttp3.Response
import okio.FileSystem

/** What a chapter holds: the pages of a comic, or the HTML of a chapter of a novel. */
sealed interface ChapterContent {
    /** The pages, as images that the image loader of the app draws. */
    data class Pages(val pages: List<RemoteImage>) : ChapterContent

    data class Html(val html: String) : ChapterContent
}

/** A source that the app has loaded, such as a Mihon extension. */
interface OnlineSource {
    val info: SourceInfo
    val canShowLatest: Boolean
    suspend fun popular(page: Int): OnlinePage
    suspend fun latest(page: Int): OnlinePage
    suspend fun search(query: String, page: Int): OnlinePage
    suspend fun details(series: OnlineSeries): OnlineDetails
    suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent

    /** Gets an image of the source, such as a cover or a page, with the client and the headers that the source needs. */
    suspend fun image(image: RemoteImage): Response

    /** The filters of the list of the source. Empty when the source has none. */
    suspend fun filterOptions(): List<SourceOption> = emptyList()

    /** A page of the list with filters. With a query, the list is a search. */
    suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>): OnlinePage =
        if (query.isEmpty()) popular(page) else search(query, page)

    /** The settings of the source. Empty when the source has none. */
    suspend fun settingOptions(): List<SourceOption> = emptyList()

    /** The value of each setting now. */
    suspend fun settingValues(): Map<String, OptionValue> = emptyMap()

    /** Keeps a new value of a setting. */
    suspend fun setSetting(id: String, value: OptionValue) {}
}

/**
 * An image of an installed source: a cover URL, or a page that the source knows. The image loader asks the source
 * for it, because many sources need their own headers, cookies, or changes to the image.
 */
data class RemoteImage(
    /** The ID of the installed source. */
    val source: String,
    /** The URL of a cover, or the page of the source. */
    val item: Any,
    /** The key of the image in the cache. */
    val key: String,
) {
    companion object {
        fun cover(source: String, url: String) = RemoteImage(source, url, "$source|$url")
    }
}

/**
 * The sources that the app has loaded in this session, by the ID of their installation. A source holds its classes
 * and its network client, so the sources that the user did not use for the longest time leave the memory.
 */
object LoadedSources {
    private const val LIMIT = 24
    private val sources = LinkedHashMap<String, OnlineSource>(16, 0.75f, true)

    @Synchronized fun get(id: String): OnlineSource? = sources[id]

    @Synchronized fun set(id: String, source: OnlineSource) {
        sources[id] = source
        while (sources.size > LIMIT) sources.remove(sources.keys.first())
    }

    @Synchronized fun remove(id: String) {
        sources.remove(id)
    }
}

/** Draws a [RemoteImage] with the source that gives it. */
class RemoteImageFetcher(private val image: RemoteImage, private val load: suspend (String) -> OnlineSource) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val response = load(image.source).image(image)
        val body = response.body
        return SourceFetchResult(
            source = ImageSource(body.source(), FileSystem.SYSTEM),
            mimeType = body.contentType()?.toString()?.substringBefore(';'),
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val load: suspend (String) -> OnlineSource) : Fetcher.Factory<RemoteImage> {
        override fun create(data: RemoteImage, options: Options, imageLoader: ImageLoader) = RemoteImageFetcher(data, load)
    }
}

class RemoteImageKeyer : Keyer<RemoteImage> {
    override fun key(data: RemoteImage, options: Options) = data.key
}
