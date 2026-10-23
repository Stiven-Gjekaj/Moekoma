package io.github.stivengjekaj.moekoma.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import io.github.stivengjekaj.moekoma.VolumeKeys
import io.github.stivengjekaj.moekoma.core.ReadingMode
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Preferences
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** The part of the screen that the user taps, from the left edge to the right edge. */
enum class TapZone { Left, Middle, Right }

val ReadingMode.label
    get() = when (this) {
        ReadingMode.LeftToRight -> "Left to right"
        ReadingMode.RightToLeft -> "Right to left"
        ReadingMode.Vertical -> "Vertical scroll"
    }

private val ReadingMode.icon: ImageVector
    get() = when (this) {
        ReadingMode.LeftToRight -> Icons.AutoMirrored.Outlined.ArrowForward
        ReadingMode.RightToLeft -> Icons.AutoMirrored.Outlined.ArrowBack
        ReadingMode.Vertical -> Icons.Outlined.ArrowDownward
    }

/** Reads one comic of the library. A tap in the middle shows or hides the bars. A tap at an edge turns the page. */
@Composable
fun ReaderScreen(model: AppModel, bookId: UUID, prefs: Preferences, onClose: () -> Unit) {
    val library by model.library.collectAsState()
    val book = library.book(bookId) ?: return
    val pages = remember(bookId) { model.pages(book) }
    val mode = library.mode(book)
    val palette = LocalPalette.current
    val eInk = palette.isEInk
    var page by rememberSaveable(bookId) { mutableIntStateOf(book.page.coerceIn(0, maxOf(pages.size - 1, 0))) }
    var bars by rememberSaveable { mutableStateOf(true) }
    var turns by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    SystemBars(visible = bars)
    KeepScreenOn(prefs.keepScreenOn)

    LaunchedEffect(page) {
        if (pages.isNotEmpty()) model.update(bookId) { it.read(page) }
    }
    // On an e-ink screen, a black frame clears the ghost image of the last pages.
    LaunchedEffect(turns) {
        if (eInk && prefs.refreshEvery > 0 && turns > 0 && turns % prefs.refreshEvery == 0) {
            flash = true
            delay(120)
            flash = false
        }
    }

    fun go(to: Int) {
        if (to in pages.indices && to != page) {
            page = to
            turns++
        }
    }

    fun tap(zone: TapZone) {
        when (zone) {
            TapZone.Middle -> bars = !bars
            // The left edge goes back in a western comic and forward in a manga.
            else -> go(page + if ((zone == TapZone.Right) != (mode == ReadingMode.RightToLeft)) 1 else -1)
        }
    }

    Box(Modifier.fillMaxSize().background(palette.readerBackground)) {
        when {
            pages.isEmpty() -> Text(
                "This book has no pages.", color = palette.textSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
            mode == ReadingMode.Vertical -> VerticalReader(pages, page, eInk, onPage = { page = it }, onTurn = { turns++ }, onTap = { bars = !bars })
            else -> PagedReader(pages, mode == ReadingMode.RightToLeft, page, eInk, onPage = ::go, onTap = ::tap)
        }

        if (eInk) {
            if (bars) Chrome(book.title, page, pages.size, mode, onClose, onMode = { m -> model.update(bookId) { it.copy(mode = m) } }, onPage = ::go)
        } else {
            AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut()) {
                Chrome(book.title, page, pages.size, mode, onClose, onMode = { m -> model.update(bookId) { it.copy(mode = m) } }, onPage = ::go)
            }
        }
        if (flash) Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

/** One page at a time, with a swipe or a tap at the edge. Each page zooms with a pinch or a double tap. */
@Composable
private fun PagedReader(
    pages: List<File>, rightToLeft: Boolean, page: Int, eInk: Boolean,
    onPage: (Int) -> Unit, onTap: (TapZone) -> Unit,
) {
    val pager = rememberPagerState(initialPage = page) { pages.size }
    var zoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The pager follows a page that the slider, a tap, or a key chooses. An e-ink screen jumps with no animation.
    LaunchedEffect(page) {
        if (pager.currentPage != page) if (eInk) pager.scrollToPage(page) else pager.animateScrollToPage(page)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { if (it != page) onPage(it) }
    }
    VolumeHandler { forward -> onPage(page + if (forward) 1 else -1) }

    // In a manga the first page is at the right. reverseLayout puts it there.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HorizontalPager(
            state = pager,
            reverseLayout = rightToLeft,
            // An e-ink screen does not scroll: a swipe turns the page at once.
            userScrollEnabled = !zoomed && !eInk,
            pageSpacing = 14.dp,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize().then(
                if (eInk) Modifier.pointerInput(rightToLeft) {
                    var total = 0f
                    detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                        if (abs(total) > 48.dp.toPx() && !zoomed) {
                            val towardsStart = total > 0
                            scope.launch { onPage(pager.currentPage + if (towardsStart != rightToLeft) -1 else 1) }
                        }
                    }) { _, amount -> total += amount }
                } else Modifier,
            ),
        ) { index ->
            ZoomablePage(pages[index], index, onTap = onTap, onZoom = { if (index == pager.currentPage) zoomed = it })
        }
    }
}

