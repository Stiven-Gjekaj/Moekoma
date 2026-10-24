package io.github.stivengjekaj.moekoma.data

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * A client that keeps the app alive when the render process of its web view stops, for example when the device
 * has too little memory. Without it, Android stops the app with the renderer. The web view cannot be used again,
 * so the client removes it from the screen and destroys it.
 */
// Lint does not see the override below.
@SuppressLint("MissingOnRenderProcessGone")
open class SafeWebViewClient : WebViewClient() {
    /** True after the render process of the web view stopped. */
    @Volatile var gone = false
        private set

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
        gone = true
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        return true
    }
}
