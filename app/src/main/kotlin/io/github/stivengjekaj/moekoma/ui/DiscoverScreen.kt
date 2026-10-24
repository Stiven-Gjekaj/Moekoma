package io.github.stivengjekaj.moekoma.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.data.DiscoverFeed
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/** The titles to find: a banner of popular titles, and rows of the popular and the latest titles of the sources. */
@Composable
fun DiscoverScreen(container: AppContainer, openTitle: (TitleRoute) -> Unit, openSources: () -> Unit) {
    val palette = LocalPalette.current
    val feed = container.discover
    val library by container.model.library.collectAsState()
    val rows by feed.rows.collectAsState()
    val featured by feed.featured.collectAsState()
    val loading by feed.loading.collectAsState()
    LaunchedEffect(library.installed.map { it.id }) { feed.load() }
    // A passed browser check asks the sources again.
    val passes by BrowserChecks.passes.collectAsState()
    LaunchedEffect(passes) { if (passes > 0) feed.load(force = true) }

    fun open(item: DiscoverFeed.Item) = openTitle(TitleRoute(listOf(item.hit), findOthers = true))

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
        if (rows.isEmpty()) {
            item {
                Column(Modifier.statusBarsPadding().padding(horizontal = Gutter, vertical = 12.dp).semantics(mergeDescendants = true) { heading() }) {
                    Text("Something new to read", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    Text("Discover", style = MaterialTheme.typography.displaySmall)
                }
            }
            item {
                when {
                    library.installed.isEmpty() -> EmptyArt(
                        "Nothing to discover yet",
                        "Add a source list in Settings, Sources. Moekoma installs its sources, and their popular titles show here.",
                    ) { PillButton("Add sources", openSources) }
                    loading -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyArt("Asking your sources", "The popular titles of each source come in a moment.")
                        CircularProgressIndicator(color = palette.accent)
                    }
                    else -> EmptyArt("No source answered", "Check the network, then try again.") { PillButton("Try again", { feed.load(force = true) }) }
                }
            }
        } else {
            item { DiscoverHero(featured, ::open) }
            items(rows, key = { it.id }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Gutter).semantics { heading() })
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(row.items, key = { it.id }) { item ->
                            TitleTile(item.hit.series.title, item.sourceName, item.cover, Modifier.width(112.dp)) { open(item) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The large banner at the top of Discover, after the home banner of ZettaStream: the blurred cover as the art,
 * the cover, the facts, a short summary, and a button. The banner moves to the next title after a time.
 */
@Composable
private fun DiscoverHero(items: List<DiscoverFeed.Featured>, open: (DiscoverFeed.Item) -> Unit) {
    if (items.isEmpty()) return
    val palette = LocalPalette.current
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items.map { it.item.id }) {
        index = 0
        // The E-ink look does not move the banner, because each change of the screen flashes.
        if (items.size < 2 || palette.isEInk) return@LaunchedEffect
        while (true) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }
    val shown = items[index % items.size]
    Box(
        Modifier.fillMaxWidth().height(430.dp).pointerInput(items.size) {
            var moved = 0f
            detectHorizontalDragGestures(onDragStart = { moved = 0f }, onDragEnd = {
                if (items.size > 1 && kotlin.math.abs(moved) > 60) index = (index + if (moved < 0) 1 else items.size - 1) % items.size
            }) { _, amount -> moved += amount }
        },
    ) {
        Crossfade(shown.item.cover, label = "art") { cover ->
            if (!palette.isEInk && cover != null) {
                val request = remember(cover) { ImageRequest.Builder(context).data(cover).size(600).build() }
                AsyncImage(request, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(24.dp))
            }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to palette.background.copy(alpha = 0.15f), 1f to palette.background)))
        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("POPULAR NOW", style = MaterialTheme.typography.labelMedium, color = palette.accent, letterSpacing = 1.5.sp)
                    Text(
                        shown.details?.series?.title ?: shown.item.hit.series.title, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxLines = 2) {
                        (listOf(shown.item.sourceName) + shown.details?.genres.orEmpty().take(3)).forEach { fact ->
                            Text(
                                fact, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clip(CircleShape).background(palette.surfaceHigh).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    shown.details?.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    PillButton("Read", { open(shown.item) })
                }
                SourceCover(shown.item.cover, shown.item.hit.series.title, Modifier.width(112.dp))
            }
            // One mark for each title. The mark of the title on screen is long.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)) {
                items.indices.forEach { i ->
                    val current = i == index % items.size
                    Spacer(Modifier.size(width = if (current) 26.dp else 10.dp, height = 4.dp).clip(CircleShape).background(if (current) palette.textPrimary else palette.outline))
                }
            }
        }
    }
}
