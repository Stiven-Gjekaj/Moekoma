package io.github.stivengjekaj.moekoma

import android.app.Application
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The client of the Mihon extensions passes the browser check of the test server in a web view with no window.
 * It needs scripts/test-server.py on port 8765 of the computer, so it runs only with the argument server=1:
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.server=1
 */
@RunWith(AndroidJUnit4::class)
class BrowserCheckTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun passesTheCheckInAWebViewWithNoWindow() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        // The test starts with no cookie of a passed check.
        val cleared = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CookieManager.getInstance().removeAllCookies { cleared.countDown() } }
        cleared.await(5, TimeUnit.SECONDS)
        val reported = mutableListOf<String>()
        val network = NetworkHelper(app, { BrowserChecks.userAgent(app) }) { reported += it }
        network.client.newCall(GET("http://10.0.2.2:8765/cfm/pages/0")).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("image/png", response.header("Content-Type"))
        }
        assertTrue("A check that passed with no window does not ask the user.", reported.isEmpty())
        assertTrue(BrowserChecks.clearance("http://10.0.2.2:8765/") != null)
    }

    /** A check that needs a tap does not pass with no window. The app gets the site, to show the banner. */
    @Test fun givesACheckThatNeedsTheUserToTheApp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        val cleared = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CookieManager.getInstance().removeAllCookies { cleared.countDown() } }
        cleared.await(5, TimeUnit.SECONDS)
        val reported = mutableListOf<String>()
        val network = NetworkHelper(app, { BrowserChecks.userAgent(app) }) { reported += it }
        val fault = runCatching { network.client.newCall(GET("http://10.0.2.2:8765/cfi/pages/0")).execute().close() }.exceptionOrNull()
        assertTrue(fault is java.io.IOException)
        assertTrue(fault!!.message.orEmpty().contains("banner"))
        assertEquals(listOf("http://10.0.2.2:8765/cfi/pages/0"), reported)
    }
}
