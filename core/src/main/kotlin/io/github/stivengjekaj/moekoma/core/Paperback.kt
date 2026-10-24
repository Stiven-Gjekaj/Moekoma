package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The repository of Paperback 0.9 extensions: versioning.json, with each extension in <Id>/index.js. As in Asabunko. */
object PaperbackIndex {
    private val json = Json { ignoreUnknownKeys = true }

    /** The extensions that give chapters and a search. A tracker, such as AniList, is not a reader. */
    fun parse(data: ByteArray, url: String): List<SourceInfo> {
        val base = (if (url.substringBefore('?').endsWith("/versioning.json")) url.substringBeforeLast('/') else url).trimEnd('/')
        val sources = (json.parseToJsonElement(String(data, Charsets.UTF_8)) as JsonObject)["sources"]?.jsonArray.orEmpty()
        return sources.mapNotNull { item ->
            val source = item as? JsonObject ?: return@mapNotNull null
            fun text(name: String) = (source[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            val id = text("id") ?: return@mapNotNull null
            // The capabilities are a list of flags, or one integer with the flags.
            val mask = when (val value = source["capabilities"]) {
                is JsonArray -> value.mapNotNull { it.jsonPrimitive.intOrNull }.fold(0) { all, flag -> all or flag }
                is JsonPrimitive -> value.intOrNull ?: 65
                else -> 65
            }
            if (mask and 1 == 0 || mask and 64 == 0) return@mapNotNull null
            val folder = "$base/$id"
            SourceInfo(
                id = id, name = text("name") ?: id, language = text("language").orEmpty(), version = text("version").orEmpty(),
                icon = text("icon")?.let { "$folder/static/$it" }, code = "$folder/index.js", novels = false, adult = text("contentRating") == "ADULT",
            )
        }
    }
}

/** The state of one extension, as JSON text for each key. It stays in a file next to the code of the extension. */
class PaperbackState(private val file: File?) {
    @Serializable
    private data class Stored(val plain: Map<String, String> = emptyMap(), val secure: Map<String, String> = emptyMap())

    private var stored: Stored = file?.takeIf { it.isFile }?.let { runCatching { Json.decodeFromString(Stored.serializer(), it.readText()) }.getOrNull() } ?: Stored()

    @Synchronized fun get(key: String, secure: Boolean): String? = if (secure) stored.secure[key] else stored.plain[key]

    @Synchronized fun set(key: String, value: String?, secure: Boolean) {
        fun Map<String, String>.with() = if (value == null) this - key else this + (key to value)
        stored = if (secure) stored.copy(secure = stored.secure.with()) else stored.copy(plain = stored.plain.with())
        write()
    }

    @Synchronized fun reset() {
        stored = stored.copy(plain = emptyMap())
        write()
    }

    private fun write() {
        val target = file ?: return
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.writeText(Json.encodeToString(Stored.serializer(), stored))
        part.renameTo(target)
    }
}

/** AES-CBC with PKCS#7 padding, and AES-GCM with a tag of 16 bytes at the end of the data, as in Web Crypto. */
object PaperbackCrypto {
    fun aes(name: String, encrypt: Boolean, key: ByteArray, iv: ByteArray, data: ByteArray, additional: ByteArray?): ByteArray? = runCatching {
        val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        val secret = SecretKeySpec(key, "AES")
        when (name) {
            "AES-GCM" -> Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, secret, GCMParameterSpec(128, iv))
                additional?.let(::updateAAD)
                doFinal(data)
            }
            "AES-CBC" -> Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(mode, secret, IvParameterSpec(iv))
                doFinal(data)
            }
            else -> null
        }
    }.getOrNull()

    fun digest(name: String, data: ByteArray): ByteArray {
        val algorithm = when (name.uppercase()) {
            "MD5" -> "MD5"
            "SHA-1" -> "SHA-1"
            "SHA-384" -> "SHA-384"
            "SHA-512" -> "SHA-512"
            else -> "SHA-256"
        }
        return MessageDigest.getInstance(algorithm).digest(data)
    }
}

