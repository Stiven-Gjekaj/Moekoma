package io.github.stivengjekaj.moekoma.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.VolumeKeys
import io.github.stivengjekaj.moekoma.core.Novel
import io.github.stivengjekaj.moekoma.core.NovelSearch
import io.github.stivengjekaj.moekoma.core.BookMark
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Settings
import io.github.stivengjekaj.moekoma.data.TextStyle
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import io.github.stivengjekaj.moekoma.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The host that serves the files of a book to the web view. It is not a real host. */
private const val BOOK_HOST = "book.moekoma.invalid"

/** Reads a novel one chapter at a time. A tap shows or hides the bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(
    model: AppModel, settings: Settings, bookId: UUID,
    /** Downloads a chapter of an online novel, when the device does not have its file. */
    fetchChapter: (suspend (Int) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val library by model.library.collectAsState()
    val prefs by settings.preferences.collectAsState()
    val book = library.book(bookId) ?: return
    val folder = remember(bookId) { model.folder(book) }
    val palette = LocalPalette.current
    var novel by remember { mutableStateOf<Novel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var chapter by rememberSaveable(bookId) { mutableIntStateOf(book.page) }
    var start by rememberSaveable(bookId) { mutableStateOf(book.position) }
    var bars by rememberSaveable { mutableStateOf(true) }
    var showChapters by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val control = remember { ChapterControl() }
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchWords by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(NovelSearch.Found(emptyList(), 0)) }
    // Words to show when the chapter opens, from a search or a highlight.
    var pendingFind by remember { mutableStateOf<String?>(null) }
    var showMarks by remember { mutableStateOf(false) }
    // The place that the page reported last, for a bookmark and a highlight.
    var place by remember { mutableStateOf(book.position) }
    fun showSavedMarks() {
        val texts = model.library.value.marks(bookId).filter { it.chapter == chapter && it.kind == BookMark.Kind.Highlight }.map { it.text }
        scope.launch { control.showMarks(texts) }
    }

    BackHandler(onBack = onClose)
    SystemBars(visible = bars)
    KeepScreenOn(prefs.keepScreenOn)

    LaunchedEffect(bookId) {
        novel = withContext(Dispatchers.IO) { model.novel(book) }
        novel?.let { chapter = chapter.coerceIn(0, maxOf(it.chapters.size - 1, 0)) }
        loaded = true
    }

    // A chapter of an online novel downloads when it opens.
    var ready by remember { mutableStateOf<Int?>(null) }
    var fault by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(novel, chapter, attempt) {
        val current = novel ?: return@LaunchedEffect
        val item = current.chapters.getOrNull(chapter) ?: return@LaunchedEffect
        fault = null
        if (fetchChapter != null && item.remote != null && !File(folder, item.path).isFile) {
            ready = null
            try {
                fetchChapter(chapter)
            } catch (e: Exception) {
                fault = e.message ?: "The chapter did not load. (${e.javaClass.simpleName})"
                return@LaunchedEffect
            }
        }
        ready = chapter
    }

    fun go(index: Int, place: Double = 0.0) {
        val chapters = novel?.chapters ?: return
        if (index !in chapters.indices) return
        start = place
        chapter = index
        model.update(bookId) { it.read(chapter = index, position = place) }
    }

    val css = prefs.text.css(palette.background.hex(), palette.textPrimary.hex(), palette.accent.hex(), palette.isEInk)

    Box(Modifier.fillMaxSize().background(palette.background)) {
        val current = novel
        when {
            !loaded -> {}
            current == null -> EmptyArt("The book did not open", "The chapters of this book cannot be read. Import the book again.")
            current.chapters.isEmpty() -> EmptyArt("No chapters yet", "The book has no chapters.")
            fault != null -> Column(Modifier.align(Alignment.Center)) {
                EmptyArt("The chapter did not load", fault!!) { PillButton("Try again", { attempt++ }) }
            }
            ready != chapter -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.align(Alignment.Center))
            else -> ChapterView(
                folder = folder,
                path = current.chapters[chapter.coerceIn(current.chapters.indices)].path,
                css = css,
                start = start,
                eInk = palette.isEInk,
                background = palette.background,
                onPosition = { value -> place = value; model.update(bookId) { it.read(chapter = chapter, position = value) } },
                onTap = { bars = !bars },
                onLink = { path -> current.chapters.indexOfFirst { File(folder, it.path).canonicalPath == path }.takeIf { it >= 0 }?.let { go(it) } },
                onEnd = { forward -> go(chapter + if (forward) 1 else -1, if (forward) 0.0 else 1.0) },
                onReady = { webView = it },
                control = control,
                onLoaded = {
                    showSavedMarks()
                    scope.launch {
                        pendingFind?.let { words -> pendingFind = null; control.find(words) }
                    }
                },
                onHighlight = { words ->
                    model.change { it.copy(marks = it.marks + BookMark.at(bookId, chapter, place, words, BookMark.Kind.Highlight)) }
                    showSavedMarks()
                },
            )
        }

        val title = current?.chapters?.getOrNull(chapter)?.title ?: "Chapter ${chapter + 1}"
        val chrome: @Composable () -> Unit = {
            NovelChrome(
                book.title, title, chapter, current?.chapters?.size ?: 0, book.progress,
                onClose = onClose, onChapters = { showChapters = true }, onStyle = { showStyle = true }, onSearch = { showSearch = true }, onMarks = { showMarks = true },
                onPrevious = { go(chapter - 1) }, onNext = { go(chapter + 1) },
            )
        }
        if (palette.isEInk) { if (bars) chrome() } else AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut()) { chrome() }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }, containerColor = palette.surface) {
            ChapterList(novel?.chapters ?: emptyList(), chapter) { index -> showChapters = false; go(index) }
        }
    }
    if (showSearch) {
        // The search reads the chapters that are on the device. The text of a chapter is its HTML with no tags.
        LaunchedEffect(searchWords) {
            val chapters = novel?.chapters.orEmpty()
            found = withContext(Dispatchers.IO) {
                NovelSearch.search(searchWords, chapters.size) { index -> File(folder, chapters[index].path).takeIf { it.isFile }?.readText() }
            }
        }
        ModalBottomSheet(onDismissRequest = { showSearch = false }, containerColor = palette.surface) {
            Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchWords, onValueChange = { searchWords = it }, singleLine = true, placeholder = { Text("Words in the book") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth(),
                )
                if (found.missing > 0) {
                    Text("${found.missing} chapters are not on the device, so the search does not read them.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(found.results) { result ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                pendingFind = searchWords.trim()
                                showSearch = false
                                if (result.chapter == chapter) scope.launch { pendingFind?.let { words -> pendingFind = null; control.find(words) } } else go(result.chapter)
                            }.padding(vertical = 10.dp),
                        ) {
                            Text(novel?.chapters?.getOrNull(result.chapter)?.title ?: "Chapter ${result.chapter + 1}", style = MaterialTheme.typography.titleSmall)
                            Text(result.snippet, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    if (showMarks) {
        val chapters = novel?.chapters.orEmpty()
        fun title(index: Int) = chapters.getOrNull(index)?.title ?: "Chapter ${index + 1}"
        ModalBottomSheet(onDismissRequest = { showMarks = false }, containerColor = palette.surface) {
            Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        val words = control.visibleText()
                        model.change { it.copy(marks = it.marks + BookMark.at(bookId, chapter, place, words.ifEmpty { title(chapter) }, BookMark.Kind.Bookmark)) }
                    }
                }) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = palette.accent)
                    Text("Add a bookmark here", color = palette.accent, modifier = Modifier.padding(start = 8.dp))
                }
                Text("Select words in the chapter and choose Highlight to keep them.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                val marks = library.marks(bookId)
                if (marks.isNotEmpty()) Text("In this book", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(marks, key = { it.id }) { mark ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showMarks = false
                                if (mark.kind == BookMark.Kind.Highlight) pendingFind = mark.text
                                if (mark.chapter == chapter && mark.kind == BookMark.Kind.Highlight) {
                                    scope.launch { pendingFind?.let { words -> pendingFind = null; control.find(words) } }
                                } else {
                                    go(mark.chapter, mark.position)
                                }
                            }.padding(vertical = 8.dp).semantics(mergeDescendants = true) {
                                contentDescription = (if (mark.kind == BookMark.Kind.Highlight) "Highlight: " else "Bookmark: ") + mark.text
                            },
                        ) {
                            Icon(if (mark.kind == BookMark.Kind.Highlight) Icons.Outlined.BorderColor else Icons.Filled.Bookmark, contentDescription = null, tint = palette.accent)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(mark.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(title(mark.chapter), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            }
                            IconButton(onClick = {
                                model.change { it.copy(marks = it.marks.filterNot { m -> m.id == mark.id }) }
                                showSavedMarks()
                            }) { Icon(Icons.Outlined.Close, contentDescription = "Remove the mark", tint = palette.textSecondary) }
                        }
                    }
                }
            }
        }
    }
    if (showStyle) {
        ModalBottomSheet(onDismissRequest = { showStyle = false }, containerColor = palette.surface) {
            StyleSheet(prefs.text) { style -> settings.update { it.copy(text = style) } }
        }
    }
}

