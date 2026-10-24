package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.ChapterGroups
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SearchHit
import io.github.stivengjekaj.moekoma.core.SourceChoice
import io.github.stivengjekaj.moekoma.core.SourceInfo
import io.github.stivengjekaj.moekoma.core.SourceLink
import io.github.stivengjekaj.moekoma.core.SourceOption
import io.github.stivengjekaj.moekoma.core.TitleMatch
import io.github.stivengjekaj.moekoma.data.RemoteImage
import io.github.stivengjekaj.moekoma.ui.theme.Corner
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/** A title page: the same title in some sources. With [findOthers], the page also looks for it in the other sources. */
data class TitleRoute(val hits: List<SearchHit>, val findOthers: Boolean)

/** A cover of a source, which the image loader gets through the source. */
@Composable
fun SourceCover(image: Any?, title: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(Corner - 4.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(shape).background(palette.surfaceHigh).border(palette.outlineWidth, palette.outline, shape), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, textAlign = TextAlign.Center, maxLines = 5, modifier = Modifier.padding(10.dp))
        if (image != null) {
            val request = remember(image) { ImageRequest.Builder(context).data(image).size(600).build() }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** A title in a grid or a row: the cover, the title, and a line under it. */
@Composable
fun TitleTile(title: String, detail: String?, cover: Any?, modifier: Modifier = Modifier, badge: Int = 0, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.clickable(onClick = onClick).semantics(mergeDescendants = true) {}) {
        Box {
            SourceCover(cover, title, Modifier.fillMaxWidth())
            if (badge > 1) {
                Text(
                    "$badge", style = MaterialTheme.typography.labelMedium, color = palette.onAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).widthIn(min = 22.dp).clip(CircleShape).background(palette.accent).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** The chips of the ranges of chapters, or of other choices: one of them is on. */
@Composable
fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, describe: (String) -> String = { it }) {
    val palette = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                label, style = MaterialTheme.typography.labelLarge, color = if (on) palette.onAccent else palette.textPrimary,
                modifier = Modifier.clip(CircleShape).background(if (on) palette.accent else palette.surfaceHigh)
                    .clickable { onSelect(index) }.heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 9.dp)
                    .semantics { contentDescription = describe(label) + if (on) ", selected" else "" },
            )
        }
    }
}

