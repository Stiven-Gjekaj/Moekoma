package io.github.stivengjekaj.moekoma.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.ui.theme.Corner
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette

/**
 * A banner at the bottom of the app when a site asks for a check that needs the user. It is a banner, and not a
 * screen, so that the app does not open a page that the user did not ask for. As in Asabunko.
 */
@Composable
fun BrowserCheckBanner(url: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(Corner)
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).background(palette.surfaceHigh, shape)
            .border(palette.outlineWidth, palette.outline, shape).padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = palette.accent)
        Column(Modifier.weight(1f)) {
            Text("${BrowserChecks.host(url) ?: "A site"} asks for a check", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Open the page and pass the check once.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
        PillButton("Open", onOpen)
        IconButton(onClick = BrowserChecks::dismiss) { Icon(Icons.Outlined.Close, contentDescription = tr("Close the check")) }
    }
}

/**
 * The page of a site in a web view, with the user agent of the network clients. When the cookie of the check comes,
 * the screen closes. The web view and the network clients share the cookie store, so the sources have the cookie.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserCheckScreen(url: String, onClose: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    var title by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    val host = BrowserChecks.host(url) ?: "Check"
    // An old cookie of the site was refused, so only a new cookie marks a passed check.
    val before = remember(url) { BrowserChecks.clearance(url) }
    val passed = { BrowserChecks.clearance(url).let { it != null && it != before } }

    fun finish(passed: Boolean) {
        if (done) return
        done = true
        CookieManager.getInstance().flush()
        onClose(passed)
    }

    BackHandler { finish(false) }
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            TextButton(onClick = { finish(false) }) { Text("Cancel", color = palette.accent) }
            Text(
                title.ifEmpty { host }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = { finish(passed()) }) { Text("Done", color = palette.accent) }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = BrowserChecks.userAgent(context)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (passed()) finish(true)
                        }

                        // The check can end after some loads of the page, so each load looks for the cookie.
                        override fun onPageFinished(view: WebView, page: String) {
                            title = view.title.orEmpty()
                            if (passed()) finish(true)
                        }
                    }
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
