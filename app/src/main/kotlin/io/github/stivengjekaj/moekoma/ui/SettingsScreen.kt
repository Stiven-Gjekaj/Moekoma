package io.github.stivengjekaj.moekoma.ui

import android.Manifest
import android.os.Build
import io.github.stivengjekaj.moekoma.data.ChapterChecks
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.github.stivengjekaj.moekoma.core.Backup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import java.util.UUID
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
fun SettingsScreen(
    container: AppContainer, newer: String?, stack: List<SettingsRoute>, setStack: (List<SettingsRoute>) -> Unit,
    read: (UUID) -> Unit, onRead: (RemoteBook) -> Unit,
) {
    val open: (SettingsRoute) -> Unit = { setStack(stack + it) }
    val back: () -> Unit = { setStack(stack.dropLast(1)) }
    when (val route = stack.lastOrNull()) {
        null -> SettingsRoot(container, newer, open)
        SettingsRoute.Sources -> SourcesHomeScreen(container, open, back)
        SettingsRoute.EditSources -> EditSourcesScreen(container, back)
        is SettingsRoute.Detect -> DetectScreen(container, route.source, { setStack(stack.dropLast(1) + it) }, back)
        is SettingsRoute.SourceList -> SourceListScreen(container, route.link, back)
        SettingsRoute.Installed -> InstalledSourcesScreen(container, open, back)
        is SettingsRoute.Source -> key(route) {
            OnlineSourceScreen(container, route.source, { open(SettingsRoute.Title(it)) }, { open(SettingsRoute.SourceSettings(route.source)) }, back)
        }
        is SettingsRoute.SourceSettings -> SourceSettingsScreen(container, route.source, open, back)
        is SettingsRoute.SourceForm -> key(route) { SourceFormScreen(container, route.source, route.form, route.title, open, back) }
        is SettingsRoute.Title -> key(stack.size, route) { OnlineSeriesScreen(container, route.route, read, back) }
        is SettingsRoute.Feed -> key(route) { OpdsFeedScreen(container, route.source, route.url, route.title, open, back) }
        is SettingsRoute.Publication -> PublicationScreen(container, route.source, route.publication, onRead, back)
    }
}