/** One Paperback 0.9 extension in its own JavaScript context. As in Asabunko. */
class PaperbackExtension private constructor(
    val info: SourceInfo,
    private val runtime: JsRuntime,
    private val sections: List<Section>,
) : AutoCloseable {
    @Serializable
    private data class Section(val id: String, val title: String, val type: Int)

    @Volatile private var coverHeaders: Map<String, String>? = null

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Starts the extension. [webView] runs `Application.executeInWebView`: it takes the JSON of the page and gives the
         * JSON of the answer. With no web view, the call fails, and the other functions work.
         */
        suspend fun load(
            info: SourceInfo, code: String, stateFile: File?, userAgent: String, fetcher: Fetcher, webView: (suspend (String) -> String)? = null,
        ): PaperbackExtension {
            val runtime = JsRuntime.create(fetcher)
            try {
                natives(runtime, PaperbackState(stateFile), userAgent)
                runtime.defineAsync("webView") { input -> webView?.invoke(input) ?: throw JsException("Moekoma has no web view here.") }
                runtime.evaluate(JsRuntime.resource("paperback.js"), "paperback.js")
                runtime.evaluate(code, "${info.id}/index.js")
                @Serializable
                class Started(val sections: List<Section>)
                val started = runtime.invoke(Started.serializer(), "__paperback.start", listOf(info.id))
                return PaperbackExtension(info, runtime, started.sections)
            } catch (e: Exception) {
                runtime.close()
                throw e
            }
        }

        /** The native functions of paperback.js. The bytes cross as Int8Array, and come back as an ArrayBuffer. */
        private suspend fun natives(runtime: JsRuntime, state: PaperbackState, userAgent: String) {
            val random = SecureRandom()
            runtime.defineBinding("pb_userAgent") { userAgent }
            runtime.defineBinding("pb_stateGet") { args -> state.get(args[0] as String, args[1] == true) }
            runtime.defineBinding("pb_stateSet") { args -> state.set(args[0] as String, args[1]?.toString(), args[2] == true); null }
            runtime.defineBinding("pb_stateReset") { state.reset(); null }
            runtime.defineBinding("pb_base64Encode") { args -> Base64.getEncoder().encodeToString(args[0] as? ByteArray ?: ByteArray(0)) }
            runtime.defineBinding("pb_base64Decode") { args ->
                var clean = (args[0] as? String).orEmpty().filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
                while (clean.length % 4 != 0) clean += "="
                val data = runCatching { Base64.getDecoder().decode(clean) }.getOrNull() ?: return@defineBinding null
                // A text comes back as a string, and other bytes as an ArrayBuffer, as the Paperback app does.
                val decoder = Charsets.UTF_8.newDecoder()
                runCatching { decoder.decode(java.nio.ByteBuffer.wrap(data)).toString() }.getOrNull() ?: data
            }
            runtime.defineBinding("pb_digest") { args ->
                val bytes = PaperbackCrypto.digest(args[0] as String, args[1] as? ByteArray ?: ByteArray(0))
                if (args[2] == true) bytes.joinToString("") { "%02x".format(it) } else bytes
            }
            runtime.defineBinding("pb_aes") { args ->
                PaperbackCrypto.aes(
                    args[0] as String, args[1] == true, args[2] as? ByteArray ?: ByteArray(0), args[3] as? ByteArray ?: ByteArray(0),
                    args[4] as? ByteArray ?: ByteArray(0), args[5] as? ByteArray,
                )
            }
            runtime.defineBinding("pb_decodeEntities") { args -> (args[0] as? String)?.let { runCatching { Entities.unescape(it) }.getOrDefault(it) } ?: "" }
            runtime.defineBinding("pb_random") { args -> ByteArray(((args[0] as? Number)?.toInt() ?: 0).coerceAtLeast(0)).also(random::nextBytes) }
            runtime.defineBinding("pb_uuid") { UUID.randomUUID().toString() }
            runtime.evaluate(NATIVES, "paperback-natives.js")
        }

        /** The chapters from the oldest to the newest. The numbers decide the direction. With no numbers, the newest comes first, as most sources give it. */
        private fun ordered(chapters: List<Chapter>): List<Chapter> {
            val numbered = chapters.mapNotNull { it.number }
            if (numbered.size >= 2) {
                val pairs = numbered.zipWithNext()
                return if (pairs.count { it.first < it.second } >= pairs.count { it.first > it.second }) chapters else chapters.reversed()
            }
            return chapters.reversed()
        }

        private fun number(value: Double) = if (value == Math.rint(value)) value.toLong().toString() else value.toString()

        private fun title(chapter: Chapter): String {
            val head = listOfNotNull(
                chapter.volume?.takeIf { it > 0 }?.let { "Vol. ${number(it)}" },
                chapter.number?.takeIf { it >= 0 }?.let { "Ch. ${number(it)}" },
            ).joinToString(" ")
            // A title such as "Chapter 12" only says the number again.
            val repeats = chapter.number?.let { value ->
                chapter.title?.let { Regex("^(chapter|ch\\.?|episode|ep\\.?)\\s*0*${Regex.escape(number(value))}$", RegexOption.IGNORE_CASE).matches(it) }
            } ?: false
            val name = chapter.title?.ifEmpty { null }
            if (name == null || name == head || repeats) return head.ifEmpty { "Chapter" }
            return if (head.isEmpty()) name else "$head: $name"
        }
    }

    private fun section(words: List<String>, type: Int? = null) =
        sections.firstOrNull { section -> (type != null && section.type == type) || words.any { it in "${section.id} ${section.title}".lowercase() } }

    private val popularSection get() = section(listOf("popular", "trend", "hot", "top"))
    private val latestSection get() = section(listOf("latest", "update", "recent", "new"), type = 3)

    val canShowLatest: Boolean get() = latestSection != null

    @Serializable
    private class Page(val items: List<Item>, val hasMore: Boolean) {
        @Serializable class Item(val id: String, val title: String, val cover: String? = null)
    }

    private suspend fun page(function: String, args: List<Any?>): OnlinePage {
        val page = runtime.invoke(Page.serializer(), function, args)
        // The headers of the covers come from the interceptors of the extension, such as a referer.
        if (coverHeaders == null) page.items.firstNotNullOfOrNull { it.cover }?.let { first ->
            coverHeaders = runCatching { runtime.invoke(MapSerializer(String.serializer(), String.serializer()), "__paperback.imageHeaders", listOf(first)) }.getOrDefault(emptyMap())
        }
        return OnlinePage(page.items.map { OnlineSeries(it.id, cleanTitle(it.title), it.cover) }, page.hasMore)
    }

    suspend fun popular(page: Int): OnlinePage =
        popularSection?.let { page("__paperback.section", listOf(it.id, page)) } ?: page("__paperback.search", listOf("", page))

    suspend fun latest(page: Int): OnlinePage = latestSection?.let { page("__paperback.section", listOf(it.id, page)) } ?: popular(page)

    suspend fun search(query: String, page: Int): OnlinePage = page("__paperback.search", listOf(query, page))

    suspend fun filterOptions(): List<SourceOption> {
        val list = runCatching { json.parseToJsonElement(runtime.invoke("__paperback.filters")) as JsonArray }.getOrNull() ?: return emptyList()
        return list.mapNotNull { element ->
            val filter = element as? JsonObject ?: return@mapNotNull null
            fun text(value: JsonElement?) = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            val id = text(filter["id"]) ?: return@mapNotNull null
            val type = text(filter["type"]) ?: return@mapNotNull null
            val title = text(filter["title"]) ?: id
            val choices = (filter["options"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .map { SourceOption.Choice(text(it["label"]).orEmpty(), text(it["value"]).orEmpty()) }
            when (type) {
                "input" -> SourceOption(id, title, SourceOption.Kind.Text(text(filter["placeholder"])), OptionValue.Text(text(filter["value"]).orEmpty()), type)
                "dropdown" -> if (choices.isEmpty()) null
                    else SourceOption(id, title, SourceOption.Kind.Select(choices), OptionValue.Select(text(filter["value"]) ?: choices[0].value), type)
                "multiselect" -> if (choices.isEmpty()) null else {
                    val marks = (filter["value"] as? JsonObject).orEmpty().mapValues { text(it.value) }
                    SourceOption(
                        id, title, SourceOption.Kind.Multi(choices, (filter["allowExclusion"] as? JsonPrimitive)?.booleanOrNull ?: false),
                        OptionValue.Multi(marks.filterValues { it == "included" }.keys.sorted(), marks.filterValues { it == "excluded" }.keys.sorted()), type,
                    )
                }
                else -> null
            }
        }
    }

    suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>): OnlinePage {
        if (filters.isEmpty()) return if (query.isEmpty()) popular(page) else search(query, page)
        val values = JsonObject(filters.mapValues { (_, value) ->
            when (value) {
                is OptionValue.Text -> JsonPrimitive(value.text)
                is OptionValue.Select -> JsonPrimitive(value.choice)
                is OptionValue.Multi -> JsonObject(value.included.associateWith { JsonPrimitive("included") } + value.excluded.associateWith { JsonPrimitive("excluded") })
                is OptionValue.Toggle -> JsonPrimitive(value.on)
                is OptionValue.Sort -> JsonPrimitive(value.index)
                is OptionValue.Range -> JsonObject(mapOf("from" to (value.from?.let(::JsonPrimitive) ?: JsonNull), "to" to (value.to?.let(::JsonPrimitive) ?: JsonNull)))
            }
        })
        return page("__paperback.browse", listOf(query, values.toString(), page))
    }

    // The settings form

    private fun <T> form(serializer: KSerializer<T>, text: String): T = json.decodeFromString(serializer, text)

    suspend fun hasSettingsForm(): Boolean = runCatching { runtime.invoke(Boolean.serializer(), "__paperback.hasForm") }.getOrDefault(false)

    suspend fun settingsForm(): SourceForm? = PaperbackForms.decode(runtime.invoke("__paperback.settingsForm"))

    suspend fun form(id: String): SourceForm = PaperbackForms.decode(runtime.invoke("__paperback.form", listOf(id))) ?: throw SourceFormException.noForm()

    suspend fun formAction(form: String, row: String, input: FormInput): SourceForm {
        val value: JsonElement = when (input) {
            FormInput.Tap -> JsonNull
            is FormInput.Text -> JsonPrimitive(input.text)
            is FormInput.Toggle -> JsonPrimitive(input.on)
            is FormInput.Number -> JsonPrimitive(input.value)
            is FormInput.Callback -> JsonObject(mapOf("callback" to JsonPrimitive(input.url)))
            is FormInput.Login, is FormInput.Web, FormInput.Logout -> throw SourceFormException.unknownRow()
        }
        return PaperbackForms.decode(runtime.invoke("__paperback.formAction", listOf(form, row, value.toString()))) ?: throw SourceFormException.noForm()
    }

    suspend fun submitForm(id: String) {
        runtime.invoke("__paperback.formSubmit", listOf(id))
    }

    // A series and its chapters

    @Serializable
    private class Chapter(
        val id: String, val title: String? = null, val number: Double? = null, val volume: Double? = null,
        val group: String? = null, val date: String? = null, val index: Double? = null,
    )

    @Serializable
    private class Details(
        val title: String, val cover: String? = null, val summary: String? = null, val author: String? = null, val status: String? = null,
        val tags: List<String> = emptyList(), val novel: Boolean = false, val chapters: List<Chapter> = emptyList(),
    )

    suspend fun details(series: OnlineSeries): OnlineDetails {
        val details = runtime.invoke(Details.serializer(), "__paperback.details", listOf(series.id))
        return OnlineDetails(
            series = OnlineSeries(series.id, cleanTitle(details.title), details.cover ?: series.cover),
            author = details.author, summary = details.summary?.let { Jsoup.parse(it).text() }?.ifEmpty { null }, status = details.status?.ifEmpty { null },
            genres = details.tags,
            chapters = ordered(details.chapters).map { OnlineChapter(it.id, title(it), it.number, it.date, it.group) },
            isNovel = details.novel,
        )
    }

    @Serializable
    private class Content(val html: String? = null, val pages: List<Request>? = null) {
        @Serializable class Request(val url: String, val headers: Map<String, String> = emptyMap())
    }

    /** The pages of a chapter, as requests with the headers of the interceptors, or the HTML of a chapter of a novel. */
    suspend fun content(chapter: OnlineChapter, series: OnlineSeries): Pair<String?, List<HttpRequest>> {
        if (!runtime.invoke(Boolean.serializer(), "__paperback.hasChapter", listOf(chapter.id))) details(series)
        val content = runtime.invoke(Content.serializer(), "__paperback.pages", listOf(chapter.id))
        content.html?.let { return it to emptyList() }
        return null to content.pages.orEmpty().filter { runCatching { URI(it.url) }.isSuccess }.map { HttpRequest(it.url, headers = it.headers) }
    }

    /** A cover with the headers of the interceptors of the extension, such as a referer. */
    fun imageRequest(url: String): HttpRequest = HttpRequest(url, headers = coverHeaders.orEmpty().filterKeys { !it.equals("cookie", true) })

    override fun close() = runtime.close()

    /** The close of the source, with no reference to the source. */
    fun closer(): AutoCloseable = runtime
}

