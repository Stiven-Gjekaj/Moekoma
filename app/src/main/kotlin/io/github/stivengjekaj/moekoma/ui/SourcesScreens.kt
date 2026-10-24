package io.github.stivengjekaj.moekoma.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.NotAFeedException
import io.github.stivengjekaj.moekoma.core.Opds
import io.github.stivengjekaj.moekoma.core.OpdsFeed
import io.github.stivengjekaj.moekoma.core.OpdsLink
import io.github.stivengjekaj.moekoma.core.OpdsPublication
import io.github.stivengjekaj.moekoma.core.OpdsSearch
import io.github.stivengjekaj.moekoma.core.SourceKind
import io.github.stivengjekaj.moekoma.core.SourceLink
import io.github.stivengjekaj.moekoma.core.SourceList
import io.github.stivengjekaj.moekoma.ui.theme.Corner
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/** A screen of Settings below the root. The screens of the sources open as a stack. */
sealed interface SettingsRoute {
    data object Sources : SettingsRoute
    data object EditSources : SettingsRoute
    data class Feed(val source: SourceLink, val url: String, val title: String) : SettingsRoute
    data class Publication(val source: SourceLink, val publication: OpdsPublication) : SettingsRoute
    data class Detect(val source: SourceLink) : SettingsRoute
    data class SourceList(val link: SourceLink) : SettingsRoute
    data object Installed : SettingsRoute
    data class SourceSettings(val source: InstalledSource) : SettingsRoute
    /** A form of the settings of a source, such as a page of the settings of an Aidoku source. */
    data class SourceForm(val source: InstalledSource, val form: String, val title: String) : SettingsRoute
    data class Source(val source: InstalledSource) : SettingsRoute
    data class Title(val route: TitleRoute) : SettingsRoute
    data object Storage : SettingsRoute
    data object MoveBooks : SettingsRoute
}

/** The pages of a book that Moekoma reads from the network and does not keep. */
data class RemoteBook(val title: String, val pages: List<RemotePage>)

/** The bar of a screen below the root, with its title and a back button. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
            actions()
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

private val SourceKind?.icon: ImageVector
    get() = if (this == null) Icons.Outlined.HelpOutline else Icons.Outlined.Dns

/** The sources of the user, in Settings: the source list and the lists by kind. */
@Composable
fun SourcesHomeScreen(container: AppContainer, open: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    val library by container.model.library.collectAsState()
    val prefs by container.settings.preferences.collectAsState()
    val setup by container.sources.setup.collectAsState()
    val palette = LocalPalette.current
    SubScreen("Sources", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Section(
                null,
                footer = "Paste the URLs of your catalogs and source lists. Moekoma installs the English sources of each list, and Search and Discover ask them. Moekoma runs Mihon extensions, Kotatsu parsers, LNReader plugins, Aidoku sources, Paperback extensions, and the sources of a Suwayomi server, and reads OPDS catalogs.",
            ) {
                LinkRow(if (library.sources.isEmpty()) "Add sources" else "Edit the source list", bold = true, color = palette.accent) { open(SettingsRoute.EditSources) }
            }
            setup?.let { (done, total) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Installing sources: $done of $total", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { done.toFloat() / maxOf(total, 1) }, color = palette.accent, trackColor = palette.surfaceHigh, modifier = Modifier.fillMaxWidth())
                }
            }
            if (library.installed.isNotEmpty()) {
                Section(null, footer = "Open a source to see its popular titles and to search it alone.") {
                    LinkRow("Installed sources (${library.installed.size})") { open(SettingsRoute.Installed) }
                }
            }
            val kinds: List<SourceKind?> = SourceKind.entries + listOf(null)
            for (kind in kinds) {
                val links = library.sources.filter { it.kind == kind }
                if (links.isEmpty()) continue
                Section(kind?.label ?: "Unknown kind") {
                    links.forEachIndexed { index, link ->
                        if (index > 0) Divider()
                        SourceRow(link) {
                            open(
                                when (link.kind) {
                                    SourceKind.Opds -> SettingsRoute.Feed(link, link.url, link.name ?: link.host ?: "Catalog")
                                    SourceKind.Mihon, SourceKind.Kotatsu, SourceKind.LnReader, SourceKind.Suwayomi, SourceKind.Aidoku, SourceKind.Paperback -> SettingsRoute.SourceList(link)
                                    else -> SettingsRoute.Detect(link)
                                },
                            )
                        }
                    }
                }
            }
            Section(null, footer = "A list marks some sources for adults. Moekoma does not install them and hides them until you turn this on.") {
                Toggle("Show sources for adults", prefs.adult) { on -> container.settings.update { it.copy(adult = on) } }
            }
        }
    }
}

