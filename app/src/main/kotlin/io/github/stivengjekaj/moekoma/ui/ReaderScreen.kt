package io.github.stivengjekaj.moekoma.ui

import android.view.HapticFeedbackConstants
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
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
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size
import io.github.stivengjekaj.moekoma.VolumeKeys
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.core.ReadingMode
import io.github.stivengjekaj.moekoma.core.Spreads
import io.github.stivengjekaj.moekoma.data.PdfDocument
import io.github.stivengjekaj.moekoma.data.PdfPage
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Preferences
import io.github.stivengjekaj.moekoma.data.PageFit
import io.github.stivengjekaj.moekoma.data.PageLayout
import io.github.stivengjekaj.moekoma.data.TapZones
import io.github.stivengjekaj.moekoma.data.CropBorders
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** Reads one comic or one PDF of the library. */
@Composable
fun ReaderScreen(model: AppModel, bookId: UUID, prefs: Preferences, onLayout: (PageLayout) -> Unit, onClose: () -> Unit) {
    val library by model.library.collectAsState()
    val book = library.book(bookId) ?: return
    // A page is the file of an image, or a page of a PDF that the image loader draws.
    val pdf = remember(bookId) { if (book.format == BookFormat.Pdf) runCatching { PdfDocument(model.pdf(book)) }.getOrNull() else null }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }
    val pages: List<Any> = remember(bookId) { pdf?.let { doc -> List(doc.pageCount) { PdfPage(doc, it) } } ?: model.pages(book) }
    // To open a book is to read it, so the book goes into the history and into Continue reading.
    LaunchedEffect(bookId) { model.update(bookId) { it.read(it.page) } }
    PageReader(
        title = book.title, pages = pages, start = book.page, mode = library.mode(book), prefs = prefs,
        onPage = { page -> model.update(bookId) { it.read(page) } },
        onMode = { m -> model.update(bookId) { it.copy(mode = m) } },
        onClose = onClose,
        onLayout = onLayout,
    )
}

/**
 * Pages with the bars of the reader: the files of a book, the pages of a PDF, or the pages of a catalog.
 * A tap in the middle shows or hides the bars. A tap at an edge turns the page.
 */
