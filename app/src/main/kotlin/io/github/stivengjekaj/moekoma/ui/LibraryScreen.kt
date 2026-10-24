package io.github.stivengjekaj.moekoma.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookCategory
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.ReadListOrder
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Downloads
import io.github.stivengjekaj.moekoma.data.Settings
import io.github.stivengjekaj.moekoma.data.Sources
import io.github.stivengjekaj.moekoma.ui.theme.Corner
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The kinds of file that the importer offers. Android does not know a type for each comic archive, so all files show. */
private val importTypes = arrayOf("*/*")

/** The shelf: the books that the user reads now, and the read list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(model: AppModel, settings: Settings, sources: Sources, downloads: Downloads, onRead: (Book) -> Unit) {
    val library by model.library.collectAsState()
    val prefs by settings.preferences.collectAsState()
    var orderMenu by remember { mutableStateOf(false) }
    val importing by model.importing.collectAsState()
    val palette = LocalPalette.current
    var deleting by remember { mutableStateOf<Book?>(null) }
    var importMenu by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    // A pull shows its own circle, so the button shows one only when the button starts the check.
    var pulled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasOnline = library.books.any { it.origin != null }
    fun check(quiet: Boolean) {
        if (checking) return
        checking = true
        pulled = quiet
        scope.launch {
            val result = sources.refreshAll()
            checking = false
            result.message(quiet)?.let { model.fault.value = it }
        }
    }

    var showingUpdates by rememberSaveable { mutableStateOf(false) }
    if (showingUpdates) {
        UpdatesScreen(model, downloads, onRead) { showingUpdates = false }
        return
    }
    var showingHistory by rememberSaveable { mutableStateOf(false) }
    if (showingHistory) {
        HistoryScreen(model, onRead) { showingHistory = false }
        return
    }
    var editingCategories by rememberSaveable { mutableStateOf(false) }
    if (editingCategories) {
        CategoriesScreen(model) { editingCategories = false }
        return
    }
    // The search, the filters, and the category of the read list.
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(emptySet<LibraryFilter>()) }
    var filterMenu by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<UUID?>(null) }
    // The online books with a chapter on the device, for the filter "On the device".
    var onDevice by remember { mutableStateOf(emptySet<UUID>()) }
    val downloadState by downloads.state.collectAsState()
    LaunchedEffect(LibraryFilter.OnDevice in filters, downloadState.changes) {
        if (LibraryFilter.OnDevice in filters) {
            onDevice = withContext(Dispatchers.IO) { library.books.filter { downloads.hasDownloads(it) }.map { it.id }.toSet() }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.importDocuments(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(model::importFolder) }

    // A pull asks the sources for new chapters, as in Asabunko. A pull with no news says nothing.
    PullToRefreshBox(
        isRefreshing = checking && pulled,
        onRefresh = { if (hasOnline) check(quiet = true) },
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
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
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, contentDescription = if (searching) "Close the search" else "Search the library")
                    }
                    if (hasOnline) {
                        IconButton(onClick = { check(quiet = false) }, enabled = !checking) {
                            if (checking && !pulled) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.accent)
                            else Icon(Icons.Outlined.Refresh, contentDescription = "Check for new chapters")
                        }
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
            if (searching) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search the library") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // The new chapters of the checks, in one list.
            val updates = library.shownUpdates.size
            if (updates > 0 && query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(Modifier.clickable(onClickLabel = "Open") { showingUpdates = true }.semantics(mergeDescendants = true) {}) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.NewReleases, contentDescription = null, tint = palette.accent)
                            Column(Modifier.weight(1f)) {
                                Text("Updates", style = MaterialTheme.typography.titleSmall)
                                Text(if (updates == 1) "1 new chapter" else "$updates new chapters", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
                        }
                    }
                }
            }
            // The chapters that the user opened, the newest first.
            if (library.shownHistory.isNotEmpty() && query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(Modifier.clickable(onClickLabel = "Open") { showingHistory = true }.semantics(mergeDescendants = true) {}) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = palette.accent)
                            Text("History", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
                        }
                    }
                }
            }
            val continuing = if (query.isBlank() && filters.isEmpty()) library.continueReading else emptyList()
            val wholeList = library.readList(prefs.readListOrder)
            val words = query.trim()
            // The books of the read list in the category, with the search and the filters.
            val readList = library.readList(category, prefs.readListOrder).filter { book ->
                (words.isEmpty() || book.title.contains(words, ignoreCase = true) || book.author.orEmpty().contains(words, ignoreCase = true)) &&
                    filters.all { it.keeps(book, onDevice) }
            }
            if (library.continueReading.isEmpty() && wholeList.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(padding = PaddingValues(0.dp)) {
                        EmptyArt(
                            title = "Your shelf is empty",
                            text = "Import an EPUB, a PDF, a comic archive such as CBZ or CBR, a TXT, FB2, MOBI, or AZW3 file, an HTML page, or a folder of images. Search and Discover come with the sources.",
                        ) { PillButton("Import a book", { pickFiles.launch(importTypes) }) }
                    }
                }
            } else {
                if (continuing.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Continue reading")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(continuing, key = { it.id }) { book ->
                                    BookMenu(book, model, downloads, onRead, onDelete = { deleting = it }) { ContinueCard(book, model) }
                                }
                            }
                        }
                    }
                }
                if (wholeList.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyArt(
                            title = "Your read list is empty",
                            text = "Tap the bookmark on the page of a title to keep it here. A book that you import goes here too.",
                        )
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionTitle("Read list") }
                            Box {
                                IconButton(
                                    onClick = { filterMenu = true },
                                    modifier = Modifier.semantics { stateDescription = if (filters.isEmpty()) "Off" else filters.joinToString(", ") { it.label } },
                                ) {
                                    Icon(Icons.Outlined.FilterList, contentDescription = "Filter the read list", tint = if (filters.isEmpty()) palette.textSecondary else palette.accent)
                                }
                                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                                    LibraryFilter.entries.forEach { filter ->
                                        DropdownMenuItem(
                                            text = { Text(filter.label) },
                                            trailingIcon = { if (filter in filters) Icon(Icons.Outlined.Check, contentDescription = "On") },
                                            onClick = { filters = if (filter in filters) filters - filter else filters + filter },
                                        )
                                    }
                                }
                            }
                            Box {
                                TextButton(
                                    onClick = { orderMenu = true },
                                    modifier = Modifier.semantics { contentDescription = "Order of the read list, ${prefs.readListOrder.label}" },
                                ) {
                                    Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.accent)
                                    Text(prefs.readListOrder.label, style = MaterialTheme.typography.labelLarge, color = palette.accent, modifier = Modifier.padding(start = 4.dp))
                                }
                                DropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                                    ReadListOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            trailingIcon = { if (order == prefs.readListOrder) Icon(Icons.Outlined.Check, contentDescription = "Chosen") },
                                            onClick = { orderMenu = false; settings.update { it.copy(readListOrder = order) } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryChips(library.categories, category, { category = it }) { editingCategories = true }
                    }
                    if (readList.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (words.isEmpty()) "No book of the read list fits the filters." else "No book of the read list has \"$words\" in its title.",
                                style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary,
                            )
                        }
                    }
                    items(readList, key = { it.id }) { book ->
                        BookMenu(book, model, downloads, onRead, onDelete = { deleting = it }) { BookTile(book, model) }
                    }
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
            confirmButton = { TextButton(onClick = { downloads.cancel(book.id); model.delete(book); deleting = null }) { Text("Delete", color = palette.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A filter of the read list. */
