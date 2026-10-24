package io.github.stivengjekaj.moekoma.ui

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.core.Translation

/** The words of the app in the language of the device. The app has English and Japanese. */
object Words {
    @Volatile private var japanese: Translation? = null

    /** The table of the language of the screen, or null for English. [MoekomaRoot] sets it. */
    @Volatile private var active: Translation? = null

    private fun japanese(context: Context): Translation = japanese ?: synchronized(this) {
        japanese ?: Translation.parse(context.resources.openRawResource(R.raw.ja).bufferedReader().use { it.readText() })
            .also { japanese = it }
    }

    /** The words of [text] in the language of [context]. A text that is not in the table stays as it is. */
    fun of(context: Context, text: String): String =
        if (context.resources.configuration.locales[0].language == "ja") japanese(context).of(text) else text

    /** Sets the language of the screen, and gives its table. */
    fun use(context: Context, language: String): Translation? =
        (if (language == "ja") japanese(context) else null).also { active = it }

    /** True when the screen shows English. */
    val english: Boolean get() = active == null

    fun tr(text: String): String = active?.of(text) ?: text
}

/** The words of [text] in the language of the screen. A text that is not in the table stays as it is. */
fun tr(text: String): String = Words.tr(text)

/** The Text of Material 3, with the words in the language of the screen. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = remember(text, Words.english) { tr(text) },
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    onTextLayout = onTextLayout,
    style = style,
)
