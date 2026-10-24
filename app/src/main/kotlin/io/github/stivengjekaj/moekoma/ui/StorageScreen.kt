package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.FolderSize
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The space of the downloads of each online book. The user removes the downloads of a book, the chapters that the
 * user read, or all downloads.
 */
@Composable
fun StorageScreen(container: AppContainer, onBack: () -> Unit) {
    val library by container.model.library.collectAsState()
    val downloadState by container.downloads.state.collectAsState()
    val palette = LocalPalette.current
    // The bytes and the number of chapters on the device of each online book with downloads.
    var sizes by remember { mutableStateOf<Map<UUID, Pair<Long, Int>>?>(null) }
    var clearing by remember { mutableStateOf(false) }
    // A removal changes the downloads, so the screen measures again.
    LaunchedEffect(downloadState.changes, library.books.size) {
        sizes = withContext(Dispatchers.IO) {
            library.books.filter { it.origin != null }.mapNotNull { book ->
                val count = container.downloads.downloaded(book).size
                if (count == 0) null else book.id to (FolderSize.bytes(File(container.model.folder(book), "chapters")) to count)
            }.toMap()
        }
    }
    val books = library.books.filter { sizes?.containsKey(it.id) == true }.sortedByDescending { sizes?.get(it.id)?.first ?: 0 }

    SubScreen("Storage", onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
        ) {
            Section(null, footer = "The books from your files are not here. They stay until you delete them.") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {}) {
                    Text("Downloads", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(sizes?.let { FolderSize.text(it.values.sumOf { size -> size.first }) } ?: "Measuring", color = palette.textSecondary)
                }
                Divider()
                LinkRow("Remove all downloads", color = if (books.isEmpty()) palette.textSecondary else palette.danger) { if (books.isNotEmpty()) clearing = true }
            }
            if (sizes != null && books.isEmpty()) {
                EmptyArt("No downloads", "The chapters that you download show here.")
            } else if (books.isNotEmpty()) {
                Section("Books") {
                    books.forEachIndexed { index, book ->
                        if (index > 0) Divider()
                        val (bytes, count) = sizes?.get(book.id) ?: (0L to 0)
                        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(FolderSize.text(bytes), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            }
                            Text(if (count == 1) "1 chapter on the device" else "$count chapters on the device", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (book.page > 0) TextButton(onClick = { container.downloads.remove(book, end = book.page) }) { Text("Remove the read chapters", color = palette.accent) }
                                TextButton(onClick = { container.downloads.cancel(book.id); container.downloads.remove(book) }) { Text("Remove all", color = palette.danger) }
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
            title = { Text("Remove all downloads?") },
            text = { Text("The books stay in your library. Their chapters download again when you read them.") },
            confirmButton = {
                TextButton(onClick = {
                    books.forEach { container.downloads.cancel(it.id); container.downloads.remove(it) }
                    clearing = false
                }) { Text("Remove all", color = palette.danger) }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } },
        )
    }
}
