package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URI

/** The list of LNReader plugins, such as plugins.min.json. */
object LnReaderIndex {
    @Serializable
    private class Item(val id: String, val name: String, val site: String? = null, val lang: String? = null, val version: String, val url: String, val iconUrl: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(data: ByteArray): List<SourceInfo> = json.decodeFromString(ListSerializer(Item.serializer()), String(data, Charsets.UTF_8)).map { item ->
        SourceInfo(
            id = item.id, name = item.name,
            language = item.lang.orEmpty().trim { it.isWhitespace() || it == '‎' || it == '‏' },
            version = item.version, icon = item.iconUrl?.let(::lenient), code = lenient(item.url), novels = true,
        )
    }

    /** A URL that can hold characters that a URI refuses, such as "[" and "]". */
    fun lenient(text: String): String = text.replace("[", "%5B").replace("]", "%5D").replace(" ", "%20")
}

/**
 * One LNReader plugin in its own JavaScript context, as in Asabunko. The glue is lnreader.js. The settings of the
 * plugin are in [settings], a JSON file, or only in memory when it is null.
 */
class LnReaderPlugin private constructor(
    val info: SourceInfo,
    private val runtime: JsRuntime,
    val site: String?,
    private val paged: Boolean,
    val imageHeaders: Map<String, String>,
    private val settings: File?,
) : AutoCloseable {
    companion object {
        /** The names of the text filters that hold the words of a search, such as the filter "search" of WTR-LAB. */
        val searchFilters = setOf("search", "keyword", "keywords", "query")

        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private class Loaded(val id: String? = null, val name: String? = null, val site: String? = null, val paged: Boolean = false, val imageHeaders: Map<String, String> = emptyMap())

        suspend fun load(info: SourceInfo, code: String, fetcher: Fetcher, settings: File? = null): LnReaderPlugin {
            val runtime = JsRuntime.create(fetcher)
            try {
                runtime.evaluate(JsRuntime.resource("lnreader.js"), "lnreader.js")
                // The plugin reads its settings from the storage, so the saved values go in first.
                settings?.takeIf { it.isFile }?.readText()?.let { saved ->
                    runtime.invoke("(json) => { const v = JSON.parse(json); for (const k of Object.keys(v)) __storage.set(k, v[k]); return true; }", listOf(saved))
                }
                val loaded = runtime.invoke(Loaded.serializer(), "__loadPlugin", listOf(code))
                return LnReaderPlugin(info, runtime, loaded.site, loaded.paged, loaded.imageHeaders, settings)
            } catch (e: Exception) {
                runtime.close()
                throw e
            }
        }

        private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { this !is JsonNull }
    }

    val canShowLatest: Boolean get() = true

    /** A cover can be a path of the site. */
    private fun cover(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        if (text.startsWith("http://") || text.startsWith("https://")) return LnReaderIndex.lenient(text)
        if (text.startsWith("//")) return "https:$text"
        val base = site ?: return null
        return runCatching { URI(base).resolve(LnReaderIndex.lenient(text)).toString() }.getOrNull()
    }

    private fun page(answer: String): OnlinePage {
        val items = (json.parseToJsonElement(answer) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { item ->
            val path = item["path"].text() ?: return@mapNotNull null
            OnlineSeries(path, item["name"].text() ?: path, cover(item["cover"].text()))
        }
        // A plugin gives an empty page at the end of its list.
        return OnlinePage(items, items.isNotEmpty())
    }

    suspend fun popular(page: Int) = page(runtime.invoke("(page) => plugin.popularNovels(page, { showLatestNovels: false, filters: __filters() })", listOf(page)))
    suspend fun latest(page: Int) = page(runtime.invoke("(page) => plugin.popularNovels(page, { showLatestNovels: true, filters: __filters() })", listOf(page)))
    suspend fun search(query: String, page: Int) = page(runtime.invoke("(q, page) => plugin.searchNovels(q, page)", listOf(query, page)))

    private fun chapters(list: JsonElement?): List<JsonObject> = (list as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    suspend fun details(series: OnlineSeries): OnlineDetails {
        val novel = json.parseToJsonElement(runtime.invoke("(path) => plugin.parseNovel(path)", listOf(series.id))).jsonObject
        var chapters = chapters(novel["chapters"])
        // A paged plugin gives the chapters of one page at a time.
        val total = (novel["totalPages"] as? JsonPrimitive)?.doubleOrNull?.toInt()
        if (paged && total != null && (total > 1 || chapters.isEmpty())) {
            chapters = (1..maxOf(total, 1)).flatMap { number ->
                chapters(json.parseToJsonElement(runtime.invoke("(path, page) => plugin.parsePage(path, page)", listOf(series.id, number.toString()))).jsonObject["chapters"])
            }
        }
        val genres = novel["genres"].text().orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return OnlineDetails(
            series = OnlineSeries(series.id, novel["name"].text() ?: series.title, cover(novel["cover"].text()) ?: series.cover),
            author = novel["author"].text()?.ifBlank { null }, summary = novel["summary"].text()?.trim()?.ifEmpty { null },
            status = novel["status"].text()?.ifBlank { null }, genres = genres,
            chapters = chapters.mapIndexedNotNull { index, chapter ->
                val path = chapter["path"].text() ?: return@mapIndexedNotNull null
                OnlineChapter(
                    id = path, title = chapter["name"].text()?.ifBlank { null } ?: "Chapter ${index + 1}",
                    number = (chapter["chapterNumber"] as? JsonPrimitive)?.doubleOrNull, date = chapter["releaseTime"].text(),
                )
            },
            isNovel = true,
        )
    }

    /** The HTML of a chapter. */
    suspend fun chapter(path: String): String = json.decodeFromString(kotlinx.serialization.serializer<String>(), runtime.invoke("(path) => plugin.parseChapter(path)", listOf(path)))

    // Filters and settings

    private suspend fun specs(function: String): List<JsonObject> =
        runCatching { json.parseToJsonElement(runtime.invoke(function)).jsonArray.mapNotNull { it as? JsonObject } }.getOrDefault(emptyList())

    private fun choices(spec: JsonObject) = (spec["options"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
        SourceOption.Choice(it["label"].text().orEmpty(), it["value"].text().orEmpty())
    }

    private fun strings(value: JsonElement?) = (value as? JsonArray).orEmpty().mapNotNull { it.text() }

    suspend fun filterOptions(): List<SourceOption> = specs("__filterSpec").mapNotNull { spec ->
        val key = spec["key"].text() ?: return@mapNotNull null
        val type = spec["type"].text() ?: return@mapNotNull null
        val title = spec["label"].text() ?: key
        val value = spec["value"]
        val choices = choices(spec)
        when (type) {
            "Text" -> SourceOption(key, title, SourceOption.Kind.Text(null), OptionValue.Text(value.text().orEmpty()), type)
            "Switch" -> SourceOption(key, title, SourceOption.Kind.Toggle, OptionValue.Toggle((value as? JsonPrimitive)?.booleanOrNull ?: false), type)
            "Picker" -> if (choices.isEmpty()) null
                else SourceOption(key, title, SourceOption.Kind.Select(choices), OptionValue.Select(value.text() ?: choices[0].value), type)
            "Checkbox" -> SourceOption(key, title, SourceOption.Kind.Multi(choices, false), OptionValue.Multi(strings(value)), type)
            "XCheckbox" -> {
                val both = value as? JsonObject
                SourceOption(key, title, SourceOption.Kind.Multi(choices, true), OptionValue.Multi(strings(both?.get("include")), strings(both?.get("exclude"))), type)
            }
            else -> null
        }
    }

    /** The value of an option in the form that a plugin reads. */
    private fun javaScript(value: OptionValue, raw: String): JsonElement = when (value) {
        is OptionValue.Text -> JsonPrimitive(value.text)
        is OptionValue.Toggle -> JsonPrimitive(value.on)
        is OptionValue.Select -> JsonPrimitive(value.choice)
        is OptionValue.Multi -> if (raw == "XCheckbox") {
            JsonObject(mapOf("include" to JsonArray(value.included.map(::JsonPrimitive)), "exclude" to JsonArray(value.excluded.map(::JsonPrimitive))))
        } else {
            JsonArray(value.included.map(::JsonPrimitive))
        }
        is OptionValue.Sort -> JsonPrimitive(value.index)
        is OptionValue.Range -> JsonObject(mapOf("from" to (value.from?.let(::JsonPrimitive) ?: JsonNull), "to" to (value.to?.let(::JsonPrimitive) ?: JsonNull)))
    }

    /**
     * The list of the plugin with filters. `searchNovels` of LNReader has no filters. A plugin with a text filter for
     * the words of a search gets the words in that filter, and its list does the search with the other filters.
     */
    suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>): OnlinePage {
        val options = filterOptions()
        val values = filters.toMutableMap()
        if (query.isNotEmpty()) {
            val words = options.firstOrNull { it.kind is SourceOption.Kind.Text && it.id in searchFilters } ?: return search(query, page)
            values[words.id] = OptionValue.Text(query)
        }
        val json = JsonObject(options.associate { it.id to javaScript(values[it.id] ?: it.initial, it.raw) })
        return page(runtime.invoke("__browse", listOf(page, json.toString())))
    }

    suspend fun settingOptions(): List<SourceOption> = specs("__settingSpec").mapNotNull { spec ->
        val key = spec["key"].text() ?: return@mapNotNull null
        val type = spec["type"].text() ?: "Text"
        val title = spec["label"].text() ?: key
        val choices = choices(spec)
        when {
            type == "Switch" -> SourceOption(key, title, SourceOption.Kind.Toggle, OptionValue.Toggle(false), type)
            type == "Select" && choices.isNotEmpty() -> SourceOption(key, title, SourceOption.Kind.Select(choices), OptionValue.Select(choices[0].value), type)
            type == "CheckboxGroup" -> SourceOption(key, title, SourceOption.Kind.Multi(choices, false), OptionValue.Multi(emptyList()), type)
            else -> SourceOption(key, title, SourceOption.Kind.Text(null), OptionValue.Text(""), type)
        }
    }

    suspend fun settingValues(): Map<String, OptionValue> = specs("__settingSpec").mapNotNull { spec ->
        val key = spec["key"].text() ?: return@mapNotNull null
        val value = spec["value"]
        key to when {
            value is JsonArray -> OptionValue.Multi(strings(value))
            value is JsonPrimitive && value.booleanOrNull != null && !value.isString -> OptionValue.Toggle(value.booleanOrNull!!)
            value is JsonPrimitive && value.isString -> if (spec["type"].text() == "Select") OptionValue.Select(value.content) else OptionValue.Text(value.content)
            value is JsonPrimitive && value !is JsonNull -> OptionValue.Text(value.content)
            else -> return@mapNotNull null
        }
    }.toMap()

    suspend fun setSetting(id: String, value: OptionValue) {
        val raw = settingOptions().firstOrNull { it.id == id }?.raw.orEmpty()
        val converted = javaScript(value, raw)
        runtime.invoke(
            "(json) => { const v = JSON.parse(json); for (const k of Object.keys(v)) __storage.set(k, v[k]); return true; }",
            listOf(JsonObject(mapOf(id to converted)).toString()),
        )
        val file = settings ?: return
        val saved = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        file.parentFile?.mkdirs()
        file.writeText(JsonObject(saved + (id to converted)).toString())
    }

    override fun close() = runtime.close()
}
