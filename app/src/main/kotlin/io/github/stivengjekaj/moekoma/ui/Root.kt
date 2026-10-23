package io.github.stivengjekaj.moekoma.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.BuildConfig
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.EInk
import io.github.stivengjekaj.moekoma.ui.theme.Appearance
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import io.github.stivengjekaj.moekoma.ui.theme.MoekomaTheme
import java.util.UUID

/** The tabs of the app. Browse comes with the sources. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Outlined.CollectionsBookmark),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
fun MoekomaRoot(container: AppContainer) {
    val prefs by container.settings.preferences.collectAsState()
    MoekomaTheme(prefs.appearance) {
        var tab by rememberSaveable { mutableStateOf(Tab.Library) }
        var reading by rememberSaveable { mutableStateOf<String?>(null) }
        val newer by produceState<String?>(null) { value = container.updates.newerVersion(BuildConfig.VERSION_NAME) }
        val fault by container.model.fault.collectAsState()
        val palette = LocalPalette.current

        val book = reading?.let { id -> container.model.library.collectAsState().value.book(UUID.fromString(id)) }
        if (book != null) {
            when (book.format) {
                BookFormat.Novel -> NovelReaderScreen(container.model, container.settings, book.id) { reading = null }
                else -> ReaderScreen(container.model, book.id, prefs) { reading = null }
            }
        } else {
            // A phone shows the tabs at the bottom. A tablet or an unfolded phone shows them at the side.
            BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
                val content: @Composable () -> Unit = {
                    when (tab) {
                        Tab.Library -> LibraryScreen(container.model, container.settings) { reading = it.id.toString() }
                        Tab.Settings -> SettingsScreen(container, newer)
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

@Composable
private fun TabIcon(tab: Tab, update: Boolean) {
    if (tab == Tab.Settings && update) {
        BadgedBox(badge = { Badge(containerColor = LocalPalette.current.accent) }) { Icon(tab.icon, contentDescription = null) }
    } else {
        Icon(tab.icon, contentDescription = null)
    }
}
