package io.github.stivengjekaj.moekoma.core

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.AsyncFunctionBinding
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.JsObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.Closeable
import java.nio.charset.Charset

/** A request of a JavaScript source. */
class HttpRequest(val url: String, val method: String = "GET", val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null)

/**
 * An answer to a JavaScript source. The names of the headers are in lowercase. [setCookies] holds each Set-Cookie
 * header, because one map cannot hold two headers with the same name.
 */
class HttpResponse(val status: Int, val url: String, val headers: Map<String, String>, val body: ByteArray, val setCookies: List<String> = emptyList())

/** Makes the network requests of a JavaScript source. The app gives OkHttp, and a test gives fixed answers. */
typealias Fetcher = suspend (HttpRequest) -> HttpResponse

class JsException(message: String) : Exception(message) {
    companion object {
        fun timeout() = JsException("The source did not answer in time.")
    }
}

/**
 * One JavaScript context with the web functions that the sources need: fetch, timers, TextDecoder, and the libraries of
 * libraries.js. The scripts are the scripts of Asabunko. A small shim gives them the native functions of Asabunko with
 * QuickJS. One call runs at a time in a context.
 */
class JsRuntime private constructor(private val fetcher: Fetcher) : Closeable {
    private val js = QuickJs.create(Dispatchers.Default)
    private val lock = Mutex()
    var timeoutMillis = 60_000L

    companion object {
        /**
         * Only one context starts at a time. QuickJS-KT keeps the classes of the JVM in global references that each new
         * context replaces with no lock, so two contexts that start at the same time delete the same reference and the
         * app stops. This is in quickjs-kt 1.0.15 (initGlobals in quickjs_jni.c).
         */
        private val starting = Mutex()

        /** Runs the start of another QuickJS context, such as one of the js module of Aidoku, under the same lock. */
        internal suspend fun <T> startLocked(block: suspend () -> T): T = starting.withLock { block() }

        /** A runtime with the libraries and the host functions. */
        suspend fun create(fetcher: Fetcher): JsRuntime = starting.withLock { JsRuntime(fetcher).apply { start() } }

        fun resource(name: String): String =
            JsRuntime::class.java.getResource("/io/github/stivengjekaj/moekoma/core/js/$name")?.readText()
                ?: throw JsException("No $name in the core module.")

        private val json = Json { ignoreUnknownKeys = true }

        /** A text as a JavaScript string literal. */
        fun quote(text: String): String = JsonPrimitive(text).toString()

        private fun toJson(value: Any?): JsonElement = when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is ByteArray -> JsonArray(value.map { JsonPrimitive(it.toInt() and 0xFF) })
            is List<*> -> JsonArray(value.map(::toJson))
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
            else -> JsonPrimitive(value.toString())
        }

        /** The cookies of the Set-Cookie headers of an answer, as host.js reads them: a name, a value, a domain, a path, and the end in milliseconds. */
        internal fun cookies(response: HttpResponse): JsonArray {
            val headers = response.setCookies.ifEmpty { listOfNotNull(response.headers["set-cookie"]) }
            val host = runCatching { java.net.URI(response.url).host }.getOrNull().orEmpty()
            val now = System.currentTimeMillis()
            return JsonArray(headers.flatMap { header -> runCatching { java.net.HttpCookie.parse("set-cookie: $header") }.getOrDefault(emptyList()) }.map { cookie ->
                val item = linkedMapOf<String, JsonElement>(
                    "name" to JsonPrimitive(cookie.name), "value" to JsonPrimitive(cookie.value),
                    "domain" to JsonPrimitive(cookie.domain?.removePrefix(".") ?: host), "path" to JsonPrimitive(cookie.path ?: "/"),
                )
                if (cookie.maxAge >= 0) item["expires"] = JsonPrimitive(now + cookie.maxAge * 1000)
                JsonObject(item)
            })
        }

