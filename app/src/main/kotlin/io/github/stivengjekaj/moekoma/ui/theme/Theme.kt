package io.github.stivengjekaj.moekoma.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stivengjekaj.moekoma.R

/** The look that the user chooses in Settings. */
enum class Appearance(val label: String) {
    System("Device"),
    Dark("Warm dark"),
    Paper("Paper"),
    EInk("E-ink"),
}

/**
 * The palette of Moekoma. It is the palette of Asabunko, with the green of the logo in place of the red.
 * Each colour has a dark form, warm charcoal, and a light form, paper. The E-ink form is black on white.
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val danger: Color,
    /** The space around a page in the reader. It is darker than the background, so that the page stands out. */
    val readerBackground: Color,
    val isDark: Boolean,
    /** An e-ink screen: no animation, no shadow, and thick outlines. */
    val isEInk: Boolean = false,
) {
    /** The width of an outline. An e-ink screen shows a thin grey line badly. */
    val outlineWidth get() = if (isEInk) 1.5.dp else 0.5.dp

    companion object {
        val Dark = Palette(
            background = Color(0xFF17130F), surface = Color(0xFF211C17), surfaceHigh = Color(0xFF2C251F),
            outline = Color(0xFF3A3129), accent = Color(0xFF3DD68C), onAccent = Color(0xFF0A1E12),
            textPrimary = Color(0xFFF3EADF), textSecondary = Color(0xFFA89B8C), danger = Color(0xFFE5746A),
            readerBackground = Color(0xFF0F0C0A), isDark = true,
        )
        val Paper = Palette(
            background = Color(0xFFF6F0E4), surface = Color(0xFFFBF7EE), surfaceHigh = Color(0xFFEFE6D6),
            outline = Color(0xFFE2D6C2), accent = Color(0xFF1A6B43), onAccent = Color(0xFFFFF8EE),
            textPrimary = Color(0xFF2B231C), textSecondary = Color(0xFF6A5C4E), danger = Color(0xFFB23A30),
            readerBackground = Color(0xFFEDE5D6), isDark = false,
        )
        /** Black on white, with no grey for the text and no colour. */
        val EInk = Palette(
            background = Color.White, surface = Color.White, surfaceHigh = Color.White,
            outline = Color.Black, accent = Color.Black, onAccent = Color.White,
            textPrimary = Color.Black, textSecondary = Color.Black, danger = Color.Black,
            readerBackground = Color.White, isDark = false, isEInk = true,
        )
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.Dark }

/** The corner radius of every card, button, and cover. */
val Corner = 14.dp

/** The space at the left and the right of the content of a screen. */
val Gutter = 18.dp

/** Karla, the font of ZettaStream and Asabunko, under the SIL Open Font License. One variable file gives each weight. */
val Karla = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(R.font.karla, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private fun karla(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Karla, fontSize = size.sp, fontWeight = weight)

private val typography = Typography(
    displaySmall = karla(32, FontWeight.Bold),
    headlineSmall = karla(24, FontWeight.Bold),
    titleLarge = karla(20, FontWeight.Bold),
    titleMedium = karla(17, FontWeight.Bold),
    titleSmall = karla(15, FontWeight.Bold),
    bodyLarge = karla(16),
    bodyMedium = karla(15),
    bodySmall = karla(13),
    labelLarge = karla(15, FontWeight.Bold),
    labelMedium = karla(13, FontWeight.Medium),
    labelSmall = karla(12, FontWeight.Medium),
)

@Composable
fun paletteFor(appearance: Appearance): Palette = when (appearance) {
    Appearance.System -> if (isSystemInDarkTheme()) Palette.Dark else Palette.Paper
    Appearance.Dark -> Palette.Dark
    Appearance.Paper -> Palette.Paper
    Appearance.EInk -> Palette.EInk
}

private fun Palette.scheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent, secondary = accent, onSecondary = onAccent,
        background = background, onBackground = textPrimary, surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceHigh, onSurfaceVariant = textSecondary, surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh, surfaceContainerLow = surface,
        surfaceContainerLowest = background, outline = outline, outlineVariant = outline,
        error = danger, secondaryContainer = if (isEInk) Color.Black else surfaceHigh,
        onSecondaryContainer = if (isEInk) Color.White else accent, primaryContainer = surfaceHigh,
    )
}

@Composable
fun MoekomaTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val palette = paletteFor(appearance)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.scheme(), typography = typography, content = content)
    }
}
