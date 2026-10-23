package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.stivengjekaj.moekoma.ui.theme.Corner
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.io.File

/** The cover of a book, with the shape of a paperback and a soft shadow. An e-ink screen gets no shadow. */
@Composable
fun CoverView(file: File, title: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(Corner - 4.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .then(if (palette.isEInk) Modifier else Modifier.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f)))
            .clip(shape)
            .background(palette.surfaceHigh)
            .border(palette.outlineWidth, palette.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary,
            textAlign = TextAlign.Center, maxLines = 5, modifier = Modifier.padding(10.dp),
        )
        if (file.exists()) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** A thin bar that shows the part of a book that the user read. */
@Composable
fun ProgressLine(value: Double, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val part = value.toFloat().coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(if (palette.isEInk) 4.dp else 3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (palette.isEInk) Color.White else palette.outline)
            .then(if (palette.isEInk) Modifier.border(1.dp, Color.Black, RoundedCornerShape(2.dp)) else Modifier)
            .semantics {
                contentDescription = "Progress"
                progressBarRangeInfo = ProgressBarRangeInfo(part, 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(part).background(palette.accent))
    }
}

/** A card with a soft fill and a thin outline. */
@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(Corner)
    Column(
        modifier.fillMaxWidth().clip(shape).background(palette.surface).border(palette.outlineWidth, palette.outline, shape).padding(padding),
        content = content,
    )
}

/** The main button: a soft pill in the accent colour. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A soft glow of the accent colour behind the art of a screen. An e-ink screen shows no glow. */
@Composable
fun AccentGlow(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    if (palette.isEInk) return
    Box(
        modifier.background(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(palette.accent.copy(alpha = 0.28f), palette.accent.copy(alpha = 0f)),
            ),
        ),
    )
}

/**
 * Three covers in a fan, on a glow. An empty screen shows it, so that it does not look bare.
 * With fewer than three covers, it shows the mark of the app. An e-ink screen does not move it.
 */
@Composable
fun CoverFan(covers: List<Any>) {
    val palette = LocalPalette.current
    val lift = if (palette.isEInk) 0f else {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "fan")
        val value by transition.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(3200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "lift",
        )
        value
    }
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        AccentGlow(Modifier.size(320.dp, 240.dp).blur(18.dp))
        if (covers.size >= 3) {
            for (index in listOf(0, 2, 1)) {
                val turn = (index - 1).toFloat()
                AsyncImage(
                    model = covers[index], contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (turn * (64 + 10 * lift)).dp, y = if (index == 1) (-4 - 6 * lift).dp else 10.dp)
                        .rotate(turn * (8 + 3 * lift))
                        .size(width = if (index == 1) 104.dp else 88.dp, height = if (index == 1) 156.dp else 132.dp)
                        .clip(RoundedCornerShape(Corner - 4.dp))
                        .background(palette.surfaceHigh),
                )
            }
        } else {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(io.github.stivengjekaj.moekoma.R.drawable.mark), contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .then(if (palette.isEInk) Modifier else Modifier.shadow(18.dp, RoundedCornerShape(27.dp), ambientColor = palette.accent.copy(alpha = 0.3f), spotColor = palette.accent.copy(alpha = 0.3f)))
                    .clip(RoundedCornerShape(27.dp)),
            )
        }
    }
}

/** The art and a message of an empty screen. */
@Composable
fun EmptyArt(title: String, text: String, covers: List<Any> = emptyList(), actions: @Composable () -> Unit = {}) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        CoverFan(covers)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, textAlign = TextAlign.Center)
        Box(Modifier.padding(top = 4.dp)) { actions() }
    }
}