/** A title with no spaces and no invisible marks at its ends, such as the zero-width joiner of some MangaDex titles. */
internal fun cleanTitle(text: String): String = text.trim { it.isWhitespace() || it in "\u200B\u200C\u200D\u200E\u200F\uFEFF" }

/** The forms of paperback.js are the JSON of SourceForm. */
internal object PaperbackForms {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private class Login(
        val method: String, val url: String? = null, val callbackScheme: String? = null, val useEmail: Boolean? = null, val signedIn: Boolean = false,
        val username: String? = null, val clearsCookies: Boolean? = null, val storageKeys: List<String>? = null,
    )

    @Serializable
    private class Row(
        val id: String, val type: String, val title: String = "", val subtitle: String? = null, val value: String? = null, val mark: String? = null,
        val selectable: Boolean? = null, val on: Boolean? = null, val form: String? = null, val secure: Boolean? = null, val confirm: String? = null,
        val destructive: Boolean? = null, val number: Double? = null, val minimum: Double? = null, val maximum: Double? = null, val step: Double? = null,
        val url: String? = null, val login: Login? = null,
    )

    @Serializable
    private class Section(val id: String, val header: String? = null, val footer: String? = null, val rows: List<Row> = emptyList())

    @Serializable
    private class Form(val id: String, val submit: Boolean = false, val sections: List<Section> = emptyList())