/**
 * A series or a novel: its facts, its chapters, and a button to add it to the read list. The title can be in many
 * sources. The page asks each one and shows the source with the most chapters. The user can choose another source.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineSeriesScreen(container: AppContainer, route: TitleRoute, onRead: (UUID) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val library by container.model.library.collectAsState()
    val scope = rememberCoroutineScope()
    val sources = container.sources
    var hits by remember { mutableStateOf(route.hits) }
    var chosen by remember { mutableStateOf(route.hits.first()) }
    /** True after the user chooses a source. Then a late answer does not change the source. */
    var userChose by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, OnlineDetails>() }
    val faults = remember { mutableStateMapOf<String, String>() }
    var adding by remember { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    var byVolume by rememberSaveable { mutableStateOf(false) }
    var range by remember { mutableStateOf<Int?>(null) }
    var sourceMenu by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    // A passed browser check loads the page again.
    val passes by BrowserChecks.passes.collectAsState()

    fun installedOf(hit: SearchHit) = library.installed.firstOrNull { it.id == hit.source }
    fun bookOf(hit: SearchHit): Book? = installedOf(hit)?.let { library.book(sources.originOf(it, hit.series.id)) }
    val installed = installedOf(chosen)
    val book = bookOf(chosen)
    val details = answers[chosen.source]
    val fault = faults[chosen.source]

    // At most four sources read a title at the same time, so that a slow source, such as a WebAssembly source, does not
    // keep the processor from the chosen source.
    val gate = remember { Semaphore(4) }

    suspend fun ask(asked: List<SearchHit>) = coroutineScope {
        asked.map { hit ->
            async {
                val source = installedOf(hit) ?: return@async
                try {
                    answers[hit.source] = gate.withPermit { sources.load(source).details(hit.series) }
                } catch (e: Exception) {
                    faults[hit.source] = e.message ?: "The source did not answer. (${e.javaClass.simpleName})"
                }
            }
        }.awaitAll()
    }

    // Asks each source for the title at the same time. The source of a book in the library wins, so that the place
    // of the user stays. Else the source with the most chapters wins.
    LaunchedEffect(reload, passes) {
        answers.clear()
        faults.clear()
        if (!userChose) hits.firstOrNull { bookOf(it) != null }?.let { chosen = it }
        ask(hits)
        if (!userChose && hits.none { bookOf(it) != null } && hits.size > 1) {
            val best = SourceChoice.best(hits.map { hit -> hit.source to answers[hit.source]?.let { SourceChoice.distinctChapters(it.chapters) } })
            hits.firstOrNull { it.source == best }?.let { chosen = it }
        }
        if (route.findOthers) {
            // The other sources of the same kind that have the same title join the source menu.
            val key = TitleMatch.key(chosen.series.title)
            val novels = installedOf(chosen)?.info?.novels
            // A list can install hundreds of sources, so the page asks at most 24 of them: the sources of the books of
            // the library first, then the others in their order.
            val used = library.books.mapNotNull { it.origin }.map { it.list to it.source }.toSet()
            val others = library.installed.filter { other -> other.info.novels == novels && hits.none { it.source == other.id } }
                .sortedByDescending { (it.list to it.info.id) in used }.take(24)
            val extra = mutableListOf<SearchHit>()
            sources.search(chosen.series.title, others, limit = 4) { id, items ->
                items.firstOrNull { TitleMatch.key(it.title) == key }?.let { extra += SearchHit(id, it) }
            }
            if (extra.isNotEmpty()) {
                hits = hits + extra
                ask(extra)
            }
        }
    }
    // A book of the chosen source gets its new chapters.
    LaunchedEffect(chosen) { book?.let { runCatching { sources.refresh(it) } } }

    suspend fun add(details: OnlineDetails, listed: Boolean): Book? {
        val source = installed ?: return null
        adding = true
        return try {
            sources.add(details, source, listed)
        } catch (e: Exception) {
            container.model.fault.value = "The series was not added: ${e.message}"
            null
        } finally {
            adding = false
        }
    }

    /** The place of a chapter of the source in the book, which has all of them in order. */
    fun position(book: Book, details: OnlineDetails, index: Int): Int =
        container.model.novel(book)?.chapters?.indexOfFirst { it.remote == details.chapters[index].id }?.takeIf { it >= 0 } ?: index

    fun open(index: Int, details: OnlineDetails) = scope.launch {
        val added = add(details, listed = book?.listed ?: false) ?: return@launch
        val at = position(added, details, index)
        container.model.update(added.id) { it.read(chapter = at, position = 0.0) }
        onRead(added.id)
    }

    fun markRead(upTo: Int, details: OnlineDetails) = scope.launch {
        val added = add(details, listed = book?.listed ?: false) ?: return@launch
        val at = position(added, details, upTo)
        container.model.update(added.id) { if (at + 1 < it.pageCount) it.read(chapter = at + 1, position = 0.0) else it.read(chapter = at, position = 1.0) }
    }

    fun line(hit: SearchHit): String {
        val name = installedOf(hit)?.info?.name ?: "Source"
        val answer = answers[hit.source]
        return when {
            answer != null -> SourceChoice.distinctChapters(answer.chapters).let { "$name · " + if (it == 1) "1 chapter" else "$it chapters" }
            faults[hit.source] != null -> "$name · no answer"
            else -> "$name · loading"
        }
    }

    SubScreen("", onBack) {
        LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val cover = (details?.series?.cover ?: chosen.series.cover)?.let { RemoteImage.cover(chosen.source, it) }
                    SourceCover(cover, chosen.series.title, Modifier.width(110.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(details?.series?.title ?: chosen.series.title, style = MaterialTheme.typography.titleLarge)
                        details?.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary) }
                        details?.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.heightIn(min = 36.dp).clickable(enabled = hits.size > 1) { sourceMenu = true }
                                    .semantics { contentDescription = "Source, ${installed?.info?.name ?: ""}" },
                            ) {
                                Text(installed?.info?.name ?: "Source", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                                if (hits.size > 1) {
                                    Text(" · ${hits.size} sources", style = MaterialTheme.typography.bodySmall, color = palette.accent)
                                    Icon(Icons.Outlined.UnfoldMore, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                                hits.forEach { hit ->
                                    DropdownMenuItem(
                                        text = { Text(line(hit)) },
                                        leadingIcon = { if (hit == chosen) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) },
                                        onClick = { chosen = hit; userChose = true; range = null; sourceMenu = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (details != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(
                            if (book?.lastRead == null) "Start reading" else "Continue",
                            { if (book != null) onRead(book.id) else open(0, details) },
                            enabled = details.chapters.isNotEmpty(),
                        )
                        IconButton(
                            enabled = !adding,
                            onClick = {
                                scope.launch {
                                    if (book != null && book.listed) {
                                        // A book with no place leaves the library. A book with a place stays for Continue reading.
                                        if (book.lastRead == null) container.model.delete(book) else container.model.update(book.id) { it.copy(listed = false) }
                                    } else {
                                        add(details, listed = true)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                if (book?.listed == true) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (book?.listed == true) "Remove from the read list" else "Add to the read list",
                                tint = palette.accent,
                            )
                        }
                    }
                }
                details.summary?.let { item { Summary(it) } }
                if (details.genres.isNotEmpty()) item { Text(details.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
            }
            when {
                fault != null -> item {
                    EmptyArt("The source did not answer", fault) { PillButton("Try again", { reload++ }) }
                }
                details == null -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        CircularProgressIndicator(color = palette.accent)
                        if (hits.size > 1) Text("Comparing ${hits.size} sources", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                else -> {
                    val titles = details.chapters.map { it.title }
                    val volumes = ChapterGroups.volumes(titles)
                    val ranges = ChapterGroups.ranges(titles.size)
                    val shownVolumes = if (byVolume) volumes else null
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (titles.size == 1) "1 chapter" else "${titles.size} chapters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Oldest first" else "Newest first", color = palette.accent) }
                        }
                    }
                    if (volumes != null) item {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(false to "Chapters", true to "Volumes").forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = byVolume == value, onClick = { byVolume = value },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    colors = SegmentedButtonDefaults.colors(activeContainerColor = palette.accent, activeContentColor = palette.onAccent),
                                ) { Text(label) }
                            }
                        }
                    }
                    val isRead = { index: Int -> book != null && book.lastRead != null && (book.finished || position(book, details, index) < book.page) }
                    val isCurrent = { index: Int -> book != null && book.lastRead != null && book.page == index }
                    if (shownVolumes != null) {
                        val order = if (newestFirst) shownVolumes.reversed() else shownVolumes
                        items(order, key = { "v:" + it.name }) { volume ->
                            VolumeCard(volume, newestFirst, details, isRead, isCurrent, onOpen = { open(it, details) }, onMarkRead = { markRead(it, details) }) {
                                // The first chapter of the volume that the user did not read, or its first chapter.
                                val start = if (book?.lastRead != null) volume.chapters.firstOrNull { it >= book.page } ?: volume.chapters[0] else volume.chapters[0]
                                open(start, details)
                            }
                        }
                    } else {
                        if (ranges.isEmpty()) return@LazyColumn
                        val defaultRange = if (book?.lastRead != null) ranges.indexOfFirst { book.page in it }.coerceAtLeast(0) else if (newestFirst) ranges.size - 1 else 0
                        val selected = (range ?: defaultRange).coerceIn(0, ranges.size - 1)
                        if (ranges.size > 1) item {
                            val order = if (newestFirst) ranges.indices.reversed().toList() else ranges.indices.toList()
                            Chips(order.map { ChapterGroups.name(ranges[it]) }, order.indexOf(selected), { range = order[it] }) { "Chapters $it" }
                        }
                        val shown = ranges[selected].toList().let { if (newestFirst) it.reversed() else it }
                        items(shown, key = { "c:$it" }) { index ->
                            Card(padding = PaddingValues(0.dp)) {
                                ChapterRow(details, index, isRead(index), isCurrent(index), { open(index, details) }, { markRead(index, details) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A summary of six lines. "More" shows all of it, so that the chapters stay near the top. */
@Composable
private fun Summary(text: String) {
    val palette = LocalPalette.current
    var full by rememberSaveable(text) { mutableStateOf(false) }
    var long by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text, style = MaterialTheme.typography.bodyLarge, maxLines = if (full) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!full) long = it.hasVisualOverflow },
        )
        if (long || full) TextButton(onClick = { full = !full }) { Text(if (full) "Less" else "More", color = palette.accent) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(details: OnlineDetails, index: Int, read: Boolean, current: Boolean, onOpen: () -> Unit, onMarkRead: () -> Unit) {
    val palette = LocalPalette.current
    val chapter = details.chapters[index]
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp).alpha(if (read) 0.55f else 1f),
        ) {
            Column(Modifier.weight(1f)) {
                Text(chapter.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (current) FontWeight.Bold else null)
                val facts = listOfNotNull(chapter.date, chapter.group).joinToString(" · ")
                if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            if (current) Icon(Icons.Filled.Bookmark, contentDescription = "You read here", tint = palette.accent)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Mark as read up to here") }, onClick = { menu = false; onMarkRead() })
        }
    }
}

@Composable
private fun VolumeCard(
    volume: ChapterGroups.Volume, newestFirst: Boolean, details: OnlineDetails, isRead: (Int) -> Boolean, isCurrent: (Int) -> Boolean,
    onOpen: (Int) -> Unit, onMarkRead: (Int) -> Unit, onReadVolume: () -> Unit,
) {
    val palette = LocalPalette.current
    var open by rememberSaveable(volume.name) { mutableStateOf(false) }
    Card(padding = PaddingValues(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { open = !open }.padding(start = 16.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(volume.name, style = MaterialTheme.typography.titleSmall)
                Text(if (volume.chapters.size == 1) "1 chapter" else "${volume.chapters.size} chapters", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            TextButton(onClick = onReadVolume, modifier = Modifier.semantics { contentDescription = "Read ${volume.name}" }) { Text("Read", color = palette.accent) }
        }
        if (open) {
            (if (newestFirst) volume.chapters.reversed() else volume.chapters).forEach { index ->
                Divider()
                ChapterRow(details, index, isRead(index), isCurrent(index), { onOpen(index) }, { onMarkRead(index) })
            }
        }
    }
}

/** One installed source: its popular and latest lists, its search, its filters, and its settings. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnlineSourceScreen(
    container: AppContainer, installed: InstalledSource, openTitle: (TitleRoute) -> Unit, openSettings: () -> Unit, onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var latest by rememberSaveable { mutableStateOf(false) }
    var canLatest by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<OnlineSeries>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var fault by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf("") }
    var generation by remember { mutableStateOf(0) }
    var filters by remember { mutableStateOf<List<SourceOption>>(emptyList()) }
    /** The values of the filters that the user applied. Empty means the list of the source with no filters. */
    var applied by remember { mutableStateOf<Map<String, OptionValue>>(emptyMap()) }
    var showFilters by remember { mutableStateOf(false) }
    var hasSettings by remember { mutableStateOf(false) }
    LaunchedEffect(installed.id) {
        val source = runCatching { container.sources.load(installed) }.getOrNull() ?: return@LaunchedEffect
        filters = runCatching { source.filterOptions() }.getOrDefault(emptyList())
        hasSettings = runCatching { source.hasSettingsForm() || source.settingOptions().isNotEmpty() }.getOrDefault(false)
    }

    fun loadPage(number: Int) {
        // A new search or list makes the answer of an older load old, so it must not go into the list.
        val mine = ++generation
        loading = true
        if (number == 1) items = emptyList()
        scope.launch {
            try {
                val source = container.sources.load(installed)
                canLatest = source.canShowLatest
                val result = when {
                    applied.isNotEmpty() -> source.browse(number, searching, applied)
                    searching.isNotEmpty() -> source.search(searching, number)
                    latest && source.canShowLatest -> source.latest(number)
                    else -> source.popular(number)
                }
                if (mine != generation) return@launch
                val known = items.map { it.id }.toSet()
                items = items + result.items.filter { it.id !in known }
                hasMore = result.hasMore && !result.items.all { it.id in known }
                page = number
                fault = null
            } catch (e: Exception) {
                if (mine == generation) fault = e.message ?: "The source did not answer. (${e.javaClass.simpleName})"
            } finally {
                if (mine == generation) loading = false
            }
        }
    }
    LaunchedEffect(latest, searching, applied) { loadPage(1) }
    // A passed browser check loads a list with a fault again.
    val passes by BrowserChecks.passes.collectAsState()
    LaunchedEffect(passes) { if (fault != null) loadPage(1) }

    SubScreen(installed.info.name, onBack, actions = {
        if (filters.isNotEmpty()) IconButton(onClick = { showFilters = true }) {
            Icon(if (applied.isEmpty()) Icons.Outlined.FilterList else Icons.Filled.FilterList, contentDescription = "Filters, " + if (applied.isEmpty()) "off" else "on", tint = palette.accent)
        }
        if (hasSettings) IconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Source settings") }
    }) {
        if (showFilters) {
            ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = palette.surface) {
                FilterSheet(filters, applied) { values -> applied = values; showFilters = false }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; if (it.isEmpty() && searching.isNotEmpty()) searching = "" }, singleLine = true,
                    placeholder = { Text("Search ${installed.info.name}") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searching = query.trim() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            if (searching.isEmpty() && applied.isEmpty() && canLatest) item(span = { GridItemSpan(maxLineSpan) }) {
                Chips(listOf("Popular", "Latest"), if (latest) 1 else 0, { latest = it == 1 })
            }
            fault?.let { text ->
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyArt("The source did not answer", text) { PillButton("Try again", { loadPage(1) }) } }
            }
            if (fault == null && items.isEmpty() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyArt("Nothing here", if (searching.isEmpty()) "The source gave no books." else "No results for \"$searching\".")
            }
            items(items, key = { it.id }) { series ->
                TitleTile(series.title, null, series.cover?.let { RemoteImage.cover(installed.id, it) }) {
                    openTitle(TitleRoute(listOf(SearchHit(installed.id, series)), findOthers = true))
                }
            }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette.accent) }
            } else if (hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton("Load more", { loadPage(page + 1) }) }
            }
        }
    }
}

/** The sources of one list, such as a repository of Mihon extensions, with a button to install or remove each one. */
@Composable
fun SourceListScreen(container: AppContainer, link: SourceLink, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val library by container.model.library.collectAsState()
    val prefs by container.settings.preferences.collectAsState()
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<SourceInfo>?>(null) }
    var fault by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val working = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(link.id) {
        try {
            sources = container.sources.sources(link)
        } catch (e: Exception) {
            fault = e.message ?: "The list did not load. (${e.javaClass.simpleName})"
        }
    }
    val shown = sources.orEmpty().filter { info ->
        (prefs.adult || !info.adult) && (filter.isBlank() || info.name.contains(filter, true) || info.language.contains(filter, true))
    }
    val languages = shown.map { it.language }.distinct().sortedWith(compareBy({ it != "en" }, { it }))

    SubScreen(link.name ?: link.kind?.label ?: "Sources", onBack) {
        LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Name or language") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                fault != null -> item { EmptyArt("The list did not load", fault!!) }
                sources == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette.accent) } }
            }
            for (language in languages) {
                item(key = "l:$language") {
                    Text(language.ifEmpty { "Other" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                }
                items(shown.filter { it.language == language }, key = { "s:" + it.id }) { info ->
                    val isInstalled = library.installed.any { it.list == link.id && it.info.id == info.id }
                    Card(padding = PaddingValues(0.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp)) {
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                                Text(info.name, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull("Version ${info.version}".takeIf { info.version.isNotEmpty() }, "18+".takeIf { info.adult }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            }
                            when {
                                working[info.id] == true -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.padding(12.dp).size(24.dp))
                                isInstalled -> TextButton(onClick = {
                                    library.installed.firstOrNull { it.list == link.id && it.info.id == info.id }?.let(container.sources::uninstall)
                                }) { Text("Remove", color = palette.danger) }
                                else -> TextButton(onClick = {
                                    working[info.id] = true
                                    scope.launch {
                                        try {
                                            container.sources.install(info, link)
                                        } catch (e: Exception) {
                                            container.model.fault.value = "${info.name} did not install: ${e.message}"
                                        }
                                        working.remove(info.id)
                                    }
                                }) { Text("Install", color = palette.accent) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row of an installed source, which opens its lists. */
@Composable
fun InstalledRow(source: InstalledSource, list: String? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.info.name, style = MaterialTheme.typography.bodyLarge)
            // Two lists can have a source with the same name, so the row names its list.
            Text(listOfNotNull(source.info.language, list).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
    }
}
