package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset

class SuwayomiException(message: String) : Exception("The Suwayomi server answered: $message")

/**
 * A Suwayomi server, which runs the extensions of Mihon on a computer and gives them with GraphQL. Each source of the
 * server is one online source of the app. As in Asabunko. The [fetcher] adds the user name and the password of the
 * server, when it has them.
 */
class SuwayomiServer(base: String, private val fetcher: Fetcher) {
    val base: String = run {
        var text = base.trimEnd('/')
        for (suffix in listOf("/api/graphql", "/api/v1")) if (text.endsWith(suffix)) text = text.removeSuffix(suffix)
        text
    }

    @Serializable
    private class Problem(val message: String)

    @Serializable
    private class Reply(val data: JsonElement? = null, val errors: List<Problem>? = null)

    internal suspend fun <T> query(serializer: KSerializer<T>, text: String, variables: JsonObject = JsonObject(emptyMap())): T {
        val body = buildJsonObject {
            put("query", text)
            put("variables", variables)
        }
        val response = fetcher(HttpRequest(
            url = "$base/api/graphql", method = "POST",
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body = body.toString().toByteArray(),
        ))
        if (response.status !in 200..299) {
            throw SuwayomiException(if (response.status == 401) "The user name or the password is not correct." else "HTTP ${response.status}")
        }
        val reply = json.decodeFromString(Reply.serializer(), String(response.body, Charsets.UTF_8))
        reply.errors?.firstOrNull()?.let { throw SuwayomiException(it.message) }
        val data = reply.data?.takeIf { it is JsonObject } ?: throw SuwayomiException("No data")
        return json.decodeFromJsonElement(serializer, data)
    }

    /** A path of the server, such as "/api/v1/manga/1/thumbnail", as a full URL. */
    fun absolute(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return base + (if (path.startsWith("/")) "" else "/") + path
    }

    @Serializable
    private class Sources(val sources: Nodes) {
        @Serializable class Nodes(val nodes: List<Node>)

        @Serializable
        class Node(
            val id: String, val name: String, val displayName: String? = null, val lang: String,
            val iconUrl: String? = null, val isNsfw: Boolean? = null, val supportsLatest: Boolean? = null,
        )
    }

    /** The sources of the extensions that the server has. The local source of the server, "0", is not here. */
    suspend fun sources(): List<SourceInfo> =
        query(Sources.serializer(), "query { sources { nodes { id name displayName lang iconUrl isNsfw supportsLatest } } }")
            .sources.nodes.filter { it.id != "0" }.map { node ->
                SourceInfo(
                    id = node.id, name = node.displayName ?: node.name, language = node.lang, version = "",
                    icon = absolute(node.iconUrl), code = base, novels = false, adult = node.isNsfw ?: false, hasLatest = node.supportsLatest ?: true,
                )
            }

    internal companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}

/** One source of a Suwayomi server. */
class SuwayomiSource(val info: SourceInfo, private val server: SuwayomiServer) {
    val canShowLatest: Boolean get() = info.hasLatest

    @Serializable
    private class Fetched(val fetchSourceManga: Payload) {
        @Serializable class Payload(val hasNextPage: Boolean, val mangas: List<Manga>)
        @Serializable class Manga(val id: Int, val title: String, val thumbnailUrl: String? = null)
    }

    private suspend fun list(type: String, page: Int, query: String? = null, filters: JsonArray? = null): OnlinePage {
        // The input goes as one object. Its type name is in the code of the server.
        val input = buildJsonObject {
            put("source", info.id)
            put("type", type)
            put("page", page)
            if (query != null) put("query", query)
            if (filters != null) put("filters", filters)
        }
        val reply = server.query(Fetched.serializer(), """
            mutation (${'$'}input: FetchSourceMangaInput!) {
              fetchSourceManga(input: ${'$'}input) { hasNextPage mangas { id title thumbnailUrl } }
            }
        """.trimIndent(), buildJsonObject { put("input", input) })
        val items = reply.fetchSourceManga.mangas.map { OnlineSeries(it.id.toString(), it.title, server.absolute(it.thumbnailUrl)) }
        return OnlinePage(items, reply.fetchSourceManga.hasNextPage)
    }

    suspend fun popular(page: Int) = list("POPULAR", page)
    suspend fun latest(page: Int) = list("LATEST", page)
    suspend fun search(query: String, page: Int) = list("SEARCH", page, query)

    // The filters

    /** A filter of a Mihon source, as the server gives it. A field "default" has another type in each kind, so each kind has its own name for it. */
    @Serializable
    private class Filter(
        @SerialName("__typename") val type: String,
        val name: String? = null,
        val values: List<String>? = null,
        val selectDefault: Int? = null,
        val textDefault: String? = null,
        val checkDefault: Boolean? = null,
        val triDefault: String? = null,
        val sortDefault: Sort? = null,
        val filters: List<Filter>? = null,
    ) {
        @Serializable class Sort(val index: Int, val ascending: Boolean)
    }

