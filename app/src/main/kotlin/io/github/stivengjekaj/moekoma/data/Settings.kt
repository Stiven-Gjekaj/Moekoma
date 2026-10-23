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
    /** The app asked one time if it can use the E-ink look on this device. */
    val askedEInk: Boolean = false,
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
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        readListOrder = prefs.getString("readListOrder", null)?.let { name -> ReadListOrder.entries.firstOrNull { it.name == name } } ?: ReadListOrder.Added,
    )

    fun update(change: (Preferences) -> Preferences) {
        val next = change(state.value)
        prefs.edit()
            .putString("appearance", next.appearance.name)
            .putInt("refreshEvery", next.refreshEvery)
            .putBoolean("volumeKeys", next.volumeKeys)
            .putBoolean("volumeReversed", next.volumeReversed)
            .putBoolean("askedEInk", next.askedEInk)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putString("readListOrder", next.readListOrder.name)
            .apply()
        state.value = next
    }
}
