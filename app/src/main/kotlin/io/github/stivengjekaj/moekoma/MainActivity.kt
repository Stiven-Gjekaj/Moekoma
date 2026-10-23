package io.github.stivengjekaj.moekoma

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot

class MainActivity : ComponentActivity() {
    private val container get() = (application as MoekomaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MoekomaRoot(container) }
        if (savedInstanceState == null) importShared(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importShared(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = pageKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = pageKey(event) || super.onKeyUp(keyCode, event)

    private fun pageKey(event: KeyEvent): Boolean {
        val prefs = container.settings.preferences.value
        return VolumeKeys.handle(event, prefs.volumeKeys, prefs.volumeReversed)
    }

    /** "Open with Moekoma" and "Share to Moekoma" give the files of another app. */
    private fun importShared(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(extraStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> extraStreams(intent)
            else -> emptyList()
        }
        container.model.importDocuments(uris)
    }

    @Suppress("DEPRECATION")
    private fun extraStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun extraStreams(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}
