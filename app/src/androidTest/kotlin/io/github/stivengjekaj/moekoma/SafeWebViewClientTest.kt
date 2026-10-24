package io.github.stivengjekaj.moekoma

import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.data.SafeWebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A web view whose render process stops does not stop the app. */
@RunWith(AndroidJUnit4::class)
class SafeWebViewClientTest {
    @Test fun keepsTheAppAliveWhenTheRendererStops() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val client = SafeWebViewClient()
        lateinit var frame: FrameLayout
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            frame = FrameLayout(context)
            val view = WebView(context)
            view.webViewClient = client
            frame.addView(view)
            // The web view stops its render process when it loads this address.
            view.loadUrl("chrome://crash")
        }
        val until = System.currentTimeMillis() + 15_000
        while (!client.gone && System.currentTimeMillis() < until) Thread.sleep(100)
        assertTrue("The render process did not stop", client.gone)
        instrumentation.runOnMainSync { assertEquals(0, frame.childCount) }
    }
}
