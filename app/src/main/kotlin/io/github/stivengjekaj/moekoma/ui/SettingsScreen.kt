package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.BuildConfig
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.core.ReadingMode
import io.github.stivengjekaj.moekoma.ui.theme.Appearance
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import io.github.stivengjekaj.moekoma.update.UpdateChecker
import io.github.stivengjekaj.moekoma.update.UpdateState

/** The look, the reader, and the facts about the app. */
@Composable
fun SettingsScreen(container: AppContainer, newer: String?) {
    val prefs by container.settings.preferences.collectAsState()
    val library by container.model.library.collectAsState()
    val update by container.installer.updates.collectAsState()
    val palette = LocalPalette.current
    val links = LocalUriHandler.current
    var fontLicense by remember { mutableStateOf(false) }
    var startingOver by remember { mutableStateOf(false) }
    val locked by container.model.libraryLocked.collectAsState()

    Column(
        Modifier.fillMaxSize().background(palette.background).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })

        if (locked) {
            Section("The library did not load", footer = "Moekoma does not save while the library file cannot be read, so the file stays as it is. Start a new, empty library. A copy of the old file stays next to it.") {
                LinkRow("Start a new library", bold = true, color = palette.danger) { startingOver = true }
            }
        }

        Section("Look", footer = if (prefs.appearance == Appearance.EInk) "The E-ink look is black on white, with no animation. The readers turn pages and do not scroll." else null) {
            Appearance.entries.forEach { option ->
                Choice(option.label, prefs.appearance == option) { container.settings.update { it.copy(appearance = option) } }
            }
            if (prefs.appearance == Appearance.EInk) {
                Divider()
                Text("Clear the screen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                listOf(0, 1, 3, 5, 10).forEach { every ->
                    val label = when (every) { 0 -> "Never"; 1 -> "At each page"; else -> "Every $every pages" }
                    Choice(label, prefs.refreshEvery == every) { container.settings.update { it.copy(refreshEvery = every) } }
                }
            }
        }

        Section("Reader", footer = "A book uses this mode until you choose a mode for it in the reader. A comic with \"YesAndRightToLeft\" in its ComicInfo.xml opens from the right to the left. With \"Keep the screen on\", the screen does not go dark while a book is open.") {
            ReadingMode.entries.forEach { option ->
                Choice(option.label, library.defaultMode == option) { container.model.setDefaultMode(option) }
            }
            Divider()
            Toggle("Keep the screen on", prefs.keepScreenOn) { on -> container.settings.update { it.copy(keepScreenOn = on) } }
        }

        Section("Keys", footer = "The volume keys turn the page while a book is open. The page keys of an e-ink reader always turn the page.") {
            Toggle("Turn pages with the volume keys", prefs.volumeKeys) { on -> container.settings.update { it.copy(volumeKeys = on) } }
            Divider()
            Toggle("Volume up goes forward", prefs.volumeReversed) { on -> container.settings.update { it.copy(volumeReversed = on) } }
        }

        Section("About", footer = "Moekoma keeps your books on this device. It has no account and no analytics. The app asks GitHub for the newest version.") {
            if (newer != null) {
                val text = when (val state = update) {
                    is UpdateState.Downloading -> "Downloading version $newer, ${state.percent}%"
                    UpdateState.Installing -> "Installing version $newer"
                    UpdateState.NeedsPermission -> "Allow Moekoma to install apps, then tap again"
                    is UpdateState.Failed -> "The update failed: ${state.reason}. Tap to try again"
                    UpdateState.Idle -> "Install version $newer"
                }
                LinkRow(text, bold = true, color = palette.accent) { container.installer.install() }
                Divider()
            }
            LinkRow("Support the project on Ko-fi") { links.openUri(UpdateChecker.KOFI) }
            Divider()
            LinkRow("Source code on GitHub") { links.openUri(UpdateChecker.REPOSITORY) }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp)) {
                Text("Version", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyLarge, color = palette.textSecondary)
            }
        }

        Section("Licenses") {
            Text(
                "The code is under the GNU General Public License, version 3. Karla, the font of the app, is under the SIL Open Font License, Version 1.1. The archives open with libarchive, under the BSD License, through the library of Hai Zhang, under the Apache License 2.0.",
                style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, modifier = Modifier.padding(16.dp),
            )
            Divider()
            LinkRow(if (fontLicense) "Hide the font license" else "Show the font license") { fontLicense = !fontLicense }
            if (fontLicense) {
                val context = LocalContext.current
                val text = remember { context.resources.openRawResource(R.raw.karla_ofl).bufferedReader().use { it.readText() } }
                SelectionContainer {
                    Text(text, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = palette.textSecondary, modifier = Modifier.padding(16.dp))
                }
            }
        }
        Spacer(Modifier.width(1.dp))
    }

    if (startingOver) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { startingOver = false },
            title = { Text("Start a new library?") },
            text = { Text("The new library is empty. The copy of the old file stays.") },
            confirmButton = { TextButton(onClick = { startingOver = false; container.model.startNewLibrary() }) { Text("Start a new library", color = palette.danger) } },
            dismissButton = { TextButton(onClick = { startingOver = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        Card(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { content() }
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun Divider() {
    val palette = LocalPalette.current
    HorizontalDivider(thickness = palette.outlineWidth, color = palette.outline, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = palette.accent, unselectedColor = palette.textSecondary))
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent,
                uncheckedTrackColor = palette.surfaceHigh, uncheckedThumbColor = palette.textSecondary, uncheckedBorderColor = palette.outline,
            ),
        )
    }
}

@Composable
private fun LinkRow(label: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        label,
        style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
        color = color ?: palette.textPrimary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
    )
}