private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)

/**
 * One chapter of a novel in a web view. The scripts of the book do not run: each file comes with a
 * Content Security Policy that blocks them. The scripts of the app run with evaluateJavascript:
 * they apply the style, keep the place, and report taps.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChapterView(
    folder: File, path: String, css: String, start: Double, eInk: Boolean, background: Color,
    onPosition: (Double) -> Unit, onTap: () -> Unit, onLink: (String) -> Unit, onEnd: (forward: Boolean) -> Unit,
    onReady: (WebView) -> Unit, control: ChapterControl, onLoaded: () -> Unit, onHighlight: (String) -> Unit,
) {
    val highlight by rememberUpdatedState(onHighlight)
    val loaded by rememberUpdatedState(onLoaded)
    val context = LocalContext.current
    val position by rememberUpdatedState(onPosition)
    val tap by rememberUpdatedState(onTap)
    val link by rememberUpdatedState(onLink)
    val end by rememberUpdatedState(onEnd)
    val currentCss by rememberUpdatedState(css)
    val currentStart by rememberUpdatedState(start)
    val currentEInk by rememberUpdatedState(eInk)
    val root = remember(folder) { folder.canonicalFile }
    var view by remember { mutableStateOf<WebView?>(null) }
    var place by remember { mutableStateOf(0.0) }

    fun style(web: WebView, keepPlace: Boolean) {
        val target = if (keepPlace) "place" else currentStart.toString()
        web.evaluateJavascript(
            """
            (function () {
              const max = document.documentElement.scrollHeight - window.innerHeight;
              const place = max > 0 ? window.scrollY / max : 0;
              let style = document.getElementById('moekoma-style');
              if (!style) { style = document.createElement('style'); style.id = 'moekoma-style'; (document.head || document.documentElement).appendChild(style); }
              style.textContent = ${JSONObject.quote(currentCss + ChapterControl.CSS)};
              const target = $target;
              requestAnimationFrame(function () { window.scrollTo(0, target * (document.documentElement.scrollHeight - window.innerHeight)); });
            })();
            """.trimIndent(),
        ) {
            web.alpha = 1f
            if (!keepPlace) loaded()
        }
    }

    /** Moves one screen. At the end of a chapter, it goes to the next chapter. */
    fun screen(web: WebView, forward: Boolean) {
        if (forward && place >= 0.995) { end(true); return }
        if (!forward && place <= 0.005) { end(false); return }
        val behavior = if (currentEInk) "instant" else "smooth"
        web.evaluateJavascript("window.scrollBy({ top: ${if (forward) "" else "-"}window.innerHeight * 0.9, behavior: '$behavior' });", null)
    }

    DisposableEffect(Unit) {
        val handler: (Boolean) -> Unit = { forward -> view?.let { screen(it, forward) } }
        VolumeKeys.handler = handler
        onDispose { if (VolumeKeys.handler === handler) VolumeKeys.handler = null }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            ChapterWebView(context).apply {
                this.onHighlight = { words -> highlight(words) }
                setBackgroundColor(background.toArgb())
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.textZoom = 100
                isVerticalScrollBarEnabled = false
                addJavascriptInterface(object {
                    @JavascriptInterface fun position(value: Double) { post { place = value; position(value) } }
                    @JavascriptInterface fun tap(y: Double) {
                        post {
                            when {
                                currentEInk && y > 0.7 -> screen(this@apply, true)
                                currentEInk && y < 0.3 -> screen(this@apply, false)
                                else -> tap()
                            }
                        }
                    }
                }, "MoekomaReader")
                webViewClient = object : WebViewClient() {
                    // The font of the app is a plain TTF file in res/font, so it opens as a raw resource.
                    @SuppressLint("ResourceType")
                    override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url
                        if (url.host != BOOK_HOST) return null
                        if (url.path == "/__app/karla.ttf") {
                            return WebResourceResponse("font/ttf", null, context.resources.openRawResource(R.font.karla))
                        }
                        val file = File(root, url.path ?: "").canonicalFile
                        // A link can point out of the folder of the book. The app never serves such a file.
                        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), null)
                        val headers = mapOf("Content-Security-Policy" to "script-src 'none'; object-src 'none'")
                        return WebResourceResponse(mime(file.name), null, 200, "OK", headers, file.inputStream())
                    }

                    override fun onPageFinished(web: WebView, url: String) {
                        web.evaluateJavascript(SCRIPT, null)
                        web.evaluateJavascript(ChapterControl.SCRIPT, null)
                        style(web, keepPlace = false)
                    }

                    override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.host == BOOK_HOST) {
                            val target = File(root, url.path ?: "").canonicalPath
                            val shown = web.url?.let { Uri.parse(it).path }?.let { File(root, it).canonicalPath }
                            // A link in the same chapter only moves the page.
                            if (target == shown && url.fragment != null) {
                                web.evaluateJavascript("document.getElementById(${JSONObject.quote(url.fragment)})?.scrollIntoView()", null)
                            } else {
                                link(target)
                            }
                        } else if (url.scheme == "http" || url.scheme == "https") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                        }
                        return true
                    }
                }
                view = this
                control.view = this
                onReady(this)
            }
        },
        update = { web ->
            val url = "https://$BOOK_HOST/" + path.split('/').joinToString("/") { Uri.encode(it) }
            if (web.tag != url) {
                web.tag = url
                web.alpha = 0f
                web.setBackgroundColor(background.toArgb())
                web.loadUrl(url)
            } else if (web.getTag(R.id.moekoma_css) != css) {
                style(web, keepPlace = true)
            }
            web.setTag(R.id.moekoma_css, css)
        },
    )
}

