package io.github.stivengjekaj.moekoma.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.stivengjekaj.moekoma.core.AidokuBitmap
import io.github.stivengjekaj.moekoma.core.AidokuCanvas
import io.github.stivengjekaj.moekoma.core.AidokuColor
import io.github.stivengjekaj.moekoma.core.AidokuContent
import io.github.stivengjekaj.moekoma.core.AidokuCookie
import io.github.stivengjekaj.moekoma.core.AidokuPage
import io.github.stivengjekaj.moekoma.core.AidokuPathOp
import io.github.stivengjekaj.moekoma.core.AidokuPlatform
import io.github.stivengjekaj.moekoma.core.AidokuRect
import io.github.stivengjekaj.moekoma.core.AidokuSource
import io.github.stivengjekaj.moekoma.core.AidokuStroke
import io.github.stivengjekaj.moekoma.core.AidokuWebView
import io.github.stivengjekaj.moekoma.core.FormInput
import io.github.stivengjekaj.moekoma.core.HttpRequest
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.JsRuntime
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** An Aidoku source as an [OnlineSource]. A page that the source drew goes to a file in the cache. */
class AidokuOnlineSource(
    private val installed: InstalledSource,
    val source: AidokuSource,
    private val client: OkHttpClient,
    private val drawn: File,
) : OnlineSource {
    override val info: SourceInfo get() = installed.info
    override val canShowLatest: Boolean get() = source.canShowLatest
    override suspend fun popular(page: Int) = source.popular(page)
    override suspend fun latest(page: Int) = source.latest(page)
    override suspend fun search(query: String, page: Int) = source.search(query, page)
    override suspend fun details(series: OnlineSeries) = source.details(series)
    override suspend fun filterOptions() = source.filterOptions()
    override suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>) = source.browse(page, query, filters)
    override suspend fun settingOptions() = source.settingOptions()
    override suspend fun settingValues() = source.settingValues()
    override suspend fun setSetting(id: String, value: OptionValue) = source.setSetting(id, value)
    override suspend fun hasSettingsForm() = source.hasSettingsForm
    override suspend fun form(id: String) = source.form(id)
    override suspend fun formAction(form: String, row: String, input: FormInput) = source.formAction(form, row, input)
    override fun close() = source.close()

    override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent = when (val content = source.content(chapter, series)) {
        is AidokuContent.Html -> ChapterContent.Html(content.html)
        is AidokuContent.Pages -> withContext(Dispatchers.IO) {
            drawn.mkdirs()
            val stamp = UUID.randomUUID()
            ChapterContent.Pages(content.pages.mapIndexed { index, page ->
                when (page) {
                    is AidokuPage.Remote -> RemoteImage(installed.id, page.request, "${installed.id}|${page.request.url}")
                    is AidokuPage.Drawn -> {
                        val file = File(drawn, "$stamp-$index.png").apply { writeBytes(page.png) }
                        RemoteImage(installed.id, file, "${installed.id}|${file.name}")
                    }
                }
            })
        }
    }

    override suspend fun image(image: RemoteImage): Response = withContext(Dispatchers.IO) {
        when (val item = image.item) {
            is File -> Response.Builder()
                .request(Request.Builder().url("https://moekoma.invalid/${item.name}").build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(item.readBytes().toResponseBody("image/png".toMediaType()))
                .build()
            else -> {
                val request = item as? HttpRequest ?: (item as? String)?.let(source::plainRequest) ?: throw IOException("The image is not of this source.")
                val body = request.body?.toRequestBody()
                val call = Request.Builder().url(request.url).headers(request.headers.toHeaders()).method(request.method, body).build()
                val response = client.newCall(call).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw IOException("The image did not load (HTTP ${response.code}).")
                }
                response
            }
        }
    }
}

