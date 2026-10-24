package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.HistoryEntry
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The chapters and the books that the user opened, the newest first, by day. A tap reads the chapter again. */
@Composable
fun HistoryScreen(model: AppModel, onRead: (Book) -> Unit, onBack: () -> Unit) {
    val library by model.library.collectAsState()
    val palette = LocalPalette.current
    val entries = library.shownHistory
    val zone = ZoneId.systemDefault()
    val days = entries.groupBy { it.date.atZone(zone).toLocalDate() }.toList()
    var clearing by remember { mutableStateOf(false) }
    // The titles of the chapters of each book, read one time for each book.
    var chapters by remember { mutableStateOf(emptyMap<UUID, List<String?>>()) }
    LaunchedEffect(entries.map { it.book }.toSet()) {
        chapters = withContext(Dispatchers.IO) {
            entries.map { it.book }.toSet().mapNotNull { id ->
                val book = library.book(id) ?: return@mapNotNull null
                if (book.format != BookFormat.Novel && book.format != BookFormat.Series) return@mapNotNull null
                runCatching { id to Novel.load(model.folder(book)).chapters.map { it.title } }.getOrNull()
            }.toMap()
        }
    }

    SubScreen("History", onBack, actions = {
        if (library.history.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text("Clear", color = palette.accent) }
    }) {
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(Gutter)) {
                EmptyArt("No history", "The chapters that you open show here.")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                days.forEach { (day, group) ->
                    item(key = day.toString()) {
                        Text(dayName(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() })
                    }
                    items(group, key = { it.id }) { entry ->
                        library.book(entry.book)?.let { book ->
                            val chapter = if (book.format == BookFormat.Novel || book.format == BookFormat.Series) {
                                chapters[book.id]?.getOrNull(entry.place) ?: "Chapter ${entry.place + 1}"
                            } else null
                            HistoryRow(entry, book, chapter, zone, onRemove = { model.change { it.copy(history = it.history.filterNot { e -> e.id == entry.id }) } }) {
                                if (chapter != null) model.update(book.id) { it.copy(page = entry.place, position = 0.0) }
                                onRead(book)
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Remove all the history?") },
            text = { Text("Your books and the place in each book stay.") },
            confirmButton = { TextButton(onClick = { model.change { it.copy(history = emptyList()) }; clearing = false }) { Text("Remove all", color = palette.danger) } },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, book: Book, chapter: String?, zone: ZoneId, onRemove: () -> Unit, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Read", onClick = onClick).padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chapter != null) Text(chapter, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(entry.date.atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = tr("Remove from the history"), tint = palette.textSecondary) }
    }
}