    fun decode(text: String): SourceForm? {
        if (text == "null") return null
        val form = json.decodeFromString(Form.serializer(), text)
        return SourceForm(form.id, form.submit, form.sections.map { section ->
            SourceForm.Section(section.id, section.header, section.footer, section.rows.map { row ->
                SourceForm.Row(
                    row.id, row.type, row.title, row.subtitle, row.value, row.mark, row.selectable, row.on, row.form, row.secure, row.confirm,
                    row.destructive, row.number, row.minimum, row.maximum, row.step, row.url,
                    row.login?.let { SourceForm.Login(it.method, it.url, it.callbackScheme, it.useEmail, it.signedIn, it.username, it.clearsCookies, it.storageKeys) },
                )
            })
        })
    }
}

/**
 * The native functions of paperback.js, from the bindings of Kotlin. A typed array goes as an Int8Array, which
 * QuickJS gives to Kotlin as a ByteArray, and the bytes of an answer become an ArrayBuffer.
 */
private val NATIVES = """
(function () {
  const signed = (b) => b ? new Int8Array(b.buffer, b.byteOffset, b.byteLength) : null;
  const buffer = (b) => b instanceof Int8Array ? b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength) : b;
  Object.assign(globalThis.__native, {
    userAgent: () => __kt_pb_userAgent(),
    stateGet: (key, secure) => __kt_pb_stateGet(key, secure),
    stateSet: (key, value, secure) => __kt_pb_stateSet(key, value === undefined ? null : value, secure),
    stateReset: () => __kt_pb_stateReset(),
    base64Encode: (bytes) => __kt_pb_base64Encode(signed(bytes)),
    base64Decode: (text) => buffer(__kt_pb_base64Decode(text)),
    digest: (name, bytes, hex) => buffer(__kt_pb_digest(name, signed(bytes), hex)),
    aes: (name, encrypt, key, iv, data, extra) => buffer(__kt_pb_aes(name, encrypt, signed(key), signed(iv), signed(data), signed(extra))),
    decodeEntities: (text) => __kt_pb_decodeEntities(text),
    random: (count) => buffer(__kt_pb_random(count)),
    uuid: () => __kt_pb_uuid(),
  });
})();
""".trimIndent()