/** The canvas and the web view of the Aidoku sources, with Android. As in Asabunko, which uses CoreGraphics and WebKit. */
class AndroidAidokuPlatform(private val context: Context) : AidokuPlatform {
    private class AndroidBitmap(val bitmap: Bitmap) : AidokuBitmap {
        override val width get() = bitmap.width
        override val height get() = bitmap.height
        override fun png(): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private class AndroidCanvas(width: Int, height: Int) : AidokuCanvas {
        private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(bitmap)

        override fun setTransform(translateX: Float, translateY: Float, scaleX: Float, scaleY: Float, rotate: Float) {
            canvas.setMatrix(Matrix().apply {
                preTranslate(translateX, translateY)
                preScale(scaleX, scaleY)
                preRotate(Math.toDegrees(rotate.toDouble()).toFloat())
            })
        }

        override fun draw(image: AidokuBitmap, source: AidokuRect?, target: AidokuRect): Boolean {
            val from = (image as AndroidBitmap).bitmap
            val part = source?.let { Rect(it.x.toInt(), it.y.toInt(), (it.x + it.width).toInt(), (it.y + it.height).toInt()) }
            canvas.drawBitmap(from, part, RectF(target.x, target.y, target.x + target.width, target.y + target.height), Paint(Paint.FILTER_BITMAP_FLAG))
            return true
        }

        private fun path(ops: List<AidokuPathOp>) = Path().apply {
            for (op in ops) when (op) {
                is AidokuPathOp.Move -> moveTo(op.x, op.y)
                is AidokuPathOp.Line -> lineTo(op.x, op.y)
                is AidokuPathOp.Quad -> quadTo(op.cx, op.cy, op.x, op.y)
                is AidokuPathOp.Cubic -> cubicTo(op.c1x, op.c1y, op.c2x, op.c2y, op.x, op.y)
                is AidokuPathOp.Arc -> arcTo(
                    RectF(op.x - op.radius, op.y - op.radius, op.x + op.radius, op.y + op.radius),
                    Math.toDegrees(op.start.toDouble()).toFloat(), Math.toDegrees(op.sweep.toDouble()).toFloat(),
                )
                AidokuPathOp.Close -> close()
            }
        }

        private fun color(color: AidokuColor) =
            android.graphics.Color.argb((color.alpha * 255).toInt().coerceIn(0, 255), color.red.toInt(), color.green.toInt(), color.blue.toInt())

        override fun fill(path: List<AidokuPathOp>, color: AidokuColor) {
            canvas.drawPath(path(path), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color(color); style = Paint.Style.FILL })
        }

        override fun stroke(path: List<AidokuPathOp>, style: AidokuStroke) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = color(style.color)
                this.style = Paint.Style.STROKE
                strokeWidth = style.width
                strokeCap = listOf(Paint.Cap.ROUND, Paint.Cap.SQUARE, Paint.Cap.BUTT)[style.cap.coerceIn(0, 2)]
                strokeJoin = listOf(Paint.Join.ROUND, Paint.Join.BEVEL, Paint.Join.MITER)[style.join.coerceIn(0, 2)]
                strokeMiter = style.miter
                if (style.dashes.size >= 2) pathEffect = DashPathEffect(style.dashes.toFloatArray(), style.offset)
            }
            canvas.drawPath(path(path), paint)
        }

        override fun text(text: String, size: Float, font: Any?, x: Float, y: Float, color: AidokuColor) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color(color)
                textSize = size
                typeface = font as? Typeface ?: Typeface.DEFAULT
            }
            canvas.drawText(text, x, y, paint)
        }

        override fun image(): AidokuBitmap = AndroidBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false))
    }

    override fun canvas(width: Int, height: Int): AidokuCanvas? = runCatching { AndroidCanvas(width, height) }.getOrNull()
    override fun decode(bytes: ByteArray): AidokuBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::AndroidBitmap)
    override fun font(name: String): Any = Typeface.create(name, Typeface.NORMAL)
    /** The weight is 0 for thin to 8 for black. Android before 9 has only normal and bold. */
    override fun systemFont(weight: Int): Any = if (android.os.Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.DEFAULT, (weight + 1) * 100, false)
    } else {
        Typeface.create(Typeface.DEFAULT, if (weight >= 5) Typeface.BOLD else Typeface.NORMAL)
    }

    override fun loadFont(bytes: ByteArray): Any? = runCatching {
        val file = File(context.cacheDir, "aidoku-font-${UUID.randomUUID()}.ttf").apply { writeBytes(bytes) }
        Typeface.createFromFile(file).also { file.delete() }
    }.getOrNull()

    override fun webView(): AidokuWebView = AndroidAidokuWebView(context)
}

/**
 * A web view of the js module, with no window. The thread of the source waits while the main thread works, as in
 * Asabunko. A script that gives a promise answers through a JavaScript interface.
 */
