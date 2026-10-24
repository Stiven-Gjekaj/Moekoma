package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/** The tracker client against a fake site that keeps the requests and gives each one an answer, as in Asabunko. */
class TrackerTest {
    private class FakeSite(val answer: (HttpRequest) -> Pair<Int, String>) {
        val requests = mutableListOf<HttpRequest>()
        val fetcher: Fetcher = { request ->
            requests += request
            val (status, body) = answer(request)
            HttpResponse(status, request.url, emptyMap(), body.toByteArray())
        }
    }

    private fun body(request: HttpRequest) = request.body?.decodeToString().orEmpty()

    @Test fun findsATitleOnAniListWithNoToken() = runBlocking {
        val site = FakeSite { 200 to """{"data":{"Page":{"media":[{"id":7,"chapters":40,"title":{"userPreferred":"Sunrise"}},{"id":8,"title":{"userPreferred":"Dusk"}}]}}}""" }
        val hits = Tracker(TrackerKind.AniList, "1", null, site.fetcher).search("sunrise")
        assertEquals(listOf(TrackerHit(7, "Sunrise", 40), TrackerHit(8, "Dusk", null)), hits)
        val request = site.requests.single()
        assertEquals("https://graphql.anilist.co", request.url)
        assertNull(request.headers["Authorization"])
        assertTrue(body(request), "\"search\":\"sunrise\"" in body(request))
    }

    @Test fun sendsTheProgressToAniListWithTheToken() = runBlocking {
        val site = FakeSite { 200 to """{"data":{"SaveMediaListEntry":{"id":1,"progress":3}}}""" }
        Tracker(TrackerKind.AniList, "1", "abc", site.fetcher).setProgress(TrackLink(TrackerKind.AniList, 7, "Sunrise"), 3, finished = false)
        val request = site.requests.single()
        assertEquals("Bearer abc", request.headers["Authorization"])
        val variables = Json.parseToJsonElement(body(request)).jsonObject["variables"]!!.jsonObject
        assertEquals(7, variables["id"]!!.jsonPrimitive.int)
        assertEquals(3, variables["progress"]!!.jsonPrimitive.int)
        assertEquals("CURRENT", variables["status"]!!.jsonPrimitive.content)
    }

    @Test fun refusesAChangeWithNoToken() {
        val site = FakeSite { 200 to "{}" }
        assertThrows(TrackerException.NotSignedIn::class.java) {
            runBlocking { Tracker(TrackerKind.AniList, "1", null, site.fetcher).setProgress(TrackLink(TrackerKind.AniList, 7, "S"), 1, false) }
        }
        assertTrue("No request goes out with no token.", site.requests.isEmpty())
    }

    @Test fun givesTheMessageOfAnAniListError() {
        val site = FakeSite { 200 to """{"errors":[{"message":"Invalid token"}]}""" }
        val error = assertThrows(TrackerException.Refused::class.java) { runBlocking { Tracker(TrackerKind.AniList, "1", "bad", site.fetcher).search("x") } }
        assertEquals("Invalid token", error.text)
    }

    @Test fun readsTheTokenOfAniListFromTheFragment() = runBlocking {
        val tracker = Tracker(TrackerKind.AniList, "1", null, FakeSite { 200 to "{}" }.fetcher)
        assertEquals("xyz", tracker.token("moekoma://anilist#access_token=xyz&token_type=Bearer&expires_in=3600", ""))
        assertEquals("https://anilist.co/api/v2/oauth/authorize?client_id=1&response_type=token", tracker.signInUrl("", ""))
    }

    @Test fun changesTheCodeOfMyAnimeListIntoAToken() = runBlocking {
        val site = FakeSite { 200 to """{"access_token":"mal-token","token_type":"Bearer"}""" }
        val tracker = Tracker(TrackerKind.MyAnimeList, "cid", null, site.fetcher)
        val verifier = "v".repeat(50)
        val items = URI(tracker.signInUrl(verifier, "s1")).rawQuery.split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        assertEquals("MyAnimeList takes the plain method, so the challenge is the verifier.", verifier, items["code_challenge"])
        assertEquals("plain", items["code_challenge_method"])
        assertEquals("moekoma://myanimelist", items["redirect_uri"])
        assertEquals("mal-token", tracker.token("moekoma://myanimelist?code=c0de&state=s1", verifier))
        val request = site.requests.single()
        assertEquals("https://myanimelist.net/v1/oauth2/token", request.url)
        assertEquals("client_id=cid&code=c0de&code_verifier=$verifier&grant_type=authorization_code&redirect_uri=moekoma%3A%2F%2Fmyanimelist", body(request))
    }

    @Test fun searchesAndPatchesMyAnimeListAtATestServer() = runBlocking {
        val site = FakeSite { request ->
            if (request.method == "PATCH") 200 to """{"status":"reading","num_chapters_read":4}"""
            else 200 to """{"data":[{"node":{"id":21,"title":"Sunrise","num_chapters":0}}]}"""
        }
        val tracker = Tracker(TrackerKind.MyAnimeList, "cid", "t", site.fetcher, base = "http://127.0.0.1:9/trackers")
        assertEquals("Zero chapters means that the count is not known.", listOf(TrackerHit(21, "Sunrise", null)), tracker.search("Sunrise"))
        tracker.setProgress(TrackLink(TrackerKind.MyAnimeList, 21, "Sunrise"), 4, finished = true)
        val (search, patch) = site.requests
        assertEquals("/trackers/myanimelist/manga", URI(search.url).path)
        assertEquals("Bearer t", search.headers["Authorization"])
        assertEquals("/trackers/myanimelist/manga/21/my_list_status", URI(patch.url).path)
        assertEquals("num_chapters_read=4&status=completed", body(patch))
    }

    @Test fun treatsAnExpiredTokenAsSignedOut() {
        val site = FakeSite { 401 to """{"error":"invalid_token"}""" }
        assertThrows(TrackerException.NotSignedIn::class.java) { runBlocking { Tracker(TrackerKind.MyAnimeList, "cid", "old", site.fetcher).search("x") } }
    }

    @Test fun sendsOnlyANumberThatGoesUp() {
        assertEquals("The open chapter is not read yet.", 3, Tracker.progress(3, 10, finished = false, sent = 0))
        assertNull(Tracker.progress(3, 10, finished = false, sent = 3))
        assertNull("A step back does not go to the tracker.", Tracker.progress(1, 10, finished = false, sent = 3))
        assertEquals(10, Tracker.progress(9, 10, finished = true, sent = 3))
    }
}
