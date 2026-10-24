package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** The name of a tracker for the user. */
val TrackerKind.label: String get() = when (this) {
    TrackerKind.AniList -> "AniList"
    TrackerKind.MyAnimeList -> "MyAnimeList"
}

/** The redirect URI that the user gives to the site when the user registers a client for Moekoma. */
val TrackerKind.redirect: String get() = when (this) {
    TrackerKind.AniList -> "moekoma://anilist"
    TrackerKind.MyAnimeList -> "moekoma://myanimelist"
}

/** A title of a search on a tracker. */
data class TrackerHit(val id: Int, val title: String, val chapters: Int?)

sealed class TrackerException(message: String) : Exception(message) {
    class NotSignedIn : TrackerException("Sign in to the tracker in Settings first.")
    class Refused(val text: String) : TrackerException("The tracker refused the request: $text")
}

/**
 * The calls to AniList or MyAnimeList, as in Asabunko. The user registers a client on the site and gives its ID to the
 * app, because the app has no client of its own. [base] is the base of each URL, which a test changes to a local server.
 */
class Tracker(val kind: TrackerKind, val clientId: String, val token: String?, private val fetcher: Fetcher, private val base: String? = null) {
    private fun url(real: String, test: String): String = base?.let { "${it.trimEnd('/')}/${kind.name.lowercase()}/$test" } ?: real

    /** The page of the sign-in. MyAnimeList needs a code verifier, which the app keeps until the callback. */
    fun signInUrl(verifier: String, state: String): String = when (kind) {
        TrackerKind.AniList -> url("https://anilist.co/api/v2/oauth/authorize", "authorize") + "?client_id=${encode(clientId)}&response_type=token"
        // MyAnimeList takes only the plain method of PKCE.
        TrackerKind.MyAnimeList -> url("https://myanimelist.net/v1/oauth2/authorize", "authorize") + "?" + form(
            linkedMapOf(
                "response_type" to "code", "client_id" to clientId, "code_challenge" to verifier,
                "code_challenge_method" to "plain", "state" to state, "redirect_uri" to kind.redirect,
            ),
            sorted = false,
        )
    }

    /** The token of the callback. AniList puts it in the fragment. MyAnimeList gives a code, which changes into a token. */
    suspend fun token(callback: String, verifier: String): String {
        val uri = URI(callback)
        return when (kind) {
            TrackerKind.AniList -> parameters(uri.rawFragment)["access_token"] ?: throw TrackerException.Refused("no token")
            TrackerKind.MyAnimeList -> {
                val code = parameters(uri.rawQuery)["code"] ?: throw TrackerException.Refused("no code")
                val body = form(
                    mapOf("client_id" to clientId, "code" to code, "code_verifier" to verifier, "grant_type" to "authorization_code", "redirect_uri" to kind.redirect),
                )
                val answer = json(HttpRequest(url("https://myanimelist.net/v1/oauth2/token", "token"), "POST", mapOf("Content-Type" to "application/x-www-form-urlencoded"), body.toByteArray()))
                answer["access_token"]?.jsonPrimitive?.contentOrNull ?: throw TrackerException.Refused("no token")
            }
        }
    }

    /** The titles of the tracker for the words of a search. */
    suspend fun search(words: String): List<TrackerHit> = when (kind) {
        TrackerKind.AniList -> {
            val answer = graphQL(
                "query (\$search: String) { Page(perPage: 10) { media(search: \$search, type: MANGA) { id chapters title { userPreferred } } } }",
                buildJsonObject { put("search", words) },
            )
            val media = answer["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray ?: JsonArray(emptyList())
            media.mapNotNull { item ->
                val entry = item.jsonObject
                val id = entry["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val title = entry["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.contentOrNull.orEmpty()
                TrackerHit(id, title, entry["chapters"].int())
            }
        }
        TrackerKind.MyAnimeList -> {
            val query = form(linkedMapOf("q" to words.take(64), "limit" to "10", "fields" to "num_chapters"), sorted = false)
            val answer = json(signed(HttpRequest(url("https://api.myanimelist.net/v2/manga", "manga") + "?" + query)))
            (answer["data"]?.jsonArray ?: JsonArray(emptyList())).mapNotNull { entry ->
                val node = entry.jsonObject["node"]?.jsonObject ?: return@mapNotNull null
                val id = node["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                // Zero chapters means that the count is not known.
                TrackerHit(id, node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), node["num_chapters"].int()?.takeIf { it > 0 })
            }
        }
    }

    /** Tells the tracker the number of chapters that the user read. */
    suspend fun setProgress(link: TrackLink, chapters: Int, finished: Boolean) {
        when (kind) {
            TrackerKind.AniList -> graphQL(
                "mutation (\$id: Int, \$progress: Int, \$status: MediaListStatus) { SaveMediaListEntry(mediaId: \$id, progress: \$progress, status: \$status) { id progress } }",
                buildJsonObject { put("id", link.id); put("progress", chapters); put("status", if (finished) "COMPLETED" else "CURRENT") },
            )
            TrackerKind.MyAnimeList -> json(
                signed(
                    HttpRequest(
                        url("https://api.myanimelist.net/v2/manga/${link.id}/my_list_status", "manga/${link.id}/my_list_status"), "PATCH",
                        mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                        form(mapOf("num_chapters_read" to chapters.toString(), "status" to if (finished) "completed" else "reading")).toByteArray(),
                    ),
                ),
            )
        }
    }

    // The requests

    private fun signed(request: HttpRequest): HttpRequest {
        val token = token ?: throw TrackerException.NotSignedIn()
        return HttpRequest(request.url, request.method, request.headers + ("Authorization" to "Bearer $token"), request.body)
    }

    private suspend fun graphQL(query: String, variables: JsonObject): JsonObject {
        val body = buildJsonObject { put("query", query); put("variables", variables) }.toString().toByteArray()
        var request = HttpRequest(
            url("https://graphql.anilist.co", "graphql"), "POST",
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"), body,
        )
        // A search works with no token. A change of the list needs one.
        if (token != null || query.startsWith("mutation")) request = signed(request)
        val answer = json(request)
        val message = (answer["errors"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        if (message != null) throw TrackerException.Refused(message)
        return answer
    }

    private suspend fun json(request: HttpRequest): JsonObject {
        val response = fetcher(request)
        val answer = runCatching { Json.parseToJsonElement(response.body.decodeToString()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (response.status !in 200..299) {
            if (response.status == 401) throw TrackerException.NotSignedIn()
            val message = answer["message"]?.text() ?: answer["error"]?.text() ?: "HTTP ${response.status}"
            throw TrackerException.Refused(message)
        }
        return answer
    }

    companion object {
        /**
         * The number of chapters that the tracker gets for a place in a book: the chapters before the open one, or all
         * chapters of a finished book. The number does not go down, because a tracker counts the chapters read.
         */
        fun progress(page: Int, pageCount: Int, finished: Boolean, sent: Int): Int? {
            val read = if (finished) pageCount else maxOf(page, 0)
            return if (read > sent) read else null
        }

        /** A form with only the unreserved characters of RFC 3986, by the names of the values. */
        fun form(values: Map<String, String>, sorted: Boolean = true): String =
            (if (sorted) values.toSortedMap() else values).entries.joinToString("&") { (name, value) -> "$name=${encode(value)}" }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

        private fun parameters(text: String?): Map<String, String> = text.orEmpty().split('&').filter { '=' in it }
            .associate { URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }

        private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

        private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}