/** One source, with its name and only the host of its URL. */
@Composable
fun SourceRow(link: SourceLink, onClick: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(link.kind.icon, contentDescription = null, tint = palette.accent)
        Column(Modifier.weight(1f)) {
            Text(link.name ?: link.host ?: "Source", style = MaterialTheme.typography.titleSmall)
            Text(
                (link.username?.let { "$it at " } ?: "") + link.shortAddress,
                style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
    }
}

/** The source list as text, one URL on each line, as in ZettaStream. Save replaces the full list. */
@Composable
fun EditSourcesScreen(container: AppContainer, onBack: () -> Unit) {
    val library by container.model.library.collectAsState()
    val palette = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf(SourceList.format(library.sources)) }
    var saved by remember { mutableStateOf<String?>(null) }
    SubScreen("Sources", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paste one URL on each line. Moekoma finds the kind of each source.", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Corner)).background(palette.surfaceHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "A repository of Mihon extensions, such as Keiyoushi" to "https://.../index.min.json or repo.json",
                    "A plugin of the Kotatsu parsers, such as Gekkoushi" to "https://github.com/Gekkoushi/plugin",
                    "An OPDS catalog: Komga, Kavita, Calibre-web" to "https://.../opds/v1.2/catalog",
                    "LNReader plugins, for web novels" to "https://.../plugins.min.json",
                    "Aidoku sources" to "https://.../index.min.json",
                    "Paperback 0.9 extensions" to "https://.../versioning.json",
                    "A Suwayomi server, for Mihon extensions" to "http://...:4567",
                ).forEach { (name, example) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(example, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = palette.textSecondary)
                    }
                }
            }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = palette.textPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, unfocusedBorderColor = palette.outline, cursorColor = palette.accent),
                modifier = Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = "Source list" },
            )
            Text(
                "Save keeps the list. The new list replaces the old list. To remove a list and its sources, remove its line. " +
                    "For a server with an account, write https://user:password@host/... The password goes to the encrypted store of the device, and the list shows only the user name.",
                style = MaterialTheme.typography.bodySmall, color = palette.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save", {
                    val list = container.sources.save(draft)
                    draft = SourceList.format(list)
                    saved = "Saved ${list.size} source" + if (list.size == 1) "." else "s."
                })
                TextButton(onClick = { clipboard.getText()?.text?.let { draft = it } }) { Text("Paste", color = palette.accent) }
            }
            saved?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.accent) }
            Text("Current list (${library.sources.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            library.sources.forEach { link -> Card(padding = PaddingValues(0.dp)) { SourceRow(link) } }
        }
    }
}

/** Reads a source with no known kind and finds its kind from what it gives. */
@Composable
fun DetectScreen(container: AppContainer, source: SourceLink, open: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    var fault by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(source.id) {
        try {
            val kind = SourceKind.detect(container.model.fetch(source.url, source))
            if (kind == null) {
                fault = "The URL does not give an OPDS catalog or a source list that Moekoma knows."
            } else {
                container.model.learn(source.id, kind, null)
                if (kind == SourceKind.Opds) open(SettingsRoute.Feed(source.copy(kind = kind), source.url, source.host ?: "Catalog"))
                else fault = "This is a list of ${kind.label}. Moekoma reads it in a next version."
            }
        } catch (e: Exception) {
            fault = e.message ?: "The source did not answer. (${e.javaClass.simpleName})"
        }
    }
    SubScreen(source.name ?: source.host ?: "Source", onBack) {
        val text = fault
        if (text == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LocalPalette.current.accent) }
        else EmptyArt("This source did not open", text)
    }
}