    @Serializable
    private class Filters(val source: Source) {
        @Serializable class Source(val filters: List<Filter>)
    }

    /**
     * The filters of the source as options. The ID of an option is the position of its filter, and "3.1" is the filter
     * 1 in the group 3. A group of checks is one choice of many values.
     */
    suspend fun filterOptions(): List<SourceOption> {
        val reply = runCatching {
            server.query(Filters.serializer(), """
                query (${'$'}id: LongString!) { source(id: ${'$'}id) { filters { $FILTER_FIELDS ... on GroupFilter { name filters { $FILTER_FIELDS } } } } }
            """.trimIndent(), buildJsonObject { put("id", info.id) })
        }.getOrNull() ?: return emptyList()
        val options = mutableListOf<SourceOption>()
        reply.source.filters.forEachIndexed { index, filter ->
            if (filter.type == "GroupFilter") {
                val children = filter.filters.orEmpty()
                if (children.isNotEmpty() && children.all { it.type == "CheckBoxFilter" || it.type == "TriStateFilter" }) {
                    val choices = children.mapIndexed { i, child -> SourceOption.Choice(child.name.orEmpty(), i.toString()) }
                    val included = children.indices.filter { children[it].checkDefault == true || children[it].triDefault == "INCLUDE" }.map(Int::toString)
                    val excluded = children.indices.filter { children[it].triDefault == "EXCLUDE" }.map(Int::toString)
                    // The kind of each child, one letter for each: "c" for a check and "t" for a three-state.
                    val kinds = children.joinToString("") { if (it.type == "TriStateFilter") "t" else "c" }
                    options += SourceOption(
                        index.toString(), filter.name.orEmpty(), SourceOption.Kind.Multi(choices, 't' in kinds),
                        OptionValue.Multi(included, excluded), "group:$kinds",
                    )
                } else {
                    children.forEachIndexed { inner, child -> option(child, "$index.$inner")?.let(options::add) }
                }
            } else {
                option(filter, index.toString())?.let(options::add)
            }
        }
        return options
    }

    private fun option(filter: Filter, id: String): SourceOption? {
        val name = filter.name.orEmpty()
        val choices = filter.values.orEmpty().mapIndexed { i, value -> SourceOption.Choice(value, i.toString()) }
        return when (filter.type) {
            "SelectFilter" -> if (choices.isEmpty()) null
                else SourceOption(id, name, SourceOption.Kind.Select(choices), OptionValue.Select((filter.selectDefault ?: 0).toString()), "select")
            "TextFilter" -> SourceOption(id, name, SourceOption.Kind.Text(null), OptionValue.Text(filter.textDefault.orEmpty()), "text")
            "CheckBoxFilter" -> SourceOption(id, name, SourceOption.Kind.Toggle, OptionValue.Toggle(filter.checkDefault ?: false), "check")
            "TriStateFilter" -> {
                val states = listOf("Any" to "IGNORE", "Include" to "INCLUDE", "Exclude" to "EXCLUDE").map { SourceOption.Choice(it.first, it.second) }
                SourceOption(id, name, SourceOption.Kind.Select(states), OptionValue.Select(filter.triDefault ?: "IGNORE"), "tri")
            }
            "SortFilter" -> if (choices.isEmpty()) null
                else SourceOption(
                    id, name, SourceOption.Kind.Sort(choices, true),
                    OptionValue.Sort(filter.sortDefault?.index ?: 0, filter.sortDefault?.ascending ?: false), "sort",
                )
            else -> null
        }
    }

    suspend fun browse(page: Int, query: String, filters: Map<String, OptionValue>): OnlinePage {
        if (filters.isEmpty()) return if (query.isEmpty()) popular(page) else search(query, page)
        return list("SEARCH", page, query, changes(filters, filterOptions()))
    }

    // The settings

    /** A preference of a Mihon source. Each kind has its own names for its value and its default. */
    @Serializable
    private class Preference(
        @SerialName("__typename") val type: String,
        val key: String? = null,
        val title: String? = null,
        val visible: Boolean? = null,
        val on: Boolean? = null,
        val onDefault: Boolean? = null,
        val text: String? = null,
        val textDefault: String? = null,
        val choice: String? = null,
        val choiceDefault: String? = null,
        val choices: List<String>? = null,
        val choicesDefault: List<String>? = null,
        val entries: List<String>? = null,
        val entryValues: List<String>? = null,
    )

    @Serializable
    private class Preferences(val source: Source) {
        @Serializable class Source(val preferences: List<Preference>)
    }

