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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Settings
import io.github.stivengjekaj.moekoma.data.TextStyle
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import io.github.stivengjekaj.moekoma.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** The host that serves the files of a book to the web view. It is not a real host. */
private const val BOOK_HOST = "book.moekoma.invalid"

/** Reads a novel one chapter at a time. A tap shows or hides the bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(model: AppModel, settings: Settings, bookId: UUID, onClose: () -> Unit) {
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

    BackHandler(onBack = onClose)
    SystemBars(visible = bars)
    KeepScreenOn(prefs.keepScreenOn)

    LaunchedEffect(bookId) {
        novel = withContext(Dispatchers.IO) { model.novel(book) }
        novel?.let { chapter = chapter.coerceIn(0, maxOf(it.chapters.size - 1, 0)) }
        loaded = true
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
            else -> ChapterView(
                folder = folder,
                path = current.chapters[chapter.coerceIn(current.chapters.indices)].path,
                css = css,
                start = start,
                eInk = palette.isEInk,
                background = palette.background,
                onPosition = { value -> model.update(bookId) { it.read(chapter = chapter, position = value) } },
                onTap = { bars = !bars },
                onLink = { path -> current.chapters.indexOfFirst { File(folder, it.path).canonicalPath == path }.takeIf { it >= 0 }?.let { go(it) } },
                onEnd = { forward -> go(chapter + if (forward) 1 else -1, if (forward) 0.0 else 1.0) },
                onReady = { webView = it },
            )
        }

        val title = current?.chapters?.getOrNull(chapter)?.title ?: "Chapter ${chapter + 1}"
        val chrome: @Composable () -> Unit = {
            NovelChrome(
                book.title, title, chapter, current?.chapters?.size ?: 0, book.progress,
                onClose = onClose, onChapters = { showChapters = true }, onStyle = { showStyle = true },
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
    onReady: (WebView) -> Unit,
) {
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
              style.textContent = ${JSONObject.quote(currentCss)};
              const target = $target;
              requestAnimationFrame(function () { window.scrollTo(0, target * (document.documentElement.scrollHeight - window.innerHeight)); });
            })();
            """.trimIndent(),
        ) { web.alpha = 1f }
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
            WebView(context).apply {
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
    onClose: () -> Unit, onChapters: () -> Unit, onStyle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
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