/** One page of an OPDS catalog. */
@Composable
fun OpdsFeedScreen(container: AppContainer, source: SourceLink, url: String, title: String, open: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<OpdsFeed?>(null) }
    var results by remember { mutableStateOf<OpdsFeed?>(null) }
    var fault by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    suspend fun fetch(address: String) = Opds.parse(container.model.fetch(address, source), address)

    val passes by BrowserChecks.passes.collectAsState()
    LaunchedEffect(url, passes) {
        try {
            feed = fetch(url)
            fault = null
            if (url == source.url) container.model.learn(source.id, SourceKind.Opds, feed?.title)
        } catch (e: NotAFeedException) {
            fault = e.message
        } catch (e: Exception) {
            fault = e.message ?: "The catalog did not answer. (${e.javaClass.simpleName})"
        }
    }

    fun search() {
        val search = feed?.search ?: return
        if (query.isBlank()) return
        scope.launch {
            val template = when (search) {
                is OpdsSearch.Template -> search.text
                is OpdsSearch.Description -> runCatching { Opds.openSearchTemplate(container.model.fetch(search.url, source)) }.getOrNull()
            } ?: return@launch
            results = runCatching { fetch(Opds.searchUrl(template, query)) }.getOrNull()
        }
    }

    fun loadMore() {
        val next = (results ?: feed)?.next ?: return
        loadingMore = true
        scope.launch {
            val page = runCatching { fetch(next) }.getOrNull()
            loadingMore = false
            page ?: return@launch
            if (results != null) results = results!!.copy(publications = results!!.publications + page.publications, next = page.next)
            else feed = feed!!.copy(publications = feed!!.publications + page.publications, navigation = feed!!.navigation + page.navigation, next = page.next)
        }
    }

    SubScreen(if (results == null) feed?.title ?: title else "Results", onBack) {
        val shown = results ?: feed
        when {
            fault != null -> EmptyArt("The catalog did not open", fault!!)
            shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette.accent) }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (feed?.search != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it; if (it.isEmpty()) results = null }, singleLine = true,
                            placeholder = { Text("Search the catalog") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                if (shown.navigation.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { LinkList(shown.navigation) { open(SettingsRoute.Feed(source, it.url, it.title)) } }
                for (group in shown.groups) {
                    if (group.title.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(group.title, style = MaterialTheme.typography.titleLarge) }
                    if (group.navigation.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { LinkList(group.navigation) { open(SettingsRoute.Feed(source, it.url, it.title)) } }
                    items(group.publications) { book -> PublicationTile(container, source, book) { open(SettingsRoute.Publication(source, book)) } }
                }
                items(shown.publications) { book -> PublicationTile(container, source, book) { open(SettingsRoute.Publication(source, book)) } }
                if (shown.navigation.isEmpty() && shown.publications.isEmpty() && shown.groups.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyArt("Nothing here", "This page of the catalog is empty.") }
                }
                if (shown.next != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PillButton(if (loadingMore) "Loading" else "Load more", ::loadMore, enabled = !loadingMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkList(links: List<OpdsLink>, onOpen: (OpdsLink) -> Unit) {
    val palette = LocalPalette.current
    Card(padding = PaddingValues(0.dp)) {
        links.forEachIndexed { index, link ->
            if (index > 0) Divider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onOpen(link) }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(link.title, style = MaterialTheme.typography.bodyLarge)
                    link.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
            }
        }
    }
}

/** A cover from the network, with the headers of its source. */
@Composable
fun RemoteCover(url: String?, headers: Map<String, String>, title: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(Corner - 4.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(shape).background(palette.surfaceHigh).border(palette.outlineWidth, palette.outline, shape), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, textAlign = TextAlign.Center, maxLines = 5, modifier = Modifier.padding(10.dp))
        if (url != null) {
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build()).build()
            }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun PublicationTile(container: AppContainer, source: SourceLink, book: OpdsPublication, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable(onClick = onOpen).semantics(mergeDescendants = true) {}) {
        RemoteCover(book.image, book.image?.let { container.model.headers(it, source) } ?: emptyMap(), book.title, Modifier.fillMaxWidth())
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** One book of a catalog, with a button to read it now and a button to download it. */
@Composable
fun PublicationScreen(container: AppContainer, source: SourceLink, book: OpdsPublication, onRead: (RemoteBook) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(false) }

    val stream = book.stream
    SubScreen("", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RemoteCover(book.image, book.image?.let { container.model.headers(it, source) } ?: emptyMap(), book.title, Modifier.width(120.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge)
                    book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary) }
                    if (stream != null && stream.count > 0) Text("${stream.count} pages", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stream != null) PillButton("Read now", { onRead(RemoteBook(book.title, List(stream.count) { RemotePage(stream.url(it), container.model.headers(stream.url(it), source)) })) })
                book.download?.let { (url, ext) ->
                    PillButton(
                        if (downloaded) "In the library" else if (downloading) "Downloading" else "Download",
                        {
                            downloading = true
                            scope.launch {
                                downloaded = container.model.download(url, source, book.title, ext)
                                downloading = false
                            }
                        },
                        enabled = !downloading && !downloaded,
                    )
                }
            }
            if (stream == null && book.download == null) Text("Moekoma cannot read the files of this book.", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            book.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.size(1.dp))
        }
    }
}

/** The installed sources, with a filter by name or language. */
@Composable
fun InstalledSourcesScreen(container: AppContainer, open: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val library by container.model.library.collectAsState()
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val shown = library.installed.filter { filter.isBlank() || it.info.name.contains(filter, true) || it.info.language.contains(filter, true) }
        .sortedBy { it.info.name.lowercase() }
    SubScreen("Installed sources", onBack) {
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Name or language") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(shown.size, key = { shown[it].id }) { index -> val source = shown[index]
                val link = library.sources.firstOrNull { it.id == source.list }
                Card(padding = PaddingValues(0.dp)) { InstalledRow(source, link?.name ?: link?.kind?.label) { open(SettingsRoute.Source(source)) } }
            }
        }
    }
}