/** A page that zooms with a pinch and a double tap. While it is zoomed, a drag moves the page and not the pager. */
@Composable
private fun ZoomablePage(file: File, index: Int, onTap: (TapZone) -> Unit, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember { mutableStateOf(false) }

    fun clamp(value: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1) / 2
        val maxY = size.height * (s - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun set(s: Float, o: Offset) {
        val wasZoomed = scale > 1.01f
        scale = s
        offset = clamp(o, s)
        if ((s > 1.01f) != wasZoomed) onZoom(s > 1.01f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { point ->
                        val x = point.x / maxOf(size.width, 1)
                        onTap(if (x < 0.3f) TapZone.Left else if (x > 0.7f) TapZone.Right else TapZone.Middle)
                    },
                    onDoubleTap = { point ->
                        if (scale > 1.01f) set(1f, Offset.Zero)
                        else {
                            // The point under the finger stays under the finger.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            set(2.5f, (point - center) * (1 - 2.5f))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinch = event.changes.count { it.pressed } > 1
                        if (pinch || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            set((scale * zoom).coerceIn(1f, 4f), offset + pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .semantics {
                contentDescription = "Page ${index + 1}"
                stateDescription = if (loaded) "Loaded" else "Loading"
            },
    ) {
        AsyncImage(
            model = pageRequest(file),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { loaded = it is AsyncImagePainter.State.Success },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}

/**
 * All pages in one column, as in a webtoon. The page at the top of the screen is the current page.
 * An e-ink screen moves one screen at each tap at the bottom, and back at each tap at the top.
 */
@Composable
private fun VerticalReader(
    pages: List<File>, page: Int, eInk: Boolean,
    onPage: (Int) -> Unit, onTurn: () -> Unit, onTap: () -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = page)
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(0) }

    fun screen(forward: Boolean) {
        scope.launch {
            val step = height * 0.9f * if (forward) 1 else -1
            if (eInk) list.scroll { scrollBy(step) } else list.animateScrollBy(step)
            onTurn()
        }
    }

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collect { onPage(it) }
    }
    LaunchedEffect(page) {
        if (list.firstVisibleItemIndex != page) list.scrollToItem(page)
    }
    VolumeHandler(::screen)

    LazyColumn(
        state = list,
        userScrollEnabled = !eInk,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height }
            .pointerInput(eInk) {
                detectTapGestures { point ->
                    val y = point.y / maxOf(height, 1)
                    when {
                        eInk && y > 0.7f -> screen(true)
                        eInk && y < 0.3f -> screen(false)
                        else -> onTap()
                    }
                }
            },
    ) {
        itemsIndexed(pages, key = { index, _ -> index }) { index, file ->
            var loaded by remember { mutableStateOf(false) }
            AsyncImage(
                model = pageRequest(file, half = true),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                // Before the image loads, a page has the shape of a paperback, so that the list does not load each page at once.
                modifier = Modifier.fillMaxWidth().then(if (loaded) Modifier else Modifier.aspectRatio(2f / 3f)),
            )
        }
    }
}

/** The image of a page at two times the long side of the screen, for the zoom. A large page keeps the memory low. */
@Composable
private fun pageRequest(file: File, half: Boolean = false): ImageRequest {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val longSide = with(density) { maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx() }
    val pixels = (longSide * if (half) 1 else 2).coerceAtMost(4096)
    return remember(file, pixels) { ImageRequest.Builder(context).data(file).size(pixels, pixels).build() }
}

/** The reader gets the volume keys and the page keys while it is open. */
@Composable
private fun VolumeHandler(onKey: (forward: Boolean) -> Unit) {
    val current by androidx.compose.runtime.rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val handler: (Boolean) -> Unit = { current(it) }
        VolumeKeys.handler = handler
        onDispose { if (VolumeKeys.handler === handler) VolumeKeys.handler = null }
    }
}

/** The screen does not go dark while a book is open, when the user asks for it in Settings. */
@Composable
fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** The bars of the system show with the bars of the reader. */
@Composable
fun SystemBars(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(visible) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun Chrome(
    title: String, page: Int, count: Int, mode: ReadingMode,
    onClose: () -> Unit, onMode: (ReadingMode) -> Unit, onPage: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    // An e-ink screen shows a solid bar. A translucent bar shows the page through it.
    val barColor = if (palette.isEInk) palette.surface else palette.surface.copy(alpha = 0.92f)
    var modes by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().background(barColor).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Close") }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { modes = true }) { Icon(mode.icon, contentDescription = "Reading mode, ${mode.label}") }
                DropdownMenu(expanded = modes, onDismissRequest = { modes = false }) {
                    ReadingMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { Icon(option.icon, null) },
                            trailingIcon = { if (option == mode) Icon(Icons.Outlined.Check, contentDescription = "Chosen", Modifier.size(18.dp)) },
                            onClick = { modes = false; onMode(option) },
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().background(barColor).navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
        ) {
            if (count > 1) {
                // In a manga the first page is at the right.
                CompositionLocalProvider(LocalLayoutDirection provides if (mode == ReadingMode.RightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Slider(
                        value = page.toFloat(),
                        onValueChange = { onPage(it.toInt()) },
                        valueRange = 0f..(count - 1).toFloat(),
                        steps = if (count > 2) count - 2 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accent, activeTrackColor = palette.accent, inactiveTrackColor = palette.outline,
                            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                        ),
                        modifier = Modifier.semantics { contentDescription = "Page" },
                    )
                }
            }
            Text("${page + 1} of $count", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        }
    }
}
