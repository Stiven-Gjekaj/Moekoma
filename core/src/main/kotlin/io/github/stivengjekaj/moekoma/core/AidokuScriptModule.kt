package io.github.stivengjekaj.moekoma.core

import com.dokar.quickjs.QuickJs
import com.dylibso.chicory.wasm.types.ValType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** A JavaScript context of the js module, in QuickJS. As in Asabunko, which uses JavaScriptCore. */
internal class AidokuScript private constructor(private val js: QuickJs) : AutoCloseable {
    /** The value of a script as text, or null when the script throws or gives nothing. A promise gives its value. */
    fun evaluate(script: String, async: Boolean): String? = runCatching {
        runBlocking {
            withTimeoutOrNull(60_000) {
                val source = JsRuntime.quote(script)
                val call = if (async) {
                    "await (async () => { const v = await (0, eval)($source); return v === undefined || v === null ? null : String(v); })()"
                } else {
                    "(() => { const v = (0, eval)($source); return v === undefined || v === null ? null : String(v); })()"
                }
                js.evaluate<String?>(call, "aidoku.js", false)
            }
        }
    }.getOrNull()

    fun global(name: String): String? = evaluate("globalThis[${JsRuntime.quote(name)}]", async = false)

    override fun close() = js.close()

    companion object {
        fun create(): AidokuScript = runBlocking { AidokuScript(JsRuntime.startLocked { QuickJs.create(Dispatchers.Default) }) }
    }
}

/** The js module of aidoku-rs: a JavaScript context, and a web view of the platform. */
internal fun AidokuHost.installScript(define: Define) {
    val i32 = ValType.I32
    fun result(value: Int) = longArrayOf(value.toLong())
    fun script(rid: Long) = item(rid.toInt()) as? AidokuScript
    fun web(rid: Long) = item(rid.toInt()) as? AidokuWebView

    define("js", "context_create", emptyList(), listOf(i32)) { result(runCatching { add(AidokuScript.create()) }.getOrDefault(-1)) }
    define("js", "context_eval", listOf(i32, i32, i32), listOf(i32)) { a ->
        val script = script(a[0]) ?: return@define result(-2)
        result(script.evaluate(string(a[1].toInt(), a[2].toInt()), async = false)?.let(::addString) ?: -1)
    }
    define("js", "context_eval_async", listOf(i32, i32, i32), listOf(i32)) { a ->
        val script = script(a[0]) ?: return@define result(-2)
        result(script.evaluate(string(a[1].toInt(), a[2].toInt()), async = true)?.let(::addString) ?: -1)
    }
    define("js", "context_get", listOf(i32, i32, i32), listOf(i32)) { a ->
        val script = script(a[0]) ?: return@define result(-2)
        result(script.global(string(a[1].toInt(), a[2].toInt()))?.let(::addString) ?: -1)
    }

    define("js", "webview_create", emptyList(), listOf(i32)) { result(platform?.webView()?.let(::add) ?: -1) }
    define("js", "webview_set_rule_list", listOf(i32, i32, i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        result(if (view.setRuleList(string(a[1].toInt(), a[2].toInt()))) 0 else -6)
    }
    define("js", "webview_load", listOf(i32, i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        val request = item(a[1].toInt()) as? AidokuHost.Request ?: return@define result(-5)
        val url = request.url ?: return@define result(-5)
        view.load(HttpRequest(url, request.method, request.headers.toMap(), request.body))
        result(0)
    }
    define("js", "webview_load_html", listOf(i32, i32, i32, i32, i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        val base = if (a[4].toInt() > 0) string(a[3].toInt(), a[4].toInt()) else null
        view.loadHtml(string(a[1].toInt(), a[2].toInt()), base)
        result(0)
    }
    define("js", "webview_wait_for_load", listOf(i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        result(if (view.waitForLoad()) 0 else -1)
    }
    define("js", "webview_eval", listOf(i32, i32, i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        result(view.evaluate(string(a[1].toInt(), a[2].toInt()), async = false)?.let(::addString) ?: -1)
    }
    define("js", "webview_eval_async", listOf(i32, i32, i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        result(view.evaluate(string(a[1].toInt(), a[2].toInt()), async = true)?.let(::addString) ?: -1)
    }
    define("js", "webview_add_user_script", listOf(i32, i32, i32, i32, i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        view.addUserScript(string(a[1].toInt(), a[2].toInt()), atEnd = a[3].toInt() != 0, mainFrameOnly = a[4].toInt() != 0)
        result(0)
    }
    define("js", "webview_get_cookies", listOf(i32), listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        val data = PostcardWriter.write {
            list(view.cookies()) { cookie ->
                string(cookie.name)
                string(cookie.value)
                option(cookie.expires) { signed(it) }
                string(cookie.domain)
                string(cookie.path)
                bool(cookie.secure)
                bool(cookie.httpOnly)
            }
        }
        result(add(data))
    }
    define("js", "webview_delete_cookie", List(7) { i32 }, listOf(i32)) { a ->
        val view = web(a[0]) ?: return@define result(-2)
        // A name length of 0xFFFFFFFF means all cookies.
        val all = a[2].toInt() == -1
        view.deleteCookies(
            name = if (all) null else string(a[1].toInt(), a[2].toInt()),
            value = if (all || a[4].toInt() <= 0) null else string(a[3].toInt(), a[4].toInt()),
            domain = if (all || a[6].toInt() <= 0) null else string(a[5].toInt(), a[6].toInt()),
        )
        result(0)
    }
}
