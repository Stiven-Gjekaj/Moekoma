package io.github.stivengjekaj.moekoma.core

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.ExternalType
import com.dylibso.chicory.wasm.types.FunctionImport
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/** Defines one import of the host: the module, the name, the types of the parameters and of the results, and the body. */
internal typealias Define = (String, String, List<ValType>, List<ValType>, (LongArray) -> LongArray?) -> Unit

/** A fault of an Aidoku source. */
sealed class AidokuException(message: String) : Exception(message) {
    class Trap(text: String) : AidokuException("The source stopped: $text")
    class Code(val code: Int) : AidokuException("The source failed with the code $code.")
    class Message(text: String) : AidokuException(text)
    class Missing(name: String) : AidokuException("The source has no $name.")
}

/** The settings of one Aidoku source, as the postcard bytes of each value. They stay in a file, as in Asabunko. */
class AidokuDefaults(private val file: File?, initial: Map<String, ByteArray>) {
    private val values: MutableMap<String, ByteArray>

    init {
        val saved = file?.takeIf { it.isFile }?.let { stored ->
            runCatching {
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), stored.readText())
                    .mapValues { Base64.getDecoder().decode(it.value) }
            }.getOrNull()
        }.orEmpty()
        values = (initial + saved).toMutableMap()
    }

    /** The source reads the values on its thread, and the settings screen reads them on the main thread. */
    @Synchronized operator fun get(key: String): ByteArray? = values[key]

    @Synchronized operator fun set(key: String, value: ByteArray?) {
        if (value == null) values.remove(key) else values[key] = value
        val target = file ?: return
        target.parentFile?.mkdirs()
        val text = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), values.mapValues { Base64.getEncoder().encodeToString(it.value) })
        val part = File(target.path + ".part")
        part.writeText(text)
        part.renameTo(target)
    }
}

/**
 * The parts of the canvas and js modules that need the platform: bitmaps, fonts, and a web view. The app gives them
 * with Android. With no platform, these imports give an error, and the other imports work.
 */
interface AidokuPlatform {
    fun canvas(width: Int, height: Int): AidokuCanvas?
    fun decode(bytes: ByteArray): AidokuBitmap?
    fun font(name: String): Any?
    fun systemFont(weight: Int): Any?
    fun loadFont(bytes: ByteArray): Any?
    fun webView(): AidokuWebView? = null
}

/** A bitmap of the canvas module. */
interface AidokuBitmap {
    val width: Int
    val height: Int
    fun png(): ByteArray?
}

/** A canvas of the canvas module, with its origin at the top left, as in aidoku-rs. */
interface AidokuCanvas {
    fun setTransform(translateX: Float, translateY: Float, scaleX: Float, scaleY: Float, rotate: Float)
    fun draw(image: AidokuBitmap, source: AidokuRect?, target: AidokuRect): Boolean
    fun fill(path: List<AidokuPathOp>, color: AidokuColor)
    fun stroke(path: List<AidokuPathOp>, style: AidokuStroke)
    fun text(text: String, size: Float, font: Any?, x: Float, y: Float, color: AidokuColor)
    fun image(): AidokuBitmap?
}

/** A web view of the js module, with no window. Each function waits for the page. */
interface AidokuWebView : AutoCloseable {
    fun load(request: HttpRequest)
    fun loadHtml(html: String, base: String?)
    fun waitForLoad(): Boolean
    fun evaluate(script: String, async: Boolean): String?
    fun addUserScript(script: String, atEnd: Boolean, mainFrameOnly: Boolean)
    fun setRuleList(json: String): Boolean = false
    fun cookies(): List<AidokuCookie>
    fun deleteCookies(name: String?, value: String?, domain: String?)
    override fun close() {}
}