enum class LibraryFilter(val label: String) {
    Unread("Unread"), OnDevice("On the device"), Comics("Comics"), Novels("Novels");

    /** [onDevice] holds the online books with a chapter on the device. */
    fun keeps(book: Book, onDevice: Set<UUID>): Boolean = when (this) {
        Unread -> !book.finished
        // A book from a file is on the device. An online book needs a downloaded chapter.
        OnDevice -> book.origin == null || book.id in onDevice
        Comics -> book.format != BookFormat.Novel
        Novels -> book.format == BookFormat.Novel
    }
}

/** "All", then one chip for each category, then a chip that edits the categories. */
@Composable
private fun CategoryChips(categories: List<BookCategory>, chosen: UUID?, choose: (UUID?) -> Unit, edit: () -> Unit) {
    val palette = LocalPalette.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { CategoryChip("All", chosen == null) { choose(null) } }
        items(categories, key = { it.id }) { item -> CategoryChip(item.name, chosen == item.id) { choose(item.id) } }
        item {
            val label = if (categories.isEmpty()) "Add a category" else "Edit"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 36.dp).clip(CircleShape).background(palette.surfaceHigh)
                    .clickable(onClickLabel = if (categories.isEmpty()) label else "Edit the categories", onClick = edit).padding(horizontal = 12.dp),
            ) {
                Icon(if (categories.isEmpty()) Icons.Outlined.Add else Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (selected) palette.accent else palette.surfaceHigh)
            .selectable(selected = selected, onClick = onClick).padding(horizontal = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) palette.onAccent else palette.textPrimary)
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

/** A long press opens the menu of a book. A tap reads it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookMenu(book: Book, model: AppModel, downloads: Downloads, onRead: (Book) -> Unit, onDelete: (Book) -> Unit, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
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
            if (!book.listed) {
                DropdownMenuItem(text = { Text("Add to the read list") }, leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) }, onClick = {
                    open = false; model.update(book.id) { it.copy(listed = true) }
                })
            }
            if (book.origin != null) {
                DropdownMenuItem(text = { Text("Download all chapters") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = {
                    open = false; downloads.downloadChapters(book)
                })
                // The menu asks the disk only while it is open.
                if (downloads.hasDownloads(book)) {
                    DropdownMenuItem(text = { Text("Remove downloads") }, leadingIcon = { Icon(Icons.Outlined.FolderDelete, null) }, onClick = {
                        open = false; downloads.cancel(book.id); downloads.remove(book)
                    })
                }
            }
            if (book.listed && model.library.value.categories.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Categories") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, onClick = { open = false; choosing = true })
            }
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { open = false; onDelete(book) })
        }
    }
    if (choosing) {
        val library by model.library.collectAsState()
        val chosen = library.book(book.id)?.categories.orEmpty()
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Categories") },
            text = {
                Column {
                    library.categories.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = item.id in chosen, role = Role.Checkbox) { on ->
                                model.update(book.id) { it.copy(categories = it.categories.filterNot { id -> id == item.id } + if (on) listOf(item.id) else emptyList()) }
                            },
                        ) {
                            Checkbox(checked = item.id in chosen, onCheckedChange = null)
                            Text(item.name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Done") } },
        )
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