        /** Decodes bytes with an IANA name of an encoding, such as "utf-8", "gbk", or "shift_jis". */
        fun decode(bytes: ByteArray, label: String): String {
            val name = label.ifEmpty { "utf-8" }
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: Charsets.UTF_8
            return String(bytes, charset)
        }
    }

    private suspend fun start() {
        js.defineBinding("__kt_fetch", AsyncFunctionBinding { args ->
            val headers = json.parseToJsonElement(args[2] as String) as JsonObject
            val request = HttpRequest(
                url = args[0] as String, method = args[1] as String,
                headers = headers.mapValues { (it.value as? JsonPrimitive)?.contentOrNull ?: it.value.toString() },
                body = args[3] as? ByteArray,
            )
            val response = try {
                fetcher(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The script gets the message, and fetch rejects with a TypeError, as in a browser.
                return@AsyncFunctionBinding JsObject(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
            }
            // A Map of Kotlin becomes a Map of JavaScript, and a JsObject becomes a plain object.
            JsObject(mapOf(
                "status" to response.status, "url" to response.url,
                "headers" to toJson(response.headers).toString(), "body" to response.body,
                "cookies" to cookies(response).toString(),
            ))
        })
        js.defineBinding("__kt_decode", FunctionBinding { args -> decode(args[0] as? ByteArray ?: ByteArray(0), args[1] as? String ?: "") })
        js.defineBinding("__kt_sleep", AsyncFunctionBinding { args -> delay((args[0] as Number).toLong()); null })
        js.defineBinding("__kt_log", FunctionBinding { _ -> null })
        evaluate(SHIM, "shim.js")
        evaluate(resource("libraries.js"), "libraries.js")
        evaluate(resource("host.js"), "host.js")
    }

    /** Runs a script, such as the code of a plugin. */
    suspend fun evaluate(script: String, file: String = "source.js") {
        lock.withLock { run { js.evaluate<Any?>(script, file, false) } }
    }

    /**
     * Calls the function that [function] gives, such as "plugin.popularNovels", with the arguments. The function can
     * give a promise. The answer is its value as JSON.
     */
    suspend fun invoke(function: String, args: List<Any?> = emptyList()): String = lock.withLock {
        val call = "await __invoke(($function), ...JSON.parse(${quote(toJson(args).toString())}))"
        run { js.evaluate<String>(call, "invoke.js", false) }
    }

    /** Calls a function and decodes its answer. */
    suspend fun <T> invoke(serializer: kotlinx.serialization.KSerializer<T>, function: String, args: List<Any?> = emptyList()): T {
        val text = invoke(function, args)
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            throw JsException("The source gave an answer that Moekoma cannot read: ${e.message?.take(300)}")
        }
    }

    /**
     * Adds a native function that answers at once, as the binding `__kt_<name>`. A script in the context maps it to the
     * form that its code needs. The arguments are strings, numbers, booleans, null, and ByteArray for an Int8Array.
     */
    fun defineBinding(name: String, handler: (Array<Any?>) -> Any?) {
        js.defineBinding("__kt_$name", FunctionBinding { args -> handler(args) })
    }

    /** Adds a native function that takes time, such as a page in a web view. The script calls `__native.<name>(text)` and gets a promise of the text of the answer. */
    suspend fun defineAsync(name: String, handler: suspend (String) -> String) {
        js.defineBinding("__kt_$name", AsyncFunctionBinding { args -> handler(args[0]?.toString() ?: "") })
        evaluate("__native[${quote(name)}] = (input) => globalThis[${quote("__kt_$name")}](String(input));", "define.js")
    }

    private suspend fun <T> run(block: suspend () -> T): T = try {
        withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw JsException.timeout()
    } catch (e: QuickJsException) {
        throw JsException(e.message?.lineSequence()?.firstOrNull()?.removePrefix("Error: ") ?: "The script failed.")
    }

    override fun close() = js.close()
}

/**
 * Gives the native functions of the scripts of Asabunko (__native.fetch, decode, timer, and log) with the async
 * functions of QuickJS. The bytes go as Int8Array, which QuickJS maps to a ByteArray.
 */
private val SHIM = """
(function () {
  const signed = (b) => b ? new Int8Array(b.buffer, b.byteOffset, b.byteLength) : null;
  const unsigned = (b) => b ? new Uint8Array(b.buffer, b.byteOffset, b.byteLength) : new Uint8Array(0);
  globalThis.__native = {
    fetch(id, url, method, headers, body) {
      __kt_fetch(url, method, JSON.stringify(headers || {}), signed(body)).then(
        (r) => r.error !== undefined ? __fetchDone(id, r.error) : __fetchDone(id, null, r.status, r.url, JSON.parse(r.headers), unsigned(r.body), JSON.parse(r.cookies)),
        (e) => __fetchDone(id, String((e && e.message) || e)));
    },
    decode(bytes, label) { return __kt_decode(signed(bytes), label); },
    timer(id, ms) { __kt_sleep(ms).then(() => __timerFire(id)); },
    log(message) { __kt_log(String(message)); },
  };
})();
""".trimIndent()