@SuppressLint("SetJavaScriptEnabled")
private class AndroidAidokuWebView(context: Context) : AidokuWebView {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var view: WebView
    @Volatile private var loaded = CountDownLatch(0)
    @Volatile private var lastUrl: String? = null
    private val answers = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val waiting = java.util.concurrent.ConcurrentHashMap<String, CountDownLatch>()

    /** The answers of async scripts. A null value comes as an empty key in [answers]. */
    inner class Bridge {
        @JavascriptInterface fun done(id: String, value: String?) {
            if (value != null) answers[id] = value
            waiting.remove(id)?.countDown()
        }
    }

    init {
        onMain {
            view = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = BrowserChecks.userAgent(context)
                addJavascriptInterface(Bridge(), "__moekomaAidoku")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(page: WebView, url: String) {
                        lastUrl = url
                        loaded.countDown()
                    }
                }
            }
        }
    }

    /** Runs the body on the main thread, and waits for it on another thread. */
    private fun <T> onMain(body: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return body()
        var result: Result<T>? = null
        val done = CountDownLatch(1)
        main.post {
            result = runCatching(body)
            done.countDown()
        }
        done.await(60, TimeUnit.SECONDS)
        return result?.getOrThrow() ?: throw IOException("The web view did not answer.")
    }

    override fun load(request: HttpRequest) {
        loaded = CountDownLatch(1)
        onMain { view.loadUrl(request.url, request.headers) }
    }

    override fun loadHtml(html: String, base: String?) {
        loaded = CountDownLatch(1)
        onMain { view.loadDataWithBaseURL(base, html, "text/html", "utf-8", null) }
    }

    override fun waitForLoad(): Boolean = loaded.await(60, TimeUnit.SECONDS)

    override fun evaluate(script: String, async: Boolean): String? {
        if (async) {
            // As callAsyncJavaScript of WebKit, the script is the body of an async function.
            val id = UUID.randomUUID().toString()
            val done = CountDownLatch(1)
            waiting[id] = done
            val call = "(async () => { try { const v = await (async () => { $script })(); " +
                "__moekomaAidoku.done(${JsRuntime.quote(id)}, v === undefined || v === null ? null : String(v)); } " +
                "catch (e) { __moekomaAidoku.done(${JsRuntime.quote(id)}, null); } })();"
            onMain { view.evaluateJavascript(call, null) }
            done.await(60, TimeUnit.SECONDS)
            waiting.remove(id)
            return answers.remove(id)
        }
        var text: String? = null
        val done = CountDownLatch(1)
        onMain {
            view.evaluateJavascript(script) { result ->
                // The web view gives the result as JSON, such as a string in quotes.
                text = runCatching { JSONTokener(result).nextValue() }.getOrNull()?.takeIf { it != org.json.JSONObject.NULL }?.toString()
                done.countDown()
            }
        }
        done.await(60, TimeUnit.SECONDS)
        return text
    }

    override fun addUserScript(script: String, atEnd: Boolean, mainFrameOnly: Boolean) {
        // Android has no user scripts. The script runs now, and again at the end of each load.
        onMain {
            view.evaluateJavascript(script, null)
            val client = view.webViewClient
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(page: WebView, url: String) {
                    page.evaluateJavascript(script, null)
                    client.onPageFinished(page, url)
                }
            }
        }
    }

    override fun cookies(): List<AidokuCookie> {
        val url = lastUrl ?: return emptyList()
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return emptyList()
        val header = CookieManager.getInstance().getCookie(url) ?: return emptyList()
        return header.split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim().ifEmpty { return@mapNotNull null }
            AidokuCookie(name, part.substringAfter('=', "").trim(), null, host, "/", url.startsWith("https"), false)
        }
    }

    override fun deleteCookies(name: String?, value: String?, domain: String?) {
        val url = lastUrl ?: return
        val manager = CookieManager.getInstance()
        for (cookie in cookies()) {
            if ((name == null || cookie.name == name) && (value == null || cookie.value == value) && (domain == null || cookie.domain == domain)) {
                manager.setCookie(url, "${cookie.name}=; Max-Age=0; Path=/")
            }
        }
        manager.flush()
    }

    override fun close() {
        main.post { if (::view.isInitialized) view.destroy() }
    }
}
