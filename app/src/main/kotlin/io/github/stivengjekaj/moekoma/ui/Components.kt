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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
