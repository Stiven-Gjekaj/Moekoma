package io.github.stivengjekaj.moekoma.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.stivengjekaj.moekoma.core.FormInput
import io.github.stivengjekaj.moekoma.core.HttpRequest
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.JsException
import io.github.stivengjekaj.moekoma.core.JsRuntime
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.PaperbackExtension
import io.github.stivengjekaj.moekoma.core.SourceFormException
import io.github.stivengjekaj.moekoma.core.SourceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** A Paperback 0.9 extension as an [OnlineSource]. The pages and the covers get the headers of its interceptors. */
class PaperbackSource(private val installed: InstalledSource, val extension: PaperbackExtension, private val client: OkHttpClient) : OnlineSource {
    override val info: SourceInfo get() = installed.info
    override val canShowLatest: Boolean get() = extension.canShowLatest
    override suspend fun popular(page: Int) = extension.popular(page)
    override suspend fun latest(page: Int) = extension.latest(page)
    override suspend fun search(query: String, page: Int) = extension.search(query, page)
    override suspend fun details(series: OnlineSeries) = extension.details(series)
    override suspend fun filterOptions() = extension.filterOptions()
    override suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>) = extension.browse(page, query, filters)
    override suspend fun hasSettingsForm() = extension.hasSettingsForm()
    override suspend fun settingsForm() = extension.settingsForm() ?: throw SourceFormException.noForm()
    override suspend fun form(id: String) = extension.form(id)
    override suspend fun formAction(form: String, row: String, input: FormInput) = extension.formAction(form, row, input)
    override suspend fun submitForm(id: String) = extension.submitForm(id)
    override fun close() = extension.close()

    override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent {
        val (html, pages) = extension.content(chapter, series)
        if (html != null) return ChapterContent.Html(html)
        return ChapterContent.Pages(pages.map { RemoteImage(installed.id, it, "${installed.id}|${it.url}") })
    }

    override suspend fun image(image: RemoteImage): Response = withContext(Dispatchers.IO) {
        val request = image.item as? HttpRequest ?: (image.item as? String)?.let(extension::imageRequest) ?: throw IOException("The image is not of this source.")
        val response = client.newCall(Request.Builder().url(request.url).headers(request.headers.toHeaders()).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("The image did not load (HTTP ${response.code}).")
        }
        response
    }
}

/**
 * `Application.executeInWebView` of Paperback, as in Asabunko: a page of HTML in a web view with no window, with the
 * cookies of the extension. When the page has loaded, the script of the extension runs as the body of an async
 * function, and its result goes back with the cookies of the site.
 */
class PaperbackWebView(private val context: Context) {
    /** The time for the load of the page, and then for the script to give a result that is not empty. */
    private val loadTime = 20_000L
    private val resultTime = 15_000L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(json: String): String {
        val input = JSONObject(json)
        val base = input.getString("baseUrl")
        val host = runCatching { java.net.URI(base).host }.getOrNull() ?: throw JsException("The base URL of the web view is not correct.")
        return withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            val cookies = input.optJSONArray("cookies") ?: JSONArray()
            for (index in 0 until cookies.length()) {
                val cookie = cookies.getJSONObject(index)
                manager.setCookie(base, "${cookie.getString("name")}=${cookie.getString("value")}; Path=${cookie.optString("path", "/")}")
            }
            val loaded = CompletableDeferred<Unit>()
            val answers = mutableMapOf<String, CompletableDeferred<String?>>()
            val view = WebView(context)
            try {
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.userAgentString = input.optString("userAgent").takeIf { it.isNotEmpty() && it != "null" } ?: BrowserChecks.userAgent(context)
                view.addJavascriptInterface(object {
                    @JavascriptInterface fun done(id: String, value: String?) { answers[id]?.complete(value) }
                }, "__moekomaPaperback")
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(page: WebView, url: String) { loaded.complete(Unit) }
                }
                view.loadDataWithBaseURL(base, input.getString("html"), "text/html", "utf-8", null)
                withTimeoutOrNull(loadTime) { loaded.await() } ?: throw JsException("The page of the web view did not load.")
                // A page can set its value some time after the load, so an empty result asks again until the time ends.
                val inject = input.getString("inject")
                var result: String? = null
                val end = System.currentTimeMillis() + resultTime
                while (result == null && System.currentTimeMillis() < end) {
                    val id = UUID.randomUUID().toString()
                    val answer = CompletableDeferred<String?>().also { answers[id] = it }
                    view.evaluateJavascript(
                        "(async () => { try { const v = await (async () => { $inject })(); __moekomaPaperback.done(${JsRuntime.quote(id)}, " +
                            "v === undefined || v === null ? null : JSON.stringify(v)); } catch (e) { __moekomaPaperback.done(${JsRuntime.quote(id)}, null); } })();",
                        null,
                    )
                    result = withTimeoutOrNull(5_000) { answer.await() }
                    if (result == null) delay(300)
                }
                val found = JSONArray()
                manager.getCookie(base)?.split(';')?.filter { '=' in it }?.forEach { part ->
                    found.put(JSONObject().put("name", part.substringBefore('=').trim()).put("value", part.substringAfter('=').trim()).put("domain", host).put("path", "/"))
                }
                JSONObject().put("result", result?.let { org.json.JSONTokener(it).nextValue() } ?: JSONObject.NULL).put("cookies", found).toString()
            } finally {
                view.destroy()
            }
        }
    }
}