@Composable
private fun SettingsRoot(container: AppContainer, newer: String?, open: (SettingsRoute) -> Unit) {
    val prefs by container.settings.preferences.collectAsState()
    val library by container.model.library.collectAsState()
    val update by container.installer.updates.collectAsState()
    val palette = LocalPalette.current
    val links = LocalUriHandler.current
    var fontLicense by remember { mutableStateOf(false) }
    var startingOver by remember { mutableStateOf(false) }
    val locked by container.model.libraryLocked.collectAsState()
    val folders by container.folders.folders.collectAsState()
    val scan by container.folders.lastResult.collectAsState()
    val scanning by container.folders.busy.collectAsState()
    val scope = rememberCoroutineScope()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { container.folders.add(it) }
    }
    val context = LocalContext.current
    /** The message after a backup, a restore, or an import. */
    var report by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun work(title: String, body: suspend () -> String) {
        busy = true
        scope.launch {
            report = title to try { body() } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
            busy = false
        }
    }
    val writeBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        work("Backup") {
            val bytes = container.model.backup("Moekoma ${BuildConfig.VERSION_NAME}")
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw java.io.IOException("The file cannot be written.") }
            "The backup has ${library.books.size} book${if (library.books.size == 1) "" else "s"}, the source list with no passwords, and the installed sources."
        }
    }
    val readBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        work("Restore") {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: throw java.io.IOException("The file cannot be read.")
            val (plan, failed) = container.sources.restore(Backup.decode(bytes))
            listOfNotNull(
                "The library has ${plan.library.books.size} book${if (plan.library.books.size == 1) "" else "s"} now.",
                plan.skipped.takeIf { it.isNotEmpty() }?.let { "These books need their files again: ${it.joinToString(", ")}. Import them again." },
                failed.takeIf { it.isNotEmpty() }?.let { "These sources did not install: ${it.joinToString(", ")}. Save the source list again with a network." },
            ).joinToString("\n\n")
        }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) container.model.fault.value = "Turn on the notifications of Moekoma in the settings of Android to know about new chapters."
    }
    val readMihon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        work("Mihon backup") {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: throw java.io.IOException("The file cannot be read.")
            val result = container.sources.importMihon(bytes)
            listOfNotNull(
                "${result.added} series came into the library.",
                result.waiting.takeIf { it.isNotEmpty() }?.let { waiting ->
                    "These series come in when their source installs: " + waiting.entries.joinToString(", ") { "${it.key} (${it.value})" } + "."
                },
            ).joinToString("\n\n")
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.background).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })

        if (locked) {
            Section("The library did not load", footer = "Moekoma does not save while the library file cannot be read, so the file stays as it is. Start a new, empty library. A copy of the old file stays next to it.") {
                LinkRow("Restore a backup", bold = true, color = palette.accent) { readBackup.launch(arrayOf("application/json", "*/*")) }
                Divider()
                LinkRow("Start a new library", bold = true, color = palette.danger) { startingOver = true }
            }
        }

        Section("Sources", footer = "Mihon extensions, Kotatsu parsers, LNReader plugins, Aidoku sources, Paperback extensions, Suwayomi servers, and OPDS catalogs such as Komga, Kavita, and Calibre-web.") {
            LinkRow(if (library.sources.isEmpty()) "Add sources" else "${library.sources.size} source" + (if (library.sources.size == 1) "" else "s")) { open(SettingsRoute.Sources) }
        }

        Section(
            "New chapters",
            footer = "The books of your read list check for new chapters about every six hours, when the device has a network. " +
                "A notification tells you about new chapters.",
        ) {
            Toggle("Check in the background", prefs.backgroundChecks) { on ->
                container.settings.update { it.copy(backgroundChecks = on) }
                ChapterChecks.schedule(context, on)
                if (on && !ChapterChecks.mayNotify(context) && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        Section(
            "Backup",
            footer = "A backup is one file with your books, their places, the source list with no passwords, and the installed sources. " +
                "Asabunko restores it too. The files of the books are not in it. A Mihon backup brings its series, the chapters that you read, " +
                "its categories, and its repositories.",
        ) {
            LinkRow(if (busy) "Working" else "Back up the library", bold = true, color = palette.accent) { if (!busy) writeBackup.launch(Backup.fileName()) }
            Divider()
            LinkRow("Restore a backup") { if (!busy) readBackup.launch(arrayOf("application/json", "*/*")) }
            Divider()
            LinkRow("Import a Mihon backup") { if (!busy) readMihon.launch(arrayOf("*/*")) }
        }

        Section(
            "Folders",
            footer = "Moekoma imports each new book of these folders: EPUB, PDF, comic archives, Kindle books, text, and folders of images. " +
                "It looks when the app opens and two times a day. A book that you delete does not come back.",
        ) {
            folders.folders.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp)) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { container.folders.remove(folder) }) { Text("Remove", color = palette.danger) }
                }
                Divider()
            }
            LinkRow("Add a folder", bold = true, color = palette.accent) { pickFolder.launch(null) }
            if (folders.folders.isNotEmpty()) {
                Divider()
                val status = when {
                    scanning -> "Looking for new books"
                    scan == null -> "Look for new books now"
                    else -> scan!!.let { result ->
                        listOfNotNull(
                            if (result.added == 0) "No new books" else "${result.added} new book${if (result.added == 1) "" else "s"}",
                            result.failed.size.takeIf { it > 0 }?.let { "$it did not import" },
                            result.missing.takeIf { it.isNotEmpty() }?.let { "Moekoma cannot open ${it.joinToString(", ")}" },
                        ).joinToString(". ") + ". Look again"
                    }
                }
                LinkRow(status) { if (!scanning) scope.launch { container.folders.scan() } }
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
                "The code is under the GNU General Public License, version 3. Karla, the font of the app, is under the SIL Open Font License, Version 1.1. The archives open with libarchive, under the BSD License, through the library of Hai Zhang, under the Apache License 2.0. The source API for the Mihon extensions comes from Mihon, under the Apache License 2.0. Tsuki, the API of the Kotatsu parsers, is under the GPL 3. The JavaScript sources run in QuickJS, under the MIT License, through quickjs-kt, under the Apache License 2.0. The Aidoku sources run in Chicory, under the Apache License 2.0. OkHttp, Coil, RxJava, Injekt, and AndroidX are under the Apache License 2.0, and Jsoup is under the MIT License.",
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

    report?.let { (title, text) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { report = null }) { Text("OK") } },
        )
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
internal fun Section(title: String?, footer: String? = null, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        Card(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { content() }
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
internal fun Divider() {
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
internal fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun LinkRow(label: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        label,
        style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
        color = color ?: palette.textPrimary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
    )
}
