package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.data.ChapterContent
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.util.UUID
import kotlin.math.roundToInt

/** Reads a series of an online source, one chapter of pages at a time. */
@Composable
fun SeriesReaderScreen(container: AppContainer, bookId: UUID, onClose: () -> Unit) {
    val palette = LocalPalette.current
    val library by container.model.library.collectAsState()
    val prefs by container.settings.preferences.collectAsState()
    val book = library.books.firstOrNull { it.id == bookId } ?: return
    val novel = remember(bookId, book.pageCount) { container.model.novel(book) }
    var chapter by remember { mutableIntStateOf(book.page.coerceIn(0, maxOf((novel?.chapters?.size ?: 1) - 1, 0))) }
    var pages by remember { mutableStateOf<List<Any>?>(null) }
    var start by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var fault by remember { mutableStateOf<String?>(null) }
    var showChapters by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val count = novel?.chapters?.size ?: 0

    val passes by BrowserChecks.passes.collectAsState()
    LaunchedEffect(chapter, attempt, passes) {
        pages = null
        fault = null
        val item = novel?.chapters?.getOrNull(chapter)
        val origin = book.origin
        val source = container.sources.installed(book)
        if (item?.remote == null || origin == null || (source == null && container.downloads.pages(book, chapter) == null)) {
            fault = "The source of this series is not installed. Add its list again in Settings, Sources."
            return@LaunchedEffect
        }
        try {
            // A chapter on the device opens from its files, with no network.
            val got: List<Any> = withContext(Dispatchers.IO) { container.downloads.pages(book, chapter) } ?: run {
                val content = container.sources.load(source!!).content(OnlineChapter(item.remote!!, item.title ?: ""), OnlineSeries(origin.series, book.title))
                (content as? ChapterContent.Pages ?: throw IllegalStateException("The source gave text for this chapter, not pages.")).pages
            }
            if (got.isEmpty()) throw IllegalStateException("The chapter has no pages.")
            // The book keeps the place in the chapter as a part from 0 to 1.
            val place = if (chapter == book.page) book.position else 0.0
            start = (place * (got.size - 1)).roundToInt().coerceIn(0, got.size - 1)
            page = start
            pages = got
        } catch (e: Exception) {
            fault = e.message ?: "The chapter did not load. (${e.javaClass.simpleName})"
        }
    }

    fun go(to: Int) {
        if (to !in 0 until count) return
        container.model.update(bookId) { it.read(chapter = to, position = 0.0) }
        chapter = to
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        val shown = pages
        when {
            fault != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyArt("The chapter did not load", fault!!) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Try again", { attempt++ })
                        TextButton(onClick = onClose) { Text("Close", color = palette.accent) }
                    }
                }
            }
            shown == null -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.align(Alignment.Center))
            else -> key(chapter) {
                val title = novel?.chapters?.getOrNull(chapter)?.title?.let { "${book.title} · $it" } ?: book.title
                PageReader(
                    title, shown, start, library.mode(book), prefs,
                    onPage = { now ->
                        page = now
                        val size = shown.size
                        container.model.update(bookId) { it.read(chapter = chapter, position = if (size > 1) now.toDouble() / (size - 1) else 1.0) }
                    },
                    onMode = { mode -> container.model.update(bookId) { it.copy(mode = mode) } },
                    onClose = onClose,
                    onLayout = { layout -> container.settings.update { it.copy(layout = layout) } },
                )
                // The buttons for the chapters show on the last page.
                if (page >= shown.size - 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
                    ) {
                        if (chapter > 0) PillButton("Previous chapter", { go(chapter - 1) })
                        FilledIconButton(onClick = { showChapters = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.accent, contentColor = palette.onAccent)) {
                            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = tr("Chapters"))
                        }
                        if (chapter + 1 < count) PillButton("Next chapter", { go(chapter + 1) })
                    }
                }
            }
        }
        if (showChapters && novel != null) {
            ChapterList(novel.chapters.map { it.title }, chapter, onPick = { go(it); showChapters = false }, onClose = { showChapters = false })
        }
    }
}

/** The chapters of the book, or the chapters whose title or number has the text of the search. */
@Composable
private fun ChapterList(titles: List<String?>, current: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val shown = titles.withIndex().filter { (index, title) ->
        val q = query.trim()
        q.isEmpty() || (index + 1).toString() == q || title.orEmpty().contains(q, ignoreCase = true)
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    SubScreen("Chapters", onClose) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Title or number") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            )
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, item -> item.index }) { _, (index, title) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(index) }.padding(horizontal = Gutter, vertical = 10.dp),
                    ) {
                        Text(
                            title ?: "Chapter ${index + 1}", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == current) FontWeight.Bold else null, modifier = Modifier.weight(1f),
                        )
                        if (index == current) Icon(Icons.Filled.Bookmark, contentDescription = tr("You read here"), tint = palette.accent)
                    }
                }
            }
        }
    }
}
