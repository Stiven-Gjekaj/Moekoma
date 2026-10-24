package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** The filters and the simple settings of the Aidoku sources as options. As in Asabunko. */
object AidokuOptions {
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonElement?.flag(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
    private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonElement?.strings(): List<String>? = (this as? JsonArray)?.mapNotNull { it.text() }

    private fun list(json: ByteArray?): List<JsonObject> = json?.let {
        runCatching { Json.parseToJsonElement(String(it, Charsets.UTF_8)) as? JsonArray }.getOrNull()
    }.orEmpty().mapNotNull { it as? JsonObject }

    /** The filters of filters.json that the app can show. A note has no value, so it does not show. */
    fun filters(json: ByteArray?): List<SourceOption> = list(json).mapNotNull(::filter)

    /**
     * The filters that `get_filters` gives, as postcard `Vec<Filter>`. Each filter turns into the same object as an item
     * of filters.json, so one function makes the options of both.
     */
    fun filtersOfPostcard(postcard: ByteArray): List<SourceOption> =
        runCatching { PostcardReader(postcard).let { r -> r.list { readFilter(this) } } }.getOrDefault(emptyList()).mapNotNull(::filter)

    /** One `Filter` of aidoku-rs: the common fields, then the fields of its type. */
    internal fun readFilter(r: PostcardReader): JsonObject {
        val item = linkedMapOf<String, JsonElement>()
        fun put(key: String, value: String?) { if (value != null) item[key] = JsonPrimitive(value) }
        fun put(key: String, value: Boolean?) { if (value != null) item[key] = JsonPrimitive(value) }
        fun put(key: String, value: Float?) { if (value != null) item[key] = JsonPrimitive(value) }
        fun put(key: String, value: List<String>?) { if (value != null) item[key] = JsonArray(value.map(::JsonPrimitive)) }
        put("id", r.option { string() })
        put("title", r.option { string() })
        put("hideFromHeader", r.option { bool() })
        val type = r.string()
        put("type", type)
        when (type) {
            "text" -> put("placeholder", r.option { string() })
            "sort" -> {
                put("canAscend", r.option { bool() })
                put("options", r.list { string() })
                r.option { JsonObject(mapOf("index" to JsonPrimitive(signed()), "ascending" to JsonPrimitive(bool()))) }?.let { item["default"] = it }
            }
            "check" -> {
                put("name", r.option { string() })
                put("canExclude", r.option { bool() })
                put("default", r.option { bool() })
            }
            "select" -> {
                put("isGenre", r.option { bool() })
                put("usesTagStyle", r.option { bool() })
                put("options", r.list { string() })
                put("ids", r.option { list { string() } })
                put("default", r.option { string() })
            }
            "multi-select" -> {
                put("isGenre", r.option { bool() })
                put("canExclude", r.option { bool() })
                put("usesTagStyle", r.option { bool() })
                put("options", r.list { string() })
                put("ids", r.option { list { string() } })
                put("defaultIncluded", r.option { list { string() } })
                put("defaultExcluded", r.option { list { string() } })
            }
            "note" -> put("text", r.string())
            "range" -> {
                put("min", r.option { f32() })
                put("max", r.option { f32() })
                put("decimal", r.option { bool() })
            }
            else -> throw PostcardException("The filter type $type is not known.")
        }
        return JsonObject(item)
    }

    private fun filter(item: JsonObject): SourceOption? {
        val type = item["type"].text() ?: return null
        val title = item["title"].text() ?: item["name"].text() ?: type.replaceFirstChar(Char::uppercaseChar)
        // A filter with no id has its title as the id, or else its type.
        val id = item["id"].text() ?: item["title"].text() ?: type
        val ids = item["ids"].strings()
        val choices = item["options"].strings().orEmpty().mapIndexed { index, label -> SourceOption.Choice(label, ids?.getOrNull(index) ?: label) }
        return when (type) {
            "text" -> SourceOption(id, title, SourceOption.Kind.Text(item["placeholder"].text()), OptionValue.Text(""), type)
            "sort" -> {
                val start = item["default"] as? JsonObject
                SourceOption(
                    id, title, SourceOption.Kind.Sort(choices, item["canAscend"].flag() ?: false),
                    OptionValue.Sort((start?.get("index") as? JsonPrimitive)?.intOrNull ?: 0, start?.get("ascending").flag() ?: false), type,
                )
            }
            "check" -> SourceOption(id, item["name"].text() ?: title, SourceOption.Kind.Toggle, OptionValue.Toggle(item["default"].flag() ?: false), type)
            "select" -> {
                if (choices.isEmpty()) return null
                val text = item["default"].text()
                val start = choices.firstOrNull { it.value == text || it.label == text } ?: choices[0]
                SourceOption(id, title, SourceOption.Kind.Select(choices), OptionValue.Select(start.value), type)
            }
            "multi-select" -> {
                if (choices.isEmpty()) return null
                SourceOption(
                    id, title, SourceOption.Kind.Multi(choices, item["canExclude"].flag() ?: false),
                    OptionValue.Multi(item["defaultIncluded"].strings().orEmpty(), item["defaultExcluded"].strings().orEmpty()), type,
                )
            }
            "range" -> SourceOption(
                id, title, SourceOption.Kind.Range(item["min"].number(), item["max"].number(), item["decimal"].flag() ?: false),
                OptionValue.Range(null, null), type,
            )
            else -> null
        }
    }

    /** The `Vec<FilterValue>` of a search, in postcard. A text with no value does not go. */
    fun encode(values: Map<String, OptionValue>, filters: List<SourceOption>): ByteArray {
        val items = filters.mapNotNull { filter ->
            val value = values[filter.id] ?: filter.initial
            when {
                value is OptionValue.Text && value.text.isEmpty() -> null
                value is OptionValue.Range && value.from == null && value.to == null -> null
                // The meaning of a check with the value 0 is not clear, and a source can read it as "exclude".
                // So a check goes only when the user changed it, as the Aidoku app does.
                value is OptionValue.Toggle && value == filter.initial -> null
                else -> filter to value
            }
        }
        return PostcardWriter.write {
            list(items) { (filter, value) ->
                when (value) {
                    is OptionValue.Text -> { unsigned(0); string(filter.id); string(value.text) }
                    is OptionValue.Sort -> { unsigned(1); string(filter.id); signed(value.index); bool(value.ascending) }
                    is OptionValue.Toggle -> { unsigned(2); string(filter.id); signed(if (value.on) 1 else 0) }
                    is OptionValue.Select -> { unsigned(3); string(filter.id); string(value.choice) }
                    is OptionValue.Multi -> {
                        unsigned(4); string(filter.id)
                        list(value.included) { string(it) }
                        list(value.excluded) { string(it) }
                    }
                    is OptionValue.Range -> {
                        unsigned(5); string(filter.id)
                        option(value.from?.toFloat()) { f32(it) }
                        option(value.to?.toFloat()) { f32(it) }
                    }
                }
            }
        }
    }

    /** The settings of settings.json that the app can show: switches, choices, segments, and text. */
    fun settings(json: ByteArray?): List<SourceOption> {
        val result = mutableListOf<SourceOption>()
        fun visit(item: JsonObject) {
            (item["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.forEach(::visit)
            val type = item["type"].text() ?: return
            val key = item["key"].text() ?: return
            val title = item["title"].text() ?: key
            val titles = item["titles"].strings()
            val choices = item["values"].strings().orEmpty().mapIndexed { index, value -> SourceOption.Choice(titles?.getOrNull(index) ?: value, value) }
            when (type) {
                "switch" -> result += SourceOption(key, title, SourceOption.Kind.Toggle, OptionValue.Toggle(item["default"].flag() ?: false), type)
                "select", "picker" -> if (choices.isNotEmpty()) {
                    result += SourceOption(key, title, SourceOption.Kind.Select(choices), OptionValue.Select(item["default"].text() ?: choices[0].value), type)
                }
                "multi-select" -> if (choices.isNotEmpty()) {
                    result += SourceOption(key, title, SourceOption.Kind.Multi(choices, false), OptionValue.Multi(item["default"].strings().orEmpty()), type)
                }
                "segment" -> {
                    val segments = item["options"].strings().orEmpty().mapIndexed { index, label -> SourceOption.Choice(label, index.toString()) }
                    if (segments.isNotEmpty()) {
                        val start = (item["default"] as? JsonPrimitive)?.intOrNull ?: 0
                        result += SourceOption(key, title, SourceOption.Kind.Select(segments), OptionValue.Select(start.toString()), type)
                    }
                }
                "text" -> result += SourceOption(key, title, SourceOption.Kind.Text(item["placeholder"].text()), OptionValue.Text(item["default"].text().orEmpty()), type)
            }
        }
        list(json).forEach(::visit)
        return result
    }

    /** The value of a setting in the postcard form of the defaults of aidoku-rs. */
    fun encodeSetting(value: OptionValue, setting: SourceOption): ByteArray = PostcardWriter.write {
        when (value) {
            is OptionValue.Toggle -> bool(value.on)
            is OptionValue.Select -> if (setting.raw == "segment") signed(value.choice.toIntOrNull() ?: 0) else string(value.choice)
            is OptionValue.Text -> string(value.text)
            is OptionValue.Multi -> list(value.included) { string(it) }
            is OptionValue.Sort -> signed(value.index)
            is OptionValue.Range -> f32((value.from ?: 0.0).toFloat())
        }
    }

    /** The value of a setting from its postcard form, or null when the bytes do not fit the setting. */
    fun decodeSetting(data: ByteArray, setting: SourceOption): OptionValue? = runCatching {
        val r = PostcardReader(data)
        when (setting.kind) {
            is SourceOption.Kind.Toggle -> OptionValue.Toggle(r.bool())
            is SourceOption.Kind.Select -> if (setting.raw == "segment") OptionValue.Select(r.signed().toString()) else OptionValue.Select(r.string())
            is SourceOption.Kind.Text -> OptionValue.Text(r.string())
            is SourceOption.Kind.Multi -> OptionValue.Multi(r.list { string() })
            is SourceOption.Kind.Sort, is SourceOption.Kind.Range -> null
        }
    }.getOrNull()
}
