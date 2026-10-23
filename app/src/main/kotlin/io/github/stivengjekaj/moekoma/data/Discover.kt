package io.github.stivengjekaj.moekoma.data

import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlinePage
import io.github.stivengjekaj.moekoma.core.SearchHit
import io.github.stivengjekaj.moekoma.core.TitleMix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The titles of Discover: the popular and the latest titles of the installed sources. As in Asabunko. */
class DiscoverFeed(private val sources: Sources, private val installed: () -> List<InstalledSource>, private val scope: CoroutineScope) {
    /** One title of a row, with its cover. */
    data class Item(val hit: SearchHit, val cover: RemoteImage?, val sourceName: String) {
        val id: String get() = hit.source + "|" + hit.series.id
    }

    /** One title of the banner, with its facts from the source when they come. */
    data class Featured(val item: Item, val details: OnlineDetails? = null)

    data class Row(val id: String, val title: String, val items: List<Item>)

    private val rowsState = MutableStateFlow<List<Row>>(emptyList())
    val rows: StateFlow<List<Row>> = rowsState.asStateFlow()
    private val featuredState = MutableStateFlow<List<Featured>>(emptyList())
    val featured: StateFlow<List<Featured>> = featuredState.asStateFlow()
    private val loadingState = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = loadingState.asStateFlow()

    /** The installed sources of the last load. A change of the sources loads the feed again. */
    private var loadedFor: List<String>? = null
    private var current: Job? = null

    /** All covers of the feed, for the art of the empty screens. */
    val covers: List<RemoteImage>
        get() = (featuredState.value.map { it.item } + rowsState.value.flatMap { it.items }).distinctBy { it.id }.mapNotNull { it.cover }

    fun load(force: Boolean = false) {
        val asked = installed().take(SOURCE_LIMIT)
        if (!force && asked.map { it.id } == loadedFor) return
        current?.cancel()
        loadedFor = asked.map { it.id }
        loadingState.value = true
        current = scope.launch {
            try {
                fetch(asked)
            } finally {
                if (current?.isCancelled != true) loadingState.value = false
            }
        }
    }

    private suspend fun fetch(asked: List<InstalledSource>) {
        if (asked.isEmpty()) {
            rowsState.value = emptyList()
            featuredState.value = emptyList()
            return
        }
        val lists = coroutineScope { asked.map { source -> async { lists(source) } }.awaitAll() }
        // The order of the rows follows the order of the sources, not the order of the answers.
        val popular = lists.filter { it.second.isNotEmpty() }.map { it.first to it.second }
        val latest = lists.filter { it.third.isNotEmpty() }.map { it.first to it.third }
        val byHit = lists.flatMap { it.second + it.third }.associateBy { it.hit }
        fun mixed(parts: List<Pair<InstalledSource, List<Item>>>, limit: Int) =
            TitleMix.roundRobin(parts.map { part -> part.second.map { it.hit } }, limit).mapNotNull { byHit[it] }

        val next = mutableListOf<Row>()
        val comics = mixed(popular.filter { !it.first.info.novels }, 24)
        val novels = mixed(popular.filter { it.first.info.novels }, 24)
        if (comics.isNotEmpty()) next += Row("comics", "Popular comics", comics)
        if (novels.isNotEmpty()) next += Row("novels", "Popular novels", novels)
        val updates = mixed(latest, 24)
        if (updates.isNotEmpty()) next += Row("latest", "New chapters", updates)
        if (popular.size > 1) popular.forEach { (source, items) -> next += Row("source:" + source.id, "Popular on ${source.info.name}", items) }
        rowsState.value = next

        // The banner shows the first titles of the mix, with their summary and their genres.
        val top = mixed(popular, 5)
        featuredState.value = top.map { Featured(it) }
        coroutineScope {
            top.forEachIndexed { index, item ->
                val source = asked.firstOrNull { it.id == item.hit.source } ?: return@forEachIndexed
                launch {
                    val details = runCatching { sources.load(source).details(item.hit.series) }.getOrNull() ?: return@launch
                    featuredState.value = featuredState.value.toMutableList().also { list ->
                        if (index in list.indices && list[index].item.id == item.id) list[index] = list[index].copy(details = details)
                    }
                }
            }
        }
    }

    /** The popular and the latest titles of one source. */
    private suspend fun lists(source: InstalledSource): Triple<InstalledSource, List<Item>, List<Item>> {
        val loaded = runCatching { sources.load(source) }.getOrNull() ?: return Triple(source, emptyList(), emptyList())
        fun items(page: OnlinePage?) = page?.items.orEmpty().take(20).map { series ->
            Item(SearchHit(source.id, series), series.cover?.let { RemoteImage.cover(source.id, it) }, source.info.name)
        }
        val top = items(runCatching { loaded.popular(1) }.getOrNull())
        val new = if (loaded.canShowLatest) items(runCatching { loaded.latest(1) }.getOrNull()) else emptyList()
        return Triple(source, top, new)
    }

    companion object {
        /** The most sources that Discover asks, so that the page opens fast. */
        const val SOURCE_LIMIT = 10
    }
}