/** Reports the place on each scroll, and each tap that is not on a link. */
private val SCRIPT = """
    (function () {
      if (window.moekomaReady) return;
      window.moekomaReady = true;
      let viewport = document.querySelector('meta[name=viewport]');
      if (!viewport) { viewport = document.createElement('meta'); viewport.name = 'viewport'; (document.head || document.documentElement).appendChild(viewport); }
      viewport.content = 'width=device-width, initial-scale=1';
      let timer = null;
      function place() {
        const max = document.documentElement.scrollHeight - window.innerHeight;
        return max > 0 ? Math.min(Math.max(window.scrollY / max, 0), 1) : 1;
      }
      window.addEventListener('scroll', function () {
        if (timer) return;
        timer = setTimeout(function () { timer = null; MoekomaReader.position(place()); }, 250);
      }, { passive: true });
      document.addEventListener('click', function (event) {
        if (event.target.closest('a')) return;
        MoekomaReader.tap(event.clientY / window.innerHeight);
      });
      MoekomaReader.position(place());
    })();
""".trimIndent()

private fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "xhtml", "xht" -> "application/xhtml+xml"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    else -> "application/octet-stream"
}

@Composable
private fun NovelChrome(
    title: String, chapterTitle: String, chapter: Int, count: Int, progress: Double,
    onClose: () -> Unit, onChapters: () -> Unit, onStyle: () -> Unit, onSearch: () -> Unit, onMarks: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
) {
    val palette = LocalPalette.current
    val barColor = palette.surface
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(barColor).statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Close") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chapterTitle, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search the book") }
            IconButton(onClick = onMarks) { Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Marks") }
            IconButton(onClick = onChapters) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Chapters") }
            IconButton(onClick = onStyle) { Icon(Icons.Outlined.FormatSize, contentDescription = "Text settings") }
        }
        Box(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().background(barColor).navigationBarsPadding().padding(horizontal = Gutter, vertical = 6.dp),
        ) {
            IconButton(onClick = onPrevious, enabled = chapter > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous chapter") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressLine(progress)
                Text("Chapter ${chapter + 1} of $count", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            }
            IconButton(onClick = onNext, enabled = chapter + 1 < count) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next chapter") }
        }
    }
}

