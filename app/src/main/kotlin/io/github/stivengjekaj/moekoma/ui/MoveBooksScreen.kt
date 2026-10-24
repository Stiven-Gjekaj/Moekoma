package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.NaturalOrder
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.TitleMatch
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Moves many online books to another source at one time, such as when a source stops working. The app finds the best
 * title in the new source for each book, and the user sees the matches before the move.
 */
@Composable
fun MoveBooksScreen(container: AppContainer, onBack: () -> Unit) {
    val library by container.model.library.collectAsState()
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(emptySet<UUID>()) }
    var target by remember { mutableStateOf<String?>(null) }
    var targetMenu by remember { mutableStateOf(false) }
    // The page of the title in the new source of each chosen book, null when the source does not have it, and the switch of the move.
    var matches by remember { mutableStateOf<Map<UUID, Pair<OnlineDetails?, Boolean>>?>(null) }
    var finding by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf<Int?>(null) }
    // The online books of the library, the read list first.
    val books = library.books.filter { it.origin != null }
        .sortedWith(compareBy<Book> { if (it.listed) 0 else 1 }.thenComparator { a, b -> NaturalOrder.compare(a.title, b.title) })
    val installed = library.installed.firstOrNull { it.id == target }
    val usable = books.mapNotNull { book -> matches?.get(book.id)?.takeIf { it.second }?.first?.let { book to it } }

    fun find() {
        val source = installed ?: return
        finding = true
        moved = null
        scope.launch {
            val loaded = runCatching { container.sources.load(source) }.getOrNull()
            if (loaded == null) {
                container.model.fault.value = "${source.info.name} did not load."
                finding = false
                return@launch
            }
            val found = mutableMapOf<UUID, Pair<OnlineDetails?, Boolean>>()
            for (book in books.filter { it.id in chosen }) {
                val best = runCatching { TitleMatch.best(loaded.search(book.title, 1).items, book.title) }.getOrNull()
                val details = best?.let { runCatching { loaded.details(it) }.getOrNull() }
                found[book.id] = details to (details != null)
            }
            matches = found
            finding = false
        }
    }

    fun move() {
        val source = installed ?: return
        val jobs = usable
        scope.launch {
            var count = 0
            for ((book, details) in jobs) {
                container.downloads.cancel(book.id)
                container.downloads.remove(book)
                runCatching { container.sources.move(book, source, details) }
                    .onSuccess { count++ }
                    .onFailure { container.model.fault.value = "${book.title} did not move: ${it.message}" }
            }
            moved = count
            matches = null
            chosen = emptySet()
        }
    }

    SubScreen("Move books", onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
        ) {
            moved?.let { Text(if (it == 1) "1 book moved." else "$it books moved.", style = MaterialTheme.typography.titleSmall, color = palette.accent) }
            Section("Books", footer = "Choose the books to move.") {
                if (books.isEmpty()) Text("Your library has no online books.", modifier = Modifier.padding(16.dp), color = palette.textSecondary)
                books.forEachIndexed { index, book ->
                    if (index > 0) Divider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = book.id in chosen, role = Role.Checkbox) { on ->
                            chosen = if (on) chosen + book.id else chosen - book.id
                            matches = null
                        }.padding(start = 16.dp, end = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(book.fileName, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                        Checkbox(checked = book.id in chosen, onCheckedChange = null)
                    }
                }
            }
            Section("To") {
                Box {
                    LinkRow(installed?.info?.name ?: "Choose a source") { targetMenu = true }
                    DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                        library.installed.sortedWith { a, b -> NaturalOrder.compare(a.info.name, b.info.name) }.forEach { source ->
                            DropdownMenuItem(text = { Text(source.info.name) }, onClick = { target = source.id; matches = null; targetMenu = false })
                        }
                    }
                }
                Divider()
                val ready = chosen.isNotEmpty() && installed != null && !finding
                LinkRow(if (finding) "Searching" else "Find the titles", bold = true, color = if (ready) palette.accent else palette.textSecondary) { if (ready) find() }
            }
            if (finding) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally), color = palette.accent)
            matches?.let { found ->
                Section("Matches", footer = "Each book keeps its place at the chapter with the same number. The downloads of the old source leave the device.") {
                    books.filter { it.id in found }.forEachIndexed { index, book ->
                        if (index > 0) Divider()
                        val (details, use) = found.getValue(book.id)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (details != null) {
                                    Text("${details.series.title} · ${details.chapters.size} chapters", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                } else {
                                    Text("Not found in this source", style = MaterialTheme.typography.bodySmall, color = palette.danger)
                                }
                            }
                            Switch(checked = use, enabled = details != null, onCheckedChange = { on -> matches = found + (book.id to (details to on)) })
                        }
                    }
                    Divider()
                    LinkRow(
                        if (usable.size == 1) "Move 1 book" else "Move ${usable.size} books", bold = true,
                        color = if (usable.isEmpty()) palette.textSecondary else palette.accent,
                    ) { if (usable.isNotEmpty()) move() }
                }
            }
        }
    }
}
