package io.github.stivengjekaj.moekoma.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.BuildConfig
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.EInk
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.ui.theme.Appearance
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import io.github.stivengjekaj.moekoma.ui.theme.MoekomaTheme
import java.util.UUID

/** The tabs of the app, as in Asabunko: Library, Discover, Search, and Settings. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Outlined.CollectionsBookmark),
    Discover("Discover", Icons.Outlined.AutoAwesome),
    Search("Search", Icons.Outlined.Search),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
fun MoekomaRoot(container: AppContainer) {
    val prefs by container.settings.preferences.collectAsState()
    MoekomaTheme(prefs.appearance) {
        var tab by rememberSaveable { mutableStateOf(Tab.Library) }
        var reading by rememberSaveable { mutableStateOf<String?>(null) }
        var remote by remember { mutableStateOf<RemoteBook?>(null) }
        // The site of a browser check that the user opened.
        var checking by remember { mutableStateOf<String?>(null) }
        var remoteMode by remember { mutableStateOf(container.model.library.value.defaultMode) }
        val newer by produceState<String?>(null) { value = container.updates.newerVersion(BuildConfig.VERSION_NAME) }
        val fault by container.model.fault.collectAsState()
        val palette = LocalPalette.current

        val scope = rememberCoroutineScope()
        val search = remember { SearchModel(container, scope) }
        // The title pages that Discover and Search opened, the newest last.
        var discoverStack by remember { mutableStateOf(listOf<TitleRoute>()) }
        var searchStack by remember { mutableStateOf(listOf<TitleRoute>()) }
        var settingsStack by remember { mutableStateOf(listOf<SettingsRoute>()) }
        val read: (UUID) -> Unit = { reading = it.toString() }
        val openSources = { tab = Tab.Settings; settingsStack = listOf(SettingsRoute.Sources) }

        // The readers cover the tabs, so that each tab keeps its open screens.
        val book = reading?.let { id -> container.model.library.collectAsState().value.book(UUID.fromString(id)) }
        Box(Modifier.fillMaxSize()) {
            // A phone shows the tabs at the bottom. A tablet or an unfolded phone shows them at the side.
            // A screen reader does not read the tabs under an open book or a check.
            val hidden = if (book != null || remote != null || checking != null) Modifier.clearAndSetSemantics {} else Modifier
            BoxWithConstraints(Modifier.fillMaxSize().background(palette.background).then(hidden)) {
                val content: @Composable () -> Unit = {
                    when (tab) {
                        Tab.Library -> LibraryScreen(container.model, container.settings) { reading = it.id.toString() }
                        Tab.Discover -> TitleStack(container, discoverStack, { discoverStack = it }, read) {
                            DiscoverScreen(container, { discoverStack = discoverStack + it }, openSources)
                        }
                        Tab.Search -> TitleStack(container, searchStack, { searchStack = it }, read) {
                            SearchScreen(container, search, { searchStack = searchStack + it }, openSources)
                        }
                        Tab.Settings -> SettingsScreen(container, newer, settingsStack, { settingsStack = it }, read) { remote = it }
                    }
                }
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(containerColor = palette.surface) {
                            Tab.entries.forEach { item ->
                                NavigationRailItem(
                                    selected = tab == item, onClick = { tab = item },
                                    icon = { TabIcon(item, newer != null) }, label = { Text(item.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = palette.accent, selectedTextColor = palette.accent,
                                        indicatorColor = palette.surfaceHigh,
                                        unselectedIconColor = palette.textSecondary, unselectedTextColor = palette.textSecondary,
                                    ),
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) { content() }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content() }
                        NavigationBar(containerColor = palette.surface) {
                            Tab.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item, onClick = { tab = item },
                                    icon = { TabIcon(item, newer != null) }, label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = if (palette.isEInk) palette.onAccent else palette.accent,
                                        selectedTextColor = palette.accent,
                                        indicatorColor = if (palette.isEInk) palette.accent else palette.surfaceHigh,
                                        unselectedIconColor = palette.textSecondary, unselectedTextColor = palette.textSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            if (book != null) {
                when (book.format) {
                    BookFormat.Novel -> NovelReaderScreen(container.model, container.settings, book.id) { reading = null }
                    BookFormat.Series -> SeriesReaderScreen(container, book.id) { reading = null }
                    else -> ReaderScreen(container.model, book.id, prefs) { reading = null }
                }
            }
        }

        // A site that asks for a check that needs the user. The banner stays above the tabs.
        val check by BrowserChecks.pending.collectAsState()
        if (checking == null && book == null && remote == null) check?.let { url ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BrowserCheckBanner(url, { checking = url }, Modifier.navigationBarsPadding().padding(bottom = 88.dp))
            }
        }
        checking?.let { url ->
            BrowserCheckScreen(url) { passed ->
                if (passed) BrowserChecks.host(url)?.let(BrowserChecks::markPassed)
                checking = null
            }
        }

        // The reader of a remote book covers the tabs too.
        remote?.let { open ->
            PageReader(open.title, open.pages, 0, remoteMode, prefs, onPage = {}, onMode = { remoteMode = it }, onClose = { remote = null })
        }

        fault?.let { message ->
            AlertDialog(
                onDismissRequest = { container.model.fault.value = null },
                title = { Text("Moekoma") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { container.model.fault.value = null }) { Text("OK") } },
            )
        }

        // The first start on an e-ink reader proposes the E-ink look.
        if (!prefs.askedEInk && prefs.appearance != Appearance.EInk && EInk.isEInkDevice(Build.MANUFACTURER, Build.BRAND)) {
            AlertDialog(
                onDismissRequest = { container.settings.update { it.copy(askedEInk = true) } },
                title = { Text("Use the E-ink look?") },
                text = { Text("This device has an e-ink screen. The E-ink look is black on white, with no animation, and the readers turn pages. You can change it in Settings.") },
                confirmButton = { TextButton(onClick = { container.settings.update { it.copy(askedEInk = true, appearance = Appearance.EInk, refreshEvery = 5) } }) { Text("Use E-ink") } },
                dismissButton = { TextButton(onClick = { container.settings.update { it.copy(askedEInk = true) } }) { Text("Not now") } },
            )
        }
    }
}

/** A tab with its title pages over it. Back closes the last one. */
@Composable
private fun TitleStack(
    container: AppContainer, stack: List<TitleRoute>, setStack: (List<TitleRoute>) -> Unit, read: (UUID) -> Unit, root: @Composable () -> Unit,
) {
    val top = stack.lastOrNull()
    if (top == null) root() else androidx.compose.runtime.key(stack.size, top) {
        OnlineSeriesScreen(container, top, read) { setStack(stack.dropLast(1)) }
    }
}

@Composable
private fun TabIcon(tab: Tab, update: Boolean) {
    if (tab == Tab.Settings && update) {
        BadgedBox(badge = { Badge(containerColor = LocalPalette.current.accent) }) { Icon(tab.icon, contentDescription = null) }
    } else {
        Icon(tab.icon, contentDescription = null)
    }
}