@Composable
fun PageReader(
    title: String, pages: List<Any>, start: Int, mode: ReadingMode, prefs: Preferences,
    onPage: (Int) -> Unit, onMode: (ReadingMode) -> Unit, onClose: () -> Unit,
    onLayout: (PageLayout) -> Unit = {},
) {
    val layout = prefs.layout
    val palette = LocalPalette.current
    val eInk = palette.isEInk
    var page by rememberSaveable { mutableIntStateOf(start.coerceIn(0, maxOf(pages.size - 1, 0))) }
    var bars by rememberSaveable { mutableStateOf(true) }
    var turns by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    SystemBars(visible = bars)
    KeepScreenOn(prefs.keepScreenOn)

    LaunchedEffect(page) {
        if (pages.isNotEmpty()) onPage(page)
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

    // Two pages show side by side when the screen is wider than it is tall. A page of two pages in one image is alone.
    val configuration = LocalConfiguration.current
    val pairs = mode != ReadingMode.Vertical && layout.spreads && configuration.screenWidthDp > configuration.screenHeightDp
    var widePages by remember(pages) { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(pages, pairs) {
        if (pairs) widePages = withContext(Dispatchers.IO) { pages.indices.filter { isWide(pages[it]) }.toSet() }
    }
    val spreads = remember(pages, pairs, layout.shift, widePages) {
        if (pairs) Spreads.make(pages.size, { it in widePages }, layout.shift) else pages.indices.map { listOf(it) }
    }
    val spread = Spreads.index(page, spreads)
    val currentSpreads by rememberUpdatedState(spreads)
    /** Goes by one spread. A spread of two pages counts as one turn. */
    val view = LocalView.current
    fun turn(by: Int) {
        val all = currentSpreads
        val next = all.getOrNull(Spreads.index(page, all) + by)
        if (next != null) go(next.first())
        // A turn past the last page stops there, with a light tap of the device.
        else if (by > 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    // "3 of 20", or "2-3 of 20" for a spread of two pages.
    val shown = spreads.getOrNull(spread) ?: listOf(page)
    val pageLabel = if (shown.size > 1) "${shown.first() + 1}-${shown.last() + 1} of ${pages.size}" else "${page + 1} of ${pages.size}"

    // The page holds a reference to tap from its first composition, so tap reads the layout and the mode of now.
    val taps by rememberUpdatedState(layout.taps)
    val direction by rememberUpdatedState(mode)
    fun tap(zone: TapZone) {
        when {
            zone == TapZone.Middle || taps == TapZones.Off -> bars = !bars
            taps == TapZones.Forward -> turn(1)
            // The left edge goes back in a western comic and forward in a manga.
            else -> turn(if ((zone == TapZone.Right) != (direction == ReadingMode.RightToLeft)) 1 else -1)
        }
    }

    Box(Modifier.fillMaxSize().background(palette.readerBackground)) {
        when {
            pages.isEmpty() -> Text(
                "This book has no pages.", color = palette.textSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
            mode == ReadingMode.Vertical -> VerticalReader(pages, page, eInk, onPage = { page = it }, onTurn = { turns++ }, onTap = { bars = !bars })
            else -> PagedReader(
                pages, spreads, mode == ReadingMode.RightToLeft, spread, eInk, layout,
                onSpread = { index -> spreads.getOrNull(index)?.let { go(it.first()) } }, onTap = ::tap,
            )
        }

        if (eInk) {
            if (bars) Chrome(title, page, pages.size, pageLabel, mode, layout, onClose, onMode = onMode, onLayout = onLayout, onPage = ::go)
        } else {
            AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut()) {
                Chrome(title, page, pages.size, pageLabel, mode, layout, onClose, onMode = onMode, onLayout = onLayout, onPage = ::go)
            }
        }
        if (flash) Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

/** True when a page of the device is wider than it is tall, such as two pages in one image. A page of the network counts as one page. */
private fun isWide(page: Any): Boolean {
    val file = page as? File ?: return false
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    return options.outWidth > options.outHeight && options.outHeight > 0
}

/** The title of a group of choices in a menu. */
@Composable
private fun MenuLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

/** A page from the network, with the headers that its server asks for, such as the password of a catalog. */
data class RemotePage(val url: String, val headers: Map<String, String> = emptyMap())

/**
 * One spread at a time, with a swipe or a tap at the edge. A spread is one page, or two pages on a wide screen. Each
 * page zooms with a pinch or a double tap. [page] and [onPage] count spreads.
 */
@Composable
private fun PagedReader(
    pages: List<Any>, spreads: List<List<Int>>, rightToLeft: Boolean, page: Int, eInk: Boolean, layout: PageLayout,
    onSpread: (Int) -> Unit, onTap: (TapZone) -> Unit,
) {
    val onPage = onSpread
    val pager = rememberPagerState(initialPage = page) { spreads.size }
    var zoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The pager follows a page that the slider, a tap, or a key chooses. An e-ink screen jumps with no animation, and
    // a turn that comes while the last turn moves jumps too, so that quick taps do not wait for each other.
    val target by rememberUpdatedState(page)
    val turns = remember { intArrayOf(0) }
    LaunchedEffect(page) {
        if (pager.currentPage == page) return@LaunchedEffect
        val jump = eInk || turns[0] > 0 || pager.isScrollInProgress
        turns[0]++
        try {
            if (jump) pager.scrollToPage(page) else pager.animateScrollToPage(page)
        } finally {
            turns[0]--
        }
    }
    // Only a drag of the user chooses a page. A tap during a turn stops the pager between two pages, and that page is
    // not a choice, so the pager goes on to the page of the app.
    val dragged = remember { booleanArrayOf(false) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            when {
                settled == target -> dragged[0] = false
                dragged[0] -> { dragged[0] = false; onPage(settled) }
                turns[0] == 0 -> pager.scrollToPage(target)
            }
        }
    }
    val tapped by rememberUpdatedState(onTap)
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
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                // A tap at the edge of a page that is not zoomed turns the page at once. The reader takes it before the
                // pager, because the pager uses a tap during a turn only to stop the turn, and quick taps then get lost.
                // A finger that moves is a drag of the user.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val x = down.position.x / maxOf(size.width, 1)
                    val zone = if (x < 0.3f) TapZone.Left else if (x > 0.7f) TapZone.Right else null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragged[0] = true
                            return@awaitEachGesture
                        }
                        if (!change.pressed) {
                            if (zone != null && !zoomed && event.changes.size == 1) {
                                change.consume()
                                tapped(zone)
                            }
                            return@awaitEachGesture
                        }
                    }
                }
            }.then(
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
            val group = spreads[index]
            if (group.size == 1) {
                ZoomablePage(pages[group[0]], group[0], layout.fit, layout.crop, onTap = onTap, onZoom = { if (index == pager.currentPage) zoomed = it })
            } else {
                // In a manga the first page of a pair is at the right.
                Row(Modifier.fillMaxSize()) {
                    (if (rightToLeft) group.reversed() else group).forEach { at ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            ZoomablePage(pages[at], at, layout.fit, layout.crop, onTap = onTap, onZoom = { if (index == pager.currentPage) zoomed = it })
                        }
                    }
                }
            }
        }
    }
}