data class AidokuCookie(val name: String, val value: String, val expires: Long?, val domain: String, val path: String, val secure: Boolean, val httpOnly: Boolean)
data class AidokuRect(val x: Float, val y: Float, val width: Float, val height: Float)
data class AidokuColor(val red: Float, val green: Float, val blue: Float, val alpha: Float)
data class AidokuStroke(val color: AidokuColor, val width: Float, val cap: Int, val join: Int, val miter: Float, val dashes: List<Float>, val offset: Float)

/** One operation of a path of the canvas module: `PathOp` of aidoku-rs. */
sealed interface AidokuPathOp {
    data class Move(val x: Float, val y: Float) : AidokuPathOp
    data class Line(val x: Float, val y: Float) : AidokuPathOp
    data class Quad(val cx: Float, val cy: Float, val x: Float, val y: Float) : AidokuPathOp
    data class Cubic(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float) : AidokuPathOp
    data class Arc(val x: Float, val y: Float, val radius: Float, val start: Float, val sweep: Float) : AidokuPathOp
    data object Close : AidokuPathOp
}

/**
 * One Aidoku source in Chicory, with the imports of aidoku-rs: env, std, net, html, defaults, canvas, and js. As in
 * Asabunko. All calls go through one thread, because the imports block while they wait for the network.
 */
class AidokuHost(name: String, wasm: ByteArray, val defaults: AidokuDefaults, internal val fetcher: Fetcher, internal val platform: AidokuPlatform?) : AutoCloseable {
    internal class Request {
        var method = "GET"
        var url: String? = null
        val headers = linkedMapOf<String, String>()
        var body: ByteArray? = null
        var timeout = 30.0
        var response: HttpResponse? = null
    }

    /** An image of the canvas module. An image from the network keeps its bytes, because a source can decrypt them. */
    internal class Picture(val data: ByteArray?, private var decoded: AidokuBitmap?, private val platform: AidokuPlatform?) {
        private var tried = decoded != null
        val image: AidokuBitmap? get() {
            if (!tried && data != null) {
                tried = true
                decoded = platform?.decode(data)
            }
            return decoded
        }

