package io.github.stivengjekaj.moekoma

import android.view.KeyEvent

/**
 * The volume keys turn the page while a reader is open. The reader sets [handler], and the
 * activity gives each key to it. Many e-ink readers have page keys that send these codes too.
 */
object VolumeKeys {
    /** Gets true for the next page and false for the previous page. */
    var handler: ((forward: Boolean) -> Unit)? = null

    /** The keys of a page that some readers send, as well as the volume keys. */
    private val forwardKeys = setOf(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
    private val backKeys = setOf(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT)

    /** Returns true when the key turned a page, so that the volume does not change. */
    fun handle(event: KeyEvent, volumeKeys: Boolean, reversed: Boolean): Boolean {
        val handler = handler ?: return false
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeys) !reversed else return false
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeys) reversed else return false
            in forwardKeys -> true
            in backKeys -> false
            else -> return false
        }
        // The page turns when the key goes down. The key up must not change the volume.
        if (event.action == KeyEvent.ACTION_DOWN) handler(forward)
        return true
    }
}
