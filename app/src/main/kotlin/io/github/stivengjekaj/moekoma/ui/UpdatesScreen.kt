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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.stivengjekaj.moekoma.core.ChapterUpdate
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Downloads
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The new chapters that the checks found, the newest first, by the day of the check. A tap reads the chapter. */
@Composable
fun UpdatesScreen(model: AppModel, downloads: Downloads, onRead: (Book) -> Unit, onBack: () -> Unit) {
    val library by model.library.collectAsState()
    val palette = LocalPalette.current
    val updates = library.shownUpdates
    val days = updates.groupBy { it.found.atZone(ZoneId.systemDefault()).toLocalDate() }.toList()
    // The chapters of each book on the device, read one time for each book.
    var downloaded by remember { mutableStateOf(emptyMap<UUID, Set<Int>>()) }
    val downloadState by downloads.state.collectAsState()
    LaunchedEffect(updates.map { it.book }.toSet(), downloadState.changes) {
        downloaded = withContext(Dispatchers.IO) {
            updates.map { it.book }.toSet().mapNotNull { id -> library.book(id)?.let { id to downloads.downloaded(it) } }.toMap()
        }
    }

    SubScreen("Updates", onBack, actions = {
        if (updates.isNotEmpty()) TextButton(onClick = { model.change { it.copy(updates = emptyList()) } }) { Text("Clear", color = palette.accent) }
    }) {
        if (updates.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(Gutter)) {
                EmptyArt("No updates", "New chapters of your books show here after a check.")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                days.forEach { (day, group) ->
                    item(key = day.toString()) {
                        Text(dayName(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() })
                    }
                    items(group, key = { it.id }) { update ->
                        library.book(update.book)?.let { book ->
                            UpdateRow(update, book, model, update.chapter in downloaded[book.id].orEmpty()) {
                                // The reader opens at the new chapter.
                                model.update(book.id) { it.copy(page = update.chapter, position = 0.0) }
                                onRead(book)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(update: ChapterUpdate, book: Book, model: AppModel, downloaded: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = "Read", onClick = onClick).padding(vertical = 6.dp),
    ) {
        CoverView(model.cover(book), book.title, Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(update.title ?: "Chapter ${update.chapter + 1}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (downloaded) Icon(Icons.Filled.CheckCircle, contentDescription = "Downloaded", tint = palette.accent)
    }
}

/** The day of a check or a visit, such as "Saturday, October 24". */
internal fun dayName(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
