package io.github.stivengjekaj.moekoma.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import io.github.stivengjekaj.moekoma.core.BrowserCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

/**
 * The sites that asked for a check in a browser, such as the challenge of Cloudflare. A web view with no window
 * passes most checks. A check that needs the user comes here, and the app offers to open the site. As in Asabunko.
 */
object BrowserChecks {
    private val pendingState = MutableStateFlow<String?>(null)
    /** The page of the last site that asked for a check, until the user opens it or closes the banner. */
    val pending: StateFlow<String?> = pendingState.asStateFlow()

    private val passesState = MutableStateFlow(0)
    /** The number of checks that the user passed. A screen with a fault loads again when it changes. */
    val passes: StateFlow<Int> = passesState.asStateFlow()

    /** The hosts that the user passed in this session, so that a late answer of the same host does not ask again. */
    private val passed = mutableSetOf<String>()

    @Synchronized fun report(url: String) {
        val host = host(url) ?: return
        if (host in passed || host(pendingState.value) == host) return
        pendingState.value = url
    }

    @Synchronized fun markPassed(host: String) {
        passed += host
        passesState.value++
        if (host(pendingState.value) == host) pendingState.value = null
    }

    fun dismiss() {
        pendingState.value = null
    }

    /** The cookie of a passed check for the URL in the cookie store of the web view, or null. */
    fun clearance(url: String): String? {
        val host = host(url) ?: return null
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty().split(';').map { it.trim() }
        // The store gives only the cookies of this site, so each cookie has the host as its domain.
        return cookies.firstOrNull { cookie -> BrowserCheck.passed(listOf(cookie.substringBefore('=') to host), host) }?.substringAfter('=')
    }

    fun host(url: String?): String? = url?.let { runCatching { URI(it).host }.getOrNull() }

    /**
     * The user agent of the web view, without the mark of a web view. A check counts only for the user agent that
     * passed it, so the network clients, the web view with no window, and the visible web view all send this one.
     */
    @Volatile private var agent: String? = null

    fun userAgent(context: Context): String = agent ?: runCatching {
        WebSettings.getDefaultUserAgent(context).replace("; wv)", ")")
    }.getOrElse {
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    }.also { agent = it }

    /** Reports a check in an answer of the network client of the app, such as an OPDS catalog behind Cloudflare. */
    class Finder : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.code in setOf(403, 429, 503) || response.header("cf-mitigated") != null) {
                val headers = response.headers.associate { (name, value) -> name to value }
                if (BrowserCheck.isChallenge(response.code, headers, response.peekBody(16_384).bytes())) report(chain.request().url.toString())
            }
            return response
        }
    }
}
