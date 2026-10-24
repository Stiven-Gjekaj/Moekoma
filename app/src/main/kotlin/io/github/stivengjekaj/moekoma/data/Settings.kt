package io.github.stivengjekaj.moekoma.data

import android.content.Context
import io.github.stivengjekaj.moekoma.core.ReadListOrder
import io.github.stivengjekaj.moekoma.ui.theme.Appearance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The choices of the user that are not a part of the library. */
data class Preferences(
    val appearance: Appearance = Appearance.System,
    /** On an e-ink screen, a black frame clears the ghost image after this number of pages. 0 is off. */
    val refreshEvery: Int = 0,
    /** The volume keys turn the page in the readers. */
    val volumeKeys: Boolean = true,
    /** Volume down goes back, and volume up goes forward. */
    val volumeReversed: Boolean = false,
    /** The screen does not go dark while a book is open. */
    val keepScreenOn: Boolean = true,
    /** The order of the read list. */
    val readListOrder: ReadListOrder = ReadListOrder.Added,
    /** The look of the text of a novel. */
    val text: TextStyle = TextStyle(),
    /** The app asked one time if it can use the E-ink look on this device. */
    val askedEInk: Boolean = false,
    /** The sources for adults install and show. */
    val adult: Boolean = false,
    /** The books of the read list check for new chapters in the background. */
    val backgroundChecks: Boolean = false,
    /** The new chapters download with no question only on Wi-Fi. */
    val wifiOnly: Boolean = false,
)

/** Keeps the preferences in the shared preferences of Android. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val preferences: StateFlow<Preferences> = state.asStateFlow()

    private fun read() = Preferences(
        appearance = prefs.getString("appearance", null)?.let { name -> Appearance.entries.firstOrNull { it.name == name } } ?: Appearance.System,
        refreshEvery = prefs.getInt("refreshEvery", 0),
        volumeKeys = prefs.getBoolean("volumeKeys", true),
        volumeReversed = prefs.getBoolean("volumeReversed", false),
        askedEInk = prefs.getBoolean("askedEInk", false),
        adult = prefs.getBoolean("adult", false),
        backgroundChecks = prefs.getBoolean("backgroundChecks", false),
        wifiOnly = prefs.getBoolean("wifiOnly", false),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        readListOrder = prefs.getString("readListOrder", null)?.let { name -> ReadListOrder.entries.firstOrNull { it.name == name } } ?: ReadListOrder.Added,
        text = TextStyle(
            family = prefs.getString("text.family", null)?.let { name -> TextStyle.Family.entries.firstOrNull { it.name == name } } ?: TextStyle.Family.Serif,
            size = prefs.getInt("text.size", 19),
            lineHeight = prefs.getFloat("text.lineHeight", 1.6f),
            margin = prefs.getInt("text.margin", 22),
            justify = prefs.getBoolean("text.justify", false),
        ),
    )

    fun update(change: (Preferences) -> Preferences) {
        val next = change(state.value)
        prefs.edit()
            .putString("appearance", next.appearance.name)
            .putInt("refreshEvery", next.refreshEvery)
            .putBoolean("volumeKeys", next.volumeKeys)
            .putBoolean("volumeReversed", next.volumeReversed)
            .putBoolean("askedEInk", next.askedEInk)
            .putBoolean("adult", next.adult)
            .putBoolean("backgroundChecks", next.backgroundChecks)
            .putBoolean("wifiOnly", next.wifiOnly)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putString("readListOrder", next.readListOrder.name)
            .putString("text.family", next.text.family.name)
            .putInt("text.size", next.text.size)
            .putFloat("text.lineHeight", next.text.lineHeight)
            .putInt("text.margin", next.text.margin)
            .putBoolean("text.justify", next.text.justify)
            .apply()
        state.value = next
    }
}

/** The look of the text of a novel, which the user chooses in the reader. */
data class TextStyle(
    val family: Family = Family.Serif,
    val size: Int = 19,
    val lineHeight: Float = 1.6f,
    val margin: Int = 22,
    val justify: Boolean = false,
) {
    enum class Family(val label: String) { Serif("Serif"), Sans("Sans serif"), Karla("Karla") }

    /**
     * The CSS that goes over the CSS of the book. The colours come from the palette of the app.
     * The top and the bottom leave space for the bars of the reader.
     */
    fun css(background: String, text: String, link: String, eInk: Boolean): String {
        val font = when (family) {
            Family.Serif -> "'Noto Serif', serif"
            Family.Sans -> "'Roboto', sans-serif"
            Family.Karla -> "'Karla', sans-serif"
        }
        val karla = if (family == Family.Karla) "@font-face { font-family: 'Karla'; src: url('/__app/karla.ttf') format('truetype'); font-weight: 200 800; }" else ""
        return """
            $karla
            html { background: $background !important; -webkit-text-size-adjust: none; }
            body {
              background: $background !important; color: $text !important;
              font-family: $font !important; font-size: ${size}px !important;
              line-height: $lineHeight !important; text-align: ${if (justify) "justify" else "start"} !important;
              margin: 0 auto !important; padding: 96px ${margin}px 120px !important; max-width: 40em !important;
              hyphens: auto; overflow-wrap: break-word;
            }
            body * { color: inherit !important; background-color: transparent !important; font-family: inherit !important; line-height: inherit !important; }
            p { margin: 0 0 0.9em 0 !important; }
            h1, h2, h3 { line-height: 1.3 !important; text-align: start !important; }
            img, svg, video { max-width: 100% !important; height: auto !important; }
            a { color: $link !important; text-decoration: ${if (eInk) "underline" else "none"} !important; }
        """.trimIndent()
    }
}