/** A page that zooms with a pinch and a double tap. While it is zoomed, a drag moves the page and not the pager. */
@Composable
private fun ZoomablePage(file: Any, index: Int, fit: PageFit, crop: Boolean, onTap: (TapZone) -> Unit, onZoom: (Boolean) -> Unit) {
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
        var failed by remember(file) { mutableStateOf(false) }
        var attempt by remember(file) { mutableIntStateOf(0) }
        key(attempt) {
            val zoom = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            val image: @Composable (Modifier, ContentScale) -> Unit = { modifier, contentScale ->
                AsyncImage(
                    model = pageRequest(file, crop = crop),
                    contentDescription = null,
                    contentScale = contentScale,
                    onState = {
                        loaded = it is AsyncImagePainter.State.Success
                        failed = it is AsyncImagePainter.State.Error
                    },
                    modifier = modifier.then(zoom),
                )
            }
            // A page that fills the width or the height scrolls in the other direction.
            when (fit) {
                PageFit.Screen -> image(Modifier.fillMaxSize(), ContentScale.Fit)
                PageFit.Width -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { image(Modifier.fillMaxWidth(), ContentScale.FillWidth) }
                PageFit.Height -> Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) { image(Modifier.fillMaxHeight(), ContentScale.FillHeight) }
            }
        }
        // A page of a source can take some seconds. A page of the device loads at once, so it shows no spinner.
        val remote = file is RemotePage || file is io.github.stivengjekaj.moekoma.data.RemoteImage
        if (remote && failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The page did not load", style = MaterialTheme.typography.titleMedium)
                PillButton("Try again", { failed = false; attempt++ })
            }
        } else if (remote && !loaded) {
            CircularProgressIndicator(color = LocalPalette.current.accent, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * All pages in one column, as in a webtoon. The page at the top of the screen is the current page.
 * An e-ink screen moves one screen at each tap at the bottom, and back at each tap at the top.
 */
@Composable
private fun VerticalReader(
    pages: List<Any>, page: Int, eInk: Boolean,
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
                model = pageRequest(file, strip = true),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                // Before the image loads, a page has the shape of a paperback, so that the list does not load each page at once.
                modifier = Modifier.fillMaxWidth().then(if (loaded) Modifier else Modifier.aspectRatio(2f / 3f)),
            )
        }
    }
}