/** The chapters, or the chapters whose title or number has the text of the search. */
@Composable
private fun ChapterList(chapters: List<Novel.Chapter>, current: Int, onChoose: (Int) -> Unit) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val shown = chapters.withIndex().filter { (index, item) ->
        val text = query.trim()
        text.isEmpty() || (index + 1).toString() == text || (item.title ?: "").contains(text, ignoreCase = true)
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(current - 3, 0))
    Column(Modifier.padding(horizontal = Gutter)) {
        Text("Chapters", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Title or number") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(shown, key = { it.index }) { (index, item) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChoose(index) }.padding(vertical = 8.dp),
                ) {
                    Text(
                        item.title ?: "Chapter ${index + 1}", style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f),
                    )
                    if (index == current) Icon(Icons.Filled.Bookmark, contentDescription = "Now", tint = palette.accent)
                }
            }
        }
    }
}

/** The font, the size, the line spacing, the margins, and justified text. */
@Composable
private fun StyleSheet(style: TextStyle, onChange: (TextStyle) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.padding(horizontal = Gutter).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Text", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextStyle.Family.entries.forEach { family ->
                FilterChip(
                    selected = style.family == family, onClick = { onChange(style.copy(family = family)) }, label = { Text(family.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.accent, selectedLabelColor = palette.onAccent),
                )
            }
        }
        Stepper("Size", style.size.toString(), { onChange(style.copy(size = (style.size - 1).coerceAtLeast(12))) }, { onChange(style.copy(size = (style.size + 1).coerceAtMost(34))) })
        Stepper(
            "Line spacing", "%.1f".format(style.lineHeight),
            { onChange(style.copy(lineHeight = ((style.lineHeight * 10).toInt() - 1).coerceAtLeast(12) / 10f)) },
            { onChange(style.copy(lineHeight = ((style.lineHeight * 10).toInt() + 1).coerceAtMost(22) / 10f)) },
        )
        Stepper("Margins", style.margin.toString(), { onChange(style.copy(margin = (style.margin - 4).coerceAtLeast(8))) }, { onChange(style.copy(margin = (style.margin + 4).coerceAtMost(56))) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Justify the text", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = style.justify, onCheckedChange = { onChange(style.copy(justify = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent),
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onLess) { Icon(Icons.Outlined.Remove, contentDescription = "Less $label") }
        Text(value, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onMore) { Icon(Icons.Outlined.Add, contentDescription = "More $label") }
    }
}
