package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SuwayomiTest {
    private val seen = mutableListOf<HttpRequest>()

    /** A fetcher that answers the GraphQL queries of the test by a word in the query. */
    private fun graphQL(answers: Map<String, String>): Fetcher = { request ->
        seen += request
        val body = String(request.body ?: ByteArray(0))
        val key = answers.keys.sortedByDescending { it.length }.firstOrNull { it in body }
        HttpResponse(200, request.url, mapOf("content-type" to "application/json"), (answers[key] ?: """{"errors":[{"message":"unknown query"}]}""").toByteArray())
    }

    /** The input of the last request. */
    private fun lastInput(): JsonObject =
        Json.parseToJsonElement(String(seen.last().body!!)).jsonObject["variables"]!!.jsonObject["input"]!!.jsonObject

    private fun server() = SuwayomiServer("http://pc.local:4567/api/graphql/", graphQL(mapOf(
        "sources {" to """{"data":{"sources":{"nodes":[{"id":"0","name":"Local source","lang":"localsourcelang"},{"id":"2499283573021220255","name":"MangaDex","displayName":"MangaDex (EN)","lang":"en","iconUrl":"/api/v1/extension/icon/mangadex.apk","isNsfw":true,"supportsLatest":true}]}}}""",
        "fetchSourceManga" to """{"data":{"fetchSourceManga":{"hasNextPage":true,"mangas":[{"id":7,"title":"Yotsuba&!","thumbnailUrl":"/api/v1/manga/7/thumbnail"}]}}}""",
        "fetchManga" to """{"data":{"fetchManga":{"manga":{"id":7,"title":"Yotsuba&!","thumbnailUrl":"/api/v1/manga/7/thumbnail","author":"Kiyohiko Azuma","artist":"Kiyohiko Azuma","description":"A girl and her summer.","genre":["Comedy","Slice of Life"],"status":"ONGOING"}},"fetchChapters":{"chapters":[{"id":12,"name":"Chapter 2","chapterNumber":2,"uploadDate":"1790000000000","scanlator":"Group","sourceOrder":0},{"id":11,"name":"Chapter 1","chapterNumber":1,"uploadDate":"1780000000000","scanlator":null,"sourceOrder":1}]}}}""",
        "fetchChapterPages" to """{"data":{"fetchChapterPages":{"pages":["/api/v1/manga/7/chapter/1/page/0","/api/v1/manga/7/chapter/1/page/1"]}}}""",
    )))

    private val info = SourceInfo("2499283573021220255", "MangaDex (EN)", "en", "", code = "http://pc.local:4567", novels = false)

    @Test fun listsTheSourcesOfTheServerButNotItsLocalSource() = runBlocking {
        val sources = server().sources()
        assertEquals(listOf("MangaDex (EN)"), sources.map { it.name })
        assertEquals("http://pc.local:4567/api/v1/extension/icon/mangadex.apk", sources.first().icon)
        assertTrue(sources.first().adult)
        assertEquals("http://pc.local:4567/api/graphql", seen.first().url)
        assertEquals("POST", seen.first().method)
    }

    @Test fun listsSeriesAndReadsTheirChaptersFromTheOldest() = runBlocking {
        val source = SuwayomiSource(info, server())
        val page = source.popular(1)
        assertEquals(OnlinePage(listOf(OnlineSeries("7", "Yotsuba&!", "http://pc.local:4567/api/v1/manga/7/thumbnail")), true), page)
        assertEquals("2499283573021220255", lastInput()["source"]!!.jsonPrimitive.content)
        assertEquals("POPULAR", lastInput()["type"]!!.jsonPrimitive.content)

        val details = source.details(page.items[0])
        assertEquals("Kiyohiko Azuma", details.author)
        assertEquals("Ongoing", details.status)
        assertEquals(listOf("11", "12"), details.chapters.map { it.id })
        assertEquals("2026-09-21", details.chapters[1].date)
        assertEquals("Group", details.chapters[1].group)

        assertEquals(
            listOf("http://pc.local:4567/api/v1/manga/7/chapter/1/page/0", "http://pc.local:4567/api/v1/manga/7/chapter/1/page/1"),
            source.pages(details.chapters[0]),
        )
    }

    @Test fun givesTheMessageOfAGraphQLError() {
        val source = SuwayomiSource(info, SuwayomiServer("http://pc.local:4567", graphQL(emptyMap())))
        val error = assertThrows(SuwayomiException::class.java) { runBlocking { source.popular(1) } }
        assertEquals("The Suwayomi server answered: unknown query", error.message)
    }

    @Test fun refusesAWrongPassword() {
        val server = SuwayomiServer("http://pc.local:4567") { request -> HttpResponse(401, request.url, emptyMap(), ByteArray(0)) }
        val error = assertThrows(SuwayomiException::class.java) { runBlocking { server.sources() } }
        assertEquals("The Suwayomi server answered: The user name or the password is not correct.", error.message)
    }

    @Test fun searchesWithTheFiltersOfAMihonSource() = runBlocking {
        val filters = """{"data":{"source":{"filters":[{"__typename":"HeaderFilter","name":"Note"},{"__typename":"SortFilter","name":"Sort","values":["Popular","Latest"],"sortDefault":{"index":0,"ascending":false}},{"__typename":"SelectFilter","name":"Status","values":["Any","Done"],"selectDefault":0},{"__typename":"GroupFilter","name":"Genres","filters":[{"__typename":"TriStateFilter","name":"Action","triDefault":"IGNORE"},{"__typename":"TriStateFilter","name":"Drama","triDefault":"IGNORE"}]},{"__typename":"GroupFilter","name":"More","filters":[{"__typename":"TextFilter","name":"Author","textDefault":""}]},{"__typename":"CheckBoxFilter","name":"Colour","checkDefault":false}]}}}"""
        val source = SuwayomiSource(info, SuwayomiServer("http://pc.local:4567", graphQL(mapOf(
            "filters {" to filters,
            "fetchSourceManga" to """{"data":{"fetchSourceManga":{"hasNextPage":false,"mangas":[]}}}""",
        ))))
        val options = source.filterOptions()
        // A header does not show. A group of other filters gives each filter.
        assertEquals(listOf("1", "2", "3", "4.0", "5"), options.map { it.id })
        assertEquals(
            SourceOption.Kind.Multi(listOf(SourceOption.Choice("Action", "0"), SourceOption.Choice("Drama", "1")), true),
            options[2].kind,
        )
        source.browse(1, "tea", mapOf(
            "1" to OptionValue.Sort(1, true), "2" to OptionValue.Select("0"), "3" to OptionValue.Multi(listOf("1"), listOf("0")),
            "4.0" to OptionValue.Text("Mina"), "5" to OptionValue.Toggle(true),
        ))
        val input = lastInput()
        assertEquals("SEARCH", input["type"]!!.jsonPrimitive.content)
        assertEquals("tea", input["query"]!!.jsonPrimitive.content)
        // The status keeps its first value, so it does not go.
        val expected = Json.parseToJsonElement(
            """[{"position":1,"sortState":{"ascending":true,"index":1}},{"groupChange":{"position":0,"triState":"EXCLUDE"},"position":3},{"groupChange":{"position":1,"triState":"INCLUDE"},"position":3},{"groupChange":{"position":0,"textState":"Mina"},"position":4},{"checkBoxState":true,"position":5}]""",
        )
        assertEquals(expected, input["filters"]!!.jsonArray)
    }

    @Test fun changesAPreferenceOfAMihonSourceByItsPosition() = runBlocking {
        val preferences = """{"data":{"source":{"preferences":[{"__typename":"SwitchPreference","key":"hd","title":"HD","visible":true,"on":null,"onDefault":true},{"__typename":"EditTextPreference","key":"hidden","title":"Hidden","visible":false,"text":"x","textDefault":""},{"__typename":"ListPreference","key":"server","title":"Server","visible":true,"choice":"b","choiceDefault":"a","entries":["Alpha","Beta"],"entryValues":["a","b"]}]}}}"""
        val source = SuwayomiSource(info, SuwayomiServer("http://pc.local:4567", graphQL(mapOf(
            "preferences {" to preferences,
            "updateSourcePreference" to """{"data":{"updateSourcePreference":{"clientMutationId":null}}}""",
        ))))
        val options = source.settingOptions()
        // A hidden preference does not show.
        assertEquals(listOf("0", "2"), options.map { it.id })
        assertEquals(OptionValue.Toggle(true), options[0].initial)
        assertEquals(OptionValue.Select("b"), source.settingValues()["2"])
        source.setSetting("2", OptionValue.Select("a"))
        val input = lastInput()
        assertEquals(info.id, input["source"]!!.jsonPrimitive.content)
        val change = input["change"]!!.jsonObject
        assertEquals(2, change["position"]!!.jsonPrimitive.content.toInt())
        assertEquals("a", change["listState"]!!.jsonPrimitive.content)
    }
}