    private suspend fun preferences(): List<Preference> = runCatching {
        server.query(Preferences.serializer(), """
            query (${'$'}id: LongString!) { source(id: ${'$'}id) { preferences {
              __typename
              ... on SwitchPreference { key title visible on: currentValue onDefault: default }
              ... on CheckBoxPreference { key title visible on: currentValue onDefault: default }
              ... on EditTextPreference { key title visible text: currentValue textDefault: default }
              ... on ListPreference { key title visible choice: currentValue choiceDefault: default entries entryValues }
              ... on MultiSelectListPreference { key title visible choices: currentValue choicesDefault: default entries entryValues }
            } } }
        """.trimIndent(), buildJsonObject { put("id", info.id) }).source.preferences
    }.getOrDefault(emptyList())

    /** The settings of the source. The ID of a setting is the position of its preference, which the server needs to change it. */
    suspend fun settingOptions(): List<SourceOption> = preferences().mapIndexedNotNull { index, preference ->
        if (preference.visible == false) return@mapIndexedNotNull null
        val id = index.toString()
        val title = preference.title ?: preference.key.orEmpty()
        val choices = preference.entries.orEmpty().zip(preference.entryValues.orEmpty()).map { SourceOption.Choice(it.first, it.second) }
        when (preference.type) {
            "SwitchPreference", "CheckBoxPreference" ->
                SourceOption(id, title, SourceOption.Kind.Toggle, OptionValue.Toggle(preference.onDefault ?: false), preference.type)
            "EditTextPreference" ->
                SourceOption(id, title, SourceOption.Kind.Text(null), OptionValue.Text(preference.textDefault.orEmpty()), preference.type)
            "ListPreference" -> if (choices.isEmpty()) null
                else SourceOption(id, title, SourceOption.Kind.Select(choices), OptionValue.Select(preference.choiceDefault ?: choices[0].value), preference.type)
            "MultiSelectListPreference" -> if (choices.isEmpty()) null
                else SourceOption(id, title, SourceOption.Kind.Multi(choices, false), OptionValue.Multi(preference.choicesDefault.orEmpty()), preference.type)
            else -> null
        }
    }

    suspend fun settingValues(): Map<String, OptionValue> = buildMap {
        preferences().forEachIndexed { index, preference ->
            val id = index.toString()
            when (preference.type) {
                "SwitchPreference", "CheckBoxPreference" -> preference.on?.let { put(id, OptionValue.Toggle(it)) }
                "EditTextPreference" -> preference.text?.let { put(id, OptionValue.Text(it)) }
                "ListPreference" -> preference.choice?.let { put(id, OptionValue.Select(it)) }
                "MultiSelectListPreference" -> preference.choices?.let { put(id, OptionValue.Multi(it)) }
            }
        }
    }

    @Serializable
    private class Changed(val updateSourcePreference: JsonElement? = null)

    suspend fun setSetting(id: String, value: OptionValue) {
        val position = id.toIntOrNull() ?: return
        val option = settingOptions().firstOrNull { it.id == id } ?: return
        val change = buildJsonObject {
            put("position", position)
            when (value) {
                is OptionValue.Toggle -> put(if (option.raw == "CheckBoxPreference") "checkBoxState" else "switchState", value.on)
                is OptionValue.Text -> put("editTextState", value.text)
                is OptionValue.Select -> put("listState", value.choice)
                is OptionValue.Multi -> put("multiSelectState", JsonArray(value.included.map(::JsonPrimitive)))
                is OptionValue.Sort, is OptionValue.Range -> return
            }
        }
        runCatching {
            server.query(Changed.serializer(), """
                mutation (${'$'}input: UpdateSourcePreferenceInput!) { updateSourcePreference(input: ${'$'}input) { clientMutationId } }
            """.trimIndent(), buildJsonObject { put("input", buildJsonObject { put("source", info.id); put("change", change) }) })
        }
    }

    // A series

    @Serializable
    private class Details(val fetchManga: MangaPayload, val fetchChapters: ChaptersPayload) {
        @Serializable class MangaPayload(val manga: Full)
        @Serializable class ChaptersPayload(val chapters: List<Chapter>)

        @Serializable
        class Full(
            val id: Int, val title: String, val thumbnailUrl: String? = null, val author: String? = null, val artist: String? = null,
            val description: String? = null, val genre: List<String>? = null, val status: String? = null,
        )

        @Serializable
        class Chapter(
            val id: Int, val name: String, val chapterNumber: Double? = null, val uploadDate: String? = null,
            val scanlator: String? = null, val sourceOrder: Int,
        )
    }