/**
 * The image of a page at two times the long side of the screen, for the zoom. A large page keeps the memory low. In
 * the vertical reader, a page is a [strip] that fills the width of the screen. A strip of a webtoon is much taller than
 * it is wide, so its width, not its long side, sets its size, and the strip stays sharp.
 */
@Composable
private fun pageRequest(file: Any, strip: Boolean = false, crop: Boolean = false): ImageRequest {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val longSide = with(density) { maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx() }
    val width = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val pixels = (longSide * 2).coerceAtMost(4096)
    return remember(file, pixels, width, strip, crop) {
        val builder = ImageRequest.Builder(context)
        // The crop reads the pixels of the page, so the page does not come as a hardware bitmap.
        if (crop) builder.transformations(CropBorders()).allowHardware(false)
        // Coil keeps a bitmap at 4096 pixels on each side. A strip can be taller, up to the limit of a bitmap that Android draws.
        if (strip) builder.size(Size(Dimension(width), Dimension.Undefined)).maxBitmapSize(Size(width, 16384)) else builder.size(pixels, pixels)
        if (file is RemotePage) {
            builder.data(file.url).httpHeaders(NetworkHeaders.Builder().apply { file.headers.forEach { (key, value) -> set(key, value) } }.build())
        } else {
            builder.data(file)
        }
        builder.build()
    }
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
    title: String, page: Int, count: Int, pageLabel: String, mode: ReadingMode, layout: PageLayout,
    onClose: () -> Unit, onMode: (ReadingMode) -> Unit, onLayout: (PageLayout) -> Unit, onPage: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    // An e-ink screen shows a solid bar. A translucent bar shows the page through it.
    val barColor = if (palette.isEInk) palette.surface else palette.surface.copy(alpha = 0.92f)
    var modes by remember { mutableStateOf(false) }
    var layoutMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().background(barColor).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Close") }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // The fit and the taps of the paged reader.
            if (mode != ReadingMode.Vertical) Box {
                IconButton(onClick = { layoutMenu = true }) { Icon(Icons.Outlined.ViewAgenda, contentDescription = "Page layout") }
                DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                    MenuLabel("Fit")
                    PageFit.entries.forEach { fit ->
                        DropdownMenuItem(
                            text = { Text(fit.label) },
                            trailingIcon = { if (fit == layout.fit) Icon(Icons.Outlined.Check, contentDescription = "Chosen", Modifier.size(18.dp)) },
                            onClick = { layoutMenu = false; onLayout(layout.copy(fit = fit)) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Crop the borders") },
                        trailingIcon = { if (layout.crop) Icon(Icons.Outlined.Check, contentDescription = "On", Modifier.size(18.dp)) },
                        onClick = { layoutMenu = false; onLayout(layout.copy(crop = !layout.crop)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Two pages on a wide screen") },
                        trailingIcon = { if (layout.spreads) Icon(Icons.Outlined.Check, contentDescription = "On", Modifier.size(18.dp)) },
                        onClick = { layoutMenu = false; onLayout(layout.copy(spreads = !layout.spreads)) },
                    )
                    if (layout.spreads) {
                        DropdownMenuItem(
                            text = { Text("Move the pairs by one page") },
                            trailingIcon = { if (layout.shift) Icon(Icons.Outlined.Check, contentDescription = "On", Modifier.size(18.dp)) },
                            onClick = { layoutMenu = false; onLayout(layout.copy(shift = !layout.shift)) },
                        )
                    }
                    HorizontalDivider()
                    MenuLabel("Taps at the edges")
                    TapZones.entries.forEach { taps ->
                        DropdownMenuItem(
                            text = { Text(taps.label) },
                            trailingIcon = { if (taps == layout.taps) Icon(Icons.Outlined.Check, contentDescription = "Chosen", Modifier.size(18.dp)) },
                            onClick = { layoutMenu = false; onLayout(layout.copy(taps = taps)) },
                        )
                    }
                }
            }
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
            Text(pageLabel, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        }
    }
}