        /** The bytes of a file of the image: the bytes from the network, or a PNG of the pixels. */
        val fileData: ByteArray? get() = data ?: image?.png()
    }

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "aidoku-$name").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val items = HashMap<Int, Any>()
    private var next = 1
    private val printed = mutableListOf<String>()
    private val instance: Instance
    val exports: Set<String>
    private val functions = HashMap<String, ExportFunction>()

    init {
        val module = Parser.parse(wasm)
        val builder = ImportValues.builder()
        val defined = mutableSetOf<String>()
        val define: Define = { mod, field, params, results, body ->
            builder.addFunction(HostFunction(mod, field, FunctionType.of(params, results)) { _, args -> body(args) })
            defined += "$mod.$field"
        }
        installImports(define)
        // An import of the module that the host does not have gets a stub that gives an error.
        val imports = module.importSection()
        for (index in 0 until imports.importCount()) {
            val import = imports.getImport(index)
            if (import.importType() != ExternalType.FUNCTION || "${import.module()}.${import.name()}" in defined) continue
            val type = module.typeSection().getType((import as FunctionImport).typeIndex())
            val stub = type.returns().map { returned ->
                when (returned) {
                    ValType.I64 -> -1L
                    ValType.F32 -> java.lang.Float.floatToRawIntBits(-1f).toLong()
                    ValType.F64 -> java.lang.Double.doubleToRawLongBits(-1.0)
                    else -> -1L
                }
            }.toLongArray()
            builder.addFunction(HostFunction(import.module(), import.name(), type) { _, _ -> if (stub.isEmpty()) null else stub.copyOf() })
            defined += "${import.module()}.${import.name()}"
        }
        instance = Instance.builder(module).withImportValues(builder.build()).build()
        val section = module.exportSection()
        exports = (0 until section.exportCount()).map { section.getExport(it).name() }.toSet()
    }

    /** Runs the body on the thread of the source. */
    suspend fun <T> run(body: AidokuHost.() -> T): T = withContext(dispatcher) { body() }

    /** Calls the export "start". The caller is on the thread of the source. */
    fun start() {
        if ("start" in exports) invoke("start")
    }

    override fun close() {
        executor.execute { items.values.forEach { runCatching { (it as? AutoCloseable)?.close() } } }
        executor.shutdown()
    }

    // Values and memory

    private val memory: Memory get() = instance.memory()

    private fun byteCount(): Long = memory.pages().toLong() * Memory.PAGE_SIZE

    fun read(pointer: Int, count: Int): ByteArray {
        if (count <= 0 || pointer < 0 || pointer.toLong() + count > byteCount()) return ByteArray(0)
        return memory.readBytes(pointer, count)
    }

    fun string(pointer: Int, count: Int): String = String(read(pointer, count), Charsets.UTF_8)

    fun write(data: ByteArray, pointer: Int): Boolean {
        if (pointer < 0 || pointer.toLong() + data.size > byteCount()) return false
        memory.write(pointer, data)
        return true
    }

    fun int32(pointer: Int): Int {
        if (pointer < 0 || pointer.toLong() + 4 > byteCount()) return 0
        return memory.readInt(pointer)
    }

    /** The payload of a buffer of the source: [length][capacity][postcard], where the length counts the 8 bytes of the head. */
    fun encodedBuffer(pointer: Int): ByteArray {
        val total = int32(pointer)
        if (total < 8) return ByteArray(0)
        return read(pointer + 8, total - 8)
    }

    // The store of the host

    fun add(item: Any): Int {
        val rid = next++
        items[rid] = item
        return rid
    }

    fun item(rid: Int): Any? = items[rid]

    fun destroy(rid: Int) {
        runCatching { (items.remove(rid) as? AutoCloseable)?.close() }
    }

    fun addString(text: String): Int = add(text.toByteArray(Charsets.UTF_8))

    // Calls of exports

    /** Calls an export with the arguments. The caller is on the thread of the source. */
    fun invoke(name: String, vararg arguments: Long): LongArray {
        val function = functions.getOrPut(name) { if (name in exports) instance.export(name) else throw AidokuException.Missing(name) }
        printed.clear()
        return try {
            function.apply(*arguments) ?: LongArray(0)
        } catch (e: AidokuException) {
            throw e
        } catch (e: Exception) {
            throw AidokuException.Trap(printed.lastOrNull() ?: (e.message ?: e.javaClass.simpleName))
        }
    }

    /** The values of the arguments of a call. Bytes go in as a resource, which [made] keeps until the call ends. */
    private fun values(arguments: List<Any>, made: MutableList<Int>): LongArray = arguments.map { argument ->
        when (argument) {
            is ByteArray -> add(argument).also { made += it }.toLong()
            is Int -> argument.toLong()
            is Boolean -> if (argument) 1L else 0L
            else -> -1L
        }
    }.toLongArray()

    /** Calls a function that gives no result, such as `handle_notification`. */
    fun callWithNoResult(name: String, vararg arguments: Any) {
        val made = mutableListOf<Int>()
        try {
            invoke(name, *values(arguments.toList(), made))
        } finally {
            made.forEach(::destroy)
        }
    }

    /** Calls an export that gives a result buffer, and gives the postcard payload. */
    fun call(name: String, vararg arguments: Any): ByteArray {
        val made = mutableListOf<Int>()
        try {
            val pointer = invoke(name, *values(arguments.toList(), made)).firstOrNull()?.toInt() ?: 0
            if (pointer < 0) throw AidokuException.Code(pointer)
            val mark = int32(pointer)
            try {
                if (mark == -1) {
                    val total = int32(pointer + 8)
                    throw AidokuException.Message(string(pointer + 12, maxOf(total - 12, 0)))
                }
                return read(pointer + 8, maxOf(mark - 8, 0))
            } finally {
                if ("free_result" in exports) runCatching { invoke("free_result", pointer.toLong()) }
            }
        } finally {
            made.forEach(::destroy)
        }
    }

    // The network, which blocks the thread of the source

    internal fun send(request: Request): Int {
        val url = request.url ?: return -4
        val response = runCatching {
            runBlocking { fetcher(HttpRequest(url, request.method, request.headers.toMap(), request.body)) }
        }.getOrNull() ?: return -10
        request.response = response
        return 0
    }

    // Imports

    private fun installImports(define: Define) {
        val i32 = ValType.I32
        // env
        define("env", "abort", emptyList(), emptyList()) { printed += "abort"; null }
        define("env", "print", listOf(i32, i32), emptyList()) { a -> printed += string(a[0].toInt(), a[1].toInt()); null }
        define("env", "sleep", listOf(i32), emptyList()) { a -> Thread.sleep(a[0].toInt().coerceIn(0, 10) * 1000L); null }
        define("env", "send_partial_result", listOf(i32), emptyList()) { null }

        // std
        define("std", "destroy", listOf(i32), emptyList()) { a -> destroy(a[0].toInt()); null }
        define("std", "buffer_len", listOf(i32), listOf(i32)) { a -> result((item(a[0].toInt()) as? ByteArray)?.size ?: -1) }
        define("std", "read_buffer", listOf(i32, i32, i32), listOf(i32)) { a ->
            val data = item(a[0].toInt()) as? ByteArray ?: return@define result(-1)
            val count = a[2].toInt()
            if (count < 0 || count > data.size || !write(data.copyOf(count), a[1].toInt())) result(-3) else result(0)
        }
        define("std", "current_date", emptyList(), listOf(ValType.F64)) {
            longArrayOf(java.lang.Double.doubleToRawLongBits((System.currentTimeMillis() / 1000).toDouble()))
        }
        define("std", "utc_offset", emptyList(), listOf(ValType.I64)) {
            longArrayOf(-(TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toLong())
        }
        define("std", "parse_date", List(8) { i32 }, listOf(ValType.F64)) { a ->
            val text = string(a[0].toInt(), a[1].toInt())
            val format = string(a[2].toInt(), a[3].toInt())
            val locale = if (a[5].toInt() > 0) string(a[4].toInt(), a[5].toInt()) else ""
            val zone = if (a[7].toInt() > 0) string(a[6].toInt(), a[7].toInt()) else "UTC"
            val seconds = runCatching {
                val formatter = SimpleDateFormat(format, if (locale.isEmpty()) Locale.US else Locale.forLanguageTag(locale.replace('_', '-')))
                formatter.timeZone = if (zone == "current") TimeZone.getDefault() else TimeZone.getTimeZone(zone)
                formatter.parse(text)!!.time / 1000.0
            }.getOrDefault(-5.0)
            longArrayOf(java.lang.Double.doubleToRawLongBits(seconds))
        }

        // defaults
        define("defaults", "get", listOf(i32, i32), listOf(i32)) { a ->
            val value = defaults[string(a[0].toInt(), a[1].toInt())] ?: return@define result(-2)
            result(add(value))
        }
        define("defaults", "set", listOf(i32, i32, i32, i32), listOf(i32)) { a ->
            val key = string(a[0].toInt(), a[1].toInt())
            defaults[key] = if (a[2].toInt() == 6) null else encodedBuffer(a[3].toInt())
            result(0)
        }

        installNet(define)
        installHtml(define)
        installCanvas(define)
        installScript(define)
    }

    private fun installNet(define: Define) {
        val i32 = ValType.I32
        val methods = listOf("GET", "POST", "PUT", "HEAD", "DELETE", "PATCH", "OPTIONS", "CONNECT", "TRACE")
        fun request(rid: Long) = item(rid.toInt()) as? Request
        define("net", "init", listOf(i32), listOf(i32)) { a ->
            val method = methods.getOrNull(a[0].toInt()) ?: return@define result(-3)
            result(add(Request().apply { this.method = method }))
        }
        define("net", "set_url", listOf(i32, i32, i32), listOf(i32)) { a ->
            val request = request(a[0]) ?: return@define result(-1)
            val text = string(a[1].toInt(), a[2].toInt())
            if (!text.startsWith("http://") && !text.startsWith("https://")) return@define result(-4)
            request.url = text.replace(" ", "%20")
            result(0)
        }
        define("net", "set_header", listOf(i32, i32, i32, i32, i32), listOf(i32)) { a ->
            val request = request(a[0]) ?: return@define result(-1)
            request.headers[string(a[1].toInt(), a[2].toInt())] = string(a[3].toInt(), a[4].toInt())
            result(0)
        }
        define("net", "set_body", listOf(i32, i32, i32), listOf(i32)) { a ->
            val request = request(a[0]) ?: return@define result(-1)
            request.body = read(a[1].toInt(), a[2].toInt())
            result(0)
        }
        define("net", "set_timeout", listOf(i32, ValType.F64), listOf(i32)) { a ->
            val request = request(a[0]) ?: return@define result(-1)
            request.timeout = maxOf(1.0, java.lang.Double.longBitsToDouble(a[1]))
            result(0)
        }
        define("net", "send", listOf(i32), listOf(i32)) { a -> result(request(a[0])?.let(::send) ?: -1) }
        define("net", "send_all", listOf(i32, i32), listOf(i32)) { a ->
            val pointer = a[0].toInt()
            var failed = false
            for (index in 0 until maxOf(a[1].toInt(), 0)) {
                val slot = pointer + index * 4
                val code = request(int32(slot).toLong())?.let(::send) ?: -1
                if (code != 0) failed = true
                if (pointer < 0 || slot.toLong() + 4 > byteCount()) return@define result(-11)
                memory.writeI32(slot, code)
            }
            result(if (failed) -10 else 0)
        }
        fun response(rid: Long) = request(rid)?.response
        define("net", "data_len", listOf(i32), listOf(i32)) { a -> result(response(a[0])?.body?.size ?: -8) }
        define("net", "read_data", listOf(i32, i32, i32), listOf(i32)) { a ->
            val response = response(a[0]) ?: return@define result(-8)
            val count = a[2].toInt()
            if (count < 0 || count > response.body.size || !write(response.body.copyOf(count), a[1].toInt())) result(-11) else result(0)
        }
        define("net", "get_status_code", listOf(i32), listOf(i32)) { a -> result(response(a[0])?.status ?: -8) }
        define("net", "get_url", listOf(i32), listOf(i32)) { a -> result(response(a[0])?.let { addString(it.url) } ?: -8) }
        define("net", "get_header", listOf(i32, i32, i32), listOf(i32)) { a ->
            val response = response(a[0]) ?: return@define result(-8)
            val name = string(a[1].toInt(), a[2].toInt()).lowercase()
            val values = response.headers.filter { it.key.lowercase() == name }.values
            if (values.isEmpty()) result(-7) else result(addString(values.joinToString(", ")))
        }
        define("net", "html", listOf(i32), listOf(i32)) { a ->
            val response = response(a[0]) ?: return@define result(-8)
            val document = runCatching { AidokuHtml.parse(response) }.getOrNull() ?: return@define result(-5)
            result(add(document))
        }
        define("net", "get_image", listOf(i32), listOf(i32)) { a ->
            val response = response(a[0]) ?: return@define result(-8)
            result(add(Picture(response.body, null, platform)))
        }
        define("net", "set_rate_limit", listOf(i32, i32, i32), emptyList()) { null }
    }

    internal companion object {
        fun result(value: Int) = longArrayOf(value.toLong())
        fun float(value: Long): Float = java.lang.Float.intBitsToFloat(value.toInt())
        fun float(value: Float): Long = java.lang.Float.floatToRawIntBits(value).toLong()
    }
}