    suspend fun details(series: OnlineSeries): OnlineDetails {
        val id = series.id.toIntOrNull() ?: throw SuwayomiException("The ID ${series.id} is not a number.")
        val reply = server.query(Details.serializer(), """
            mutation (${'$'}id: Int!) {
              fetchManga(input: { id: ${'$'}id }) { manga { id title thumbnailUrl author artist description genre status } }
              fetchChapters(input: { mangaId: ${'$'}id }) { chapters { id name chapterNumber uploadDate scanlator sourceOrder } }
            }
        """.trimIndent(), buildJsonObject { put("id", id) })
        val manga = reply.fetchManga.manga
        // A source gives the newest chapter first, with the lowest source order.
        val chapters = reply.fetchChapters.chapters.sortedByDescending { it.sourceOrder }.map { chapter ->
            OnlineChapter(
                id = chapter.id.toString(), title = chapter.name,
                number = chapter.chapterNumber?.takeIf { it >= 0 },
                date = chapter.uploadDate?.toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() },
                group = chapter.scanlator,
            )
        }
        val status = manga.status?.split('_')?.joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::uppercaseChar) }
        return OnlineDetails(
            series = OnlineSeries(series.id, manga.title, server.absolute(manga.thumbnailUrl) ?: series.cover),
            author = listOfNotNull(manga.author, manga.artist).filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { null },
            summary = manga.description?.ifBlank { null },
            status = status?.takeIf { it != "Unknown" },
            genres = manga.genre.orEmpty(),
            chapters = chapters,
            isNovel = false,
        )
    }

    @Serializable
    private class Pages(val fetchChapterPages: Payload) {
        @Serializable class Payload(val pages: List<String>)
    }

    /** The URLs of the pages of a chapter. The images of the server need the password of the server too. */
    suspend fun pages(chapter: OnlineChapter): List<String> {
        val id = chapter.id.toIntOrNull() ?: throw SuwayomiException("The ID ${chapter.id} is not a number.")
        val reply = server.query(
            Pages.serializer(), "mutation (\$id: Int!) { fetchChapterPages(input: { chapterId: \$id }) { pages } }",
            buildJsonObject { put("id", id) },
        )
        return reply.fetchChapterPages.pages.mapNotNull(server::absolute)
    }

    companion object {
        private const val FILTER_FIELDS = """
            __typename
            ... on HeaderFilter { name }
            ... on SelectFilter { name values selectDefault: default }
            ... on TextFilter { name textDefault: default }
            ... on CheckBoxFilter { name checkDefault: default }
            ... on TriStateFilter { name triDefault: default }
            ... on SortFilter { name values sortDefault: default { index ascending } }
        """

        /** The changes of the filters for `fetchSourceManga`. Only a filter that the user changed goes. */
        fun changes(values: Map<String, OptionValue>, options: List<SourceOption>): JsonArray = buildJsonArray {
            for (option in options) {
                val value = values[option.id] ?: continue
                if (value == option.initial) continue
                val path = option.id.split('.').mapNotNull(String::toIntOrNull)
                // A filter in a group goes in a change of its group.
                fun wrap(change: JsonObject): JsonObject = if (path.size == 2) {
                    buildJsonObject { put("position", path[0]); put("groupChange", JsonObject(mapOf("position" to JsonPrimitive(path[1])) + change)) }
                } else {
                    JsonObject(mapOf("position" to JsonPrimitive(path[0])) + change)
                }
                fun one(key: String, element: JsonElement) = add(wrap(JsonObject(mapOf(key to element))))
                when (value) {
                    is OptionValue.Select -> if (option.raw == "tri") one("triState", JsonPrimitive(value.choice))
                        else one("selectState", JsonPrimitive(value.choice.toIntOrNull() ?: 0))
                    is OptionValue.Text -> one("textState", JsonPrimitive(value.text))
                    is OptionValue.Toggle -> one("checkBoxState", JsonPrimitive(value.on))
                    is OptionValue.Sort -> one("sortState", buildJsonObject { put("index", value.index); put("ascending", value.ascending) })
                    is OptionValue.Multi -> {
                        val kinds = option.raw.removePrefix("group:")
                        val start = option.initial as? OptionValue.Multi ?: continue
                        val kind = option.kind as? SourceOption.Kind.Multi ?: continue
                        for (choice in kind.choices) {
                            val child = choice.value.toIntOrNull()?.takeIf { it in kinds.indices } ?: continue
                            fun state(m: OptionValue.Multi) = when (choice.value) {
                                in m.included -> "INCLUDE"
                                in m.excluded -> "EXCLUDE"
                                else -> "IGNORE"
                            }
                            val now = state(value)
                            if (now == state(start)) continue
                            val inner = buildJsonObject {
                                put("position", child)
                                if (kinds[child] == 't') put("triState", now) else put("checkBoxState", now == "INCLUDE")
                            }
                            add(buildJsonObject { put("position", path[0]); put("groupChange", inner) })
                        }
                    }
                    is OptionValue.Range -> continue
                }
            }
        }
    }
}
