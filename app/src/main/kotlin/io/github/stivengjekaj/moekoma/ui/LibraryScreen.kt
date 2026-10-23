package io.github.stivengjekaj.moekoma.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.ui.theme.Corner
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.time.LocalTime

/** The kinds of file that the importer offers. Android does not know a type for each comic archive, so all files show. */
private val importTypes = arrayOf("*/*")

/** The shelf: the books that the user reads now, and every book in the library. */
@Composable
fun LibraryScreen(model: AppModel, onRead: (Book) -> Unit) {
    val library by model.library.collectAsState()
    val importing by model.importing.collectAsState()
    val palette = LocalPalette.current
    var deleting by remember { mutableStateOf<Book?>(null) }
    var importMenu by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.importDocuments(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(model::importFolder) }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                        Text(greeting(LocalTime.now().hour), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                        Text("Your library", style = MaterialTheme.typography.displaySmall)
                    }
                    Box {
                        IconButton(onClick = { importMenu = true }) { Icon(Icons.Outlined.Add, contentDescription = "Import") }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(text = { Text("Import files") }, onClick = { importMenu = false; pickFiles.launch(importTypes) })
                            DropdownMenuItem(text = { Text("Import a folder of images") }, onClick = { importMenu = false; pickFolder.launch(null) })
                        }
                    }
                }
            }
            if (library.books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyShelf { pickFiles.launch(importTypes) } }
            } else {
                val continuing = library.continueReading
                if (continuing.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Continue reading")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(continuing, key = { it.id }) { book ->
                                    BookMenu(book, model, onRead, onDelete = { deleting = it }) { ContinueCard(book, model) }
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("All books") }
                items(library.recentlyAdded, key = { it.id }) { book ->
                    BookMenu(book, model, onRead, onDelete = { deleting = it }) { BookTile(book, model) }
                }
            }
        }

        importing?.let { progress ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceHigh)
                    .border(palette.outlineWidth, palette.outline, CircleShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.accent)
                Text("Importing ${minOf(progress.done + 1, progress.total)} of ${progress.total}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    deleting?.let { book ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${book.title}?") },
            text = { Text("The book and its progress leave the library. A file that you imported stays where it is.") },
            confirmButton = { TextButton(onClick = { model.delete(book); deleting = null }) { Text("Delete", color = palette.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** "Asa" is the morning in Japanese, so the app says hello at each time of the day, as Asabunko does. */
fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "A quiet night for reading"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    val palette = LocalPalette.current
    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 36.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.mark), contentDescription = null,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(27.dp)),
            )
            Text("Your shelf is empty", style = MaterialTheme.typography.titleLarge)
            Text(
                "Import a comic archive, such as CBZ, CBR, CB7, or CBT, or a folder of images.",
                style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, textAlign = TextAlign.Center,
            )
            PillButton("Import a book", onImport, Modifier.padding(top = 4.dp))
        }
    }
}

/** A long press opens the menu of a book. A tap reads it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookMenu(book: Book, model: AppModel, onRead: (Book) -> Unit, onDelete: (Book) -> Unit, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.combinedClickable(onClickLabel = "Read", onLongClickLabel = "Open the menu", onLongClick = { open = true }) { onRead(book) }) {
        content()
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Read") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) }, onClick = { open = false; onRead(book) })
            if (book.finished || book.lastRead != null) {
                DropdownMenuItem(text = { Text("Mark as unread") }, leadingIcon = { Icon(Icons.Outlined.Undo, null) }, onClick = {
                    open = false; model.update(book.id) { it.markUnread() }
                })
            }
            if (!book.finished) {
                DropdownMenuItem(text = { Text("Mark as read") }, leadingIcon = { Icon(Icons.Outlined.Check, null) }, onClick = {
                    open = false; model.update(book.id) { it.markRead() }
                })
            }
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { open = false; onDelete(book) })
        }
    }
}

/** A large card for a book that the user reads now. */
@Composable
private fun ContinueCard(book: Book, model: AppModel) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(Corner)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .height(141.dp)
            .clip(shape)
            .background(palette.surface)
            .border(palette.outlineWidth, palette.outline, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        CoverView(model.cover(book), book.title, Modifier.width(78.dp))
        Column(Modifier.width(150.dp).height(117.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val unit = if (book.format == BookFormat.Comic || book.format == BookFormat.Pdf) "Page" else "Chapter"
            Text("$unit ${book.page + 1} of ${book.pageCount}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            Spacer(Modifier.weight(1f))
            ProgressLine(book.progress)
        }
    }
}

/** A cover with its title in the grid. */
@Composable
private fun BookTile(book: Book, model: AppModel) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Box {
            CoverView(model.cover(book), book.title, Modifier.fillMaxWidth())
            if (book.finished) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Finished", tint = palette.accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(palette.onAccent),
                )
            }
        }
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (book.lastRead != null && !book.finished) ProgressLine(book.progress)
    }
}
