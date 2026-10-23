package io.github.stivengjekaj.moekoma.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.SearchGroup
import io.github.stivengjekaj.moekoma.core.SearchResults
import io.github.stivengjekaj.moekoma.data.RemoteImage
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The state of the search. It lives as long as the tabs, so that Back from a title shows the same results. */
class SearchModel(private val container: AppContainer, private val scope: CoroutineScope) {
    var text by mutableStateOf("")
    /** The text of the last search. The search starts when the user presses Search, not while the user types. */
    var query by mutableStateOf("")
        private set
    /** The groups of the results. A new list each time a source answers. */
    var groups by mutableStateOf<List<SearchGroup>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var asked by mutableIntStateOf(0)
        private set
    var answered by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    /** Searches all installed sources. The results come as each source answers. */
    fun search() {
        val next = text.trim()
        job?.cancel()
        query = next
        groups = emptyList()
        if (next.isEmpty()) {
            searching = false
            return
        }
        val sources = container.model.library.value.installed
        asked = sources.size
        answered = 0
        searching = true
        RecentSearches.add(container.app, next)
        val results = SearchResults(next)
        job = scope.launch {
            container.sources.search(next, sources) { id, items ->
                if (query != next) return@search
                results.add(items, id)
                groups = results.sorted
                answered++
            }
            searching = false
        }
    }

    fun clear() {
        job?.cancel()
        text = ""
        query = ""
        groups = emptyList()
        searching = false
    }
}

/** The recent searches, newest first, on this device. */
object RecentSearches {
    private const val KEY = "recent"

    private fun prefs(context: Context) = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    fun all(context: Context): List<String> = prefs(context).getString(KEY, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun add(context: Context, query: String) {
        val list = listOf(query) + all(context).filter { !it.equals(query, ignoreCase = true) }
        prefs(context).edit().putString(KEY, list.take(12).joinToString("\n")).apply()
    }

    fun clear(context: Context) = prefs(context).edit().remove(KEY).apply()
}

/** Search in all sources at the same time, as in ZettaStream. A title that many sources have shows one time. */
@Composable
fun SearchScreen(container: AppContainer, model: SearchModel, openTitle: (TitleRoute) -> Unit, openSources: () -> Unit) {
    val palette = LocalPalette.current
    val library by container.model.library.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var recent by rememberSaveable { mutableStateOf(RecentSearches.all(container.app)) }
    val installed = library.installed

    fun run() {
        focus.clearFocus()
        keyboard?.hide()
        model.search()
        recent = RecentSearches.all(container.app)
    }

    fun sourceOf(id: String): InstalledSource? = installed.firstOrNull { it.id == id }
    // The groups of the chosen kind: all, comics, or novels.
    val shown = if (kind == 0) model.groups else model.groups.filter { group -> sourceOf(group.hits[0].source)?.info?.novels == (kind == 2) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().background(palette.background).statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) {
                Text("Search", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                OutlinedTextField(
                    value = model.text, onValueChange = { model.text = it }, singleLine = true,
                    placeholder = { Text("Search titles") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (model.text.isNotEmpty()) IconButton(onClick = { model.clear() }) { Icon(Icons.Outlined.Cancel, contentDescription = "Clear the search") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, unfocusedBorderColor = palette.outline, cursorColor = palette.accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search titles" },
                )
                if (model.searching) {
                    LinearProgressIndicator(
                        progress = { model.answered.toFloat() / maxOf(model.asked, 1) }, color = palette.accent, trackColor = palette.surfaceHigh,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Searching ${model.asked} sources" },
                    )
                }
            }
        }
        when {
            installed.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyArt("No sources yet", "Search asks your installed sources. Add a source list in Settings, Sources, and Moekoma installs its sources.") {
                    PillButton("Add sources", openSources)
                }
            }
            model.query.isEmpty() -> {
                if (recent.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recent.forEach { past ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clip(CircleShape).background(palette.surfaceHigh).clickable { model.text = past; run() }
                                        .heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Outlined.History, null, tint = palette.textSecondary, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                                    Text(past, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                            Text(
                                "Clear", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary,
                                modifier = Modifier.clip(CircleShape).clickable { RecentSearches.clear(container.app); recent = emptyList() }
                                    .heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                                    .semantics { contentDescription = "Clear the recent searches" },
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyArt(
                        "Find a manga or a novel",
                        "Type a title and press Search. Moekoma asks your ${installed.size} sources at the same time.",
                        covers = container.discover.covers,
                    )
                }
            }
            else -> {
                item(span = { GridItemSpan(maxLineSpan) }) { Chips(listOf("All", "Comics", "Novels"), kind, { kind = it }) }
                if (shown.isEmpty() && !model.searching) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyArt("Nothing found", "No source found \"${model.query}\".") }
                } else if (shown.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${shown.size} title" + (if (shown.size == 1) "" else "s") + " for \"${model.query}\"",
                            style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary,
                        )
                    }
                    items(shown, key = { it.key }) { group ->
                        val cover = group.cover?.let { hit -> hit.series.cover?.let { RemoteImage.cover(hit.source, it) } }
                        val detail = if (group.hits.size == 1) sourceOf(group.hits[0].source)?.info?.name ?: "Source" else "${group.hits.size} sources"
                        TitleTile(group.title, detail, cover, badge = group.hits.size) { openTitle(TitleRoute(group.hits, findOthers = false)) }
                    }
                }
            }
        }
    }
}
