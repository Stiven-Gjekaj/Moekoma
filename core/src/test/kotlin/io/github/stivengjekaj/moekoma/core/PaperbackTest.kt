package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PaperbackTest {
    /** An extension in the form of the Paperback toolchain, as in the tests of Asabunko: an IIFE that sets the global source. */
    private val bundle = PaperbackTest::class.java.getResource("/paperback/quiet.js")!!.readText()

    private val info = SourceInfo("Quiet", "Quiet", "en", "1.0.0", code = "https://repo.test/Quiet/index.js", novels = false)

    private fun encrypted(text: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("0123456789abcdef".toByteArray(), "AES"), IvParameterSpec("fedcba9876543210".toByteArray()))
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray()))
    }

    private fun state(): File = kotlin.io.path.createTempFile("paperback", ".json").toFile().apply { delete(); deleteOnExit() }

    private suspend fun load(seen: MutableList<HttpRequest>, state: File): PaperbackExtension {
        val fetcher: Fetcher = { request ->
            seen += request
            val body = when {
                "popular?page=1" in request.url -> """[{"id":"m1","title":"Morning Tea"},{"id":"m2","title":"Rain Shop"}]"""
                "latest" in request.url -> """[{"id":"m3","title":"New"}]"""
                else -> "[]"
            }
            HttpResponse(200, request.url, mapOf("content-type" to "application/json", "set-cookie" to "session=abc; Expires=Wed, 21 Oct 2099 07:28:00 GMT; Path=/"), body.toByteArray())
        }
        return PaperbackExtension.load(info, bundle, state, "Test", fetcher)
    }

    @Test fun pagesWithTheMetadataAndRunsTheInterceptorsInOrder() = runBlocking {
        val seen = mutableListOf<HttpRequest>()
        val state = state()
        val source = load(seen, state)
        val first = source.popular(1)
        assertEquals(listOf("Morning Tea", "Rain Shop"), first.items.map { it.title })
        assertTrue(first.hasMore)
        assertEquals(OnlinePage(emptyList(), false), source.popular(2))
        assertTrue(source.canShowLatest)
        assertEquals(listOf("m3"), source.latest(1).items.map { it.id })
        val request = seen.first()
        assertEquals("https://quiet.test/", request.headers.entries.first { it.key.equals("referer", true) }.value)
        assertEquals("after-referer", request.headers.entries.first { it.key.equals("x-second", true) }.value)
        assertEquals("https://quiet.test/", source.imageRequest("https://quiet.test/c/m1.jpg").headers.entries.first { it.key.equals("referer", true) }.value)
        // The cookie of the answer stays in the state, with its date.
        val saved = state.readText()
        assertTrue(saved, "session" in saved)
        assertTrue(saved, "\$date" in saved)
        source.close()
    }

    @Test fun searchesWithTheFiltersOfTheExtension() = runBlocking {
        val source = load(mutableListOf(), state())
        val filters = source.filterOptions()
        assertEquals(listOf("sort", "genre"), filters.map { it.id })
        assertEquals(SourceOption.Kind.Select(listOf(SourceOption.Choice("Newest", "new"), SourceOption.Choice("Top", "top"))), filters[0].kind)
        assertEquals(SourceOption.Kind.Multi(listOf(SourceOption.Choice("Calm", "calm"), SourceOption.Choice("Loud", "loud")), true), filters[1].kind)
        val page = source.browse(1, "tea", mapOf("sort" to OptionValue.Select("top"), "genre" to OptionValue.Multi(listOf("calm"), listOf("loud"))))
        assertEquals("Found tea with genre=calm:included,loud:excluded;sort=top", page.items.first().title)
        assertEquals("Found tea", source.browse(1, "tea", emptyMap()).items.first().title)
        source.close()
    }

    @Test fun showsTheSettingsFormAndDoesTheActionsOfItsRows() = runBlocking {
        val state = state()
        val source = load(mutableListOf(), state)
        assertTrue(source.hasSettingsForm())
        val root = source.settingsForm()!!
        assertFalse(root.submit)
        assertEquals(listOf("main", "reset"), root.sections.map { it.id })
        assertEquals("Main", root.sections[0].header)
        assertEquals("Changes go at once.", root.sections[0].footer)
        // The hidden row does not show.
        assertEquals(listOf("adult", "name", "languages", "login"), root.sections[0].rows.map { it.id })
        val k = listOf(SourceForm.Row.Kind.Toggle, SourceForm.Row.Kind.Input, SourceForm.Row.Kind.Link, SourceForm.Row.Kind.Login)
        assertEquals(k, root.sections[0].rows.map { it.kind })
        assertEquals(false, root.sections[0].rows[0].on)

        var form = source.formAction(root.id, "adult", FormInput.Toggle(true))
        assertEquals(true, form.sections[0].rows[0].on)
        form = source.formAction(root.id, "name", FormInput.Text("Mina"))
        assertEquals("Mina", form.sections[0].rows[1].value)
        assertTrue("Mina" in state.readText())

        // A child form keeps its changes until the user saves it.
        val link = form.sections[0].rows[2].form!!
        assertEquals("en", form.sections[0].rows[2].value)
        var child = source.form(link)
        assertTrue(child.submit)
        assertEquals(listOf("included", null), child.sections[0].rows.map { it.mark })
        source.formAction(link, "ja", FormInput.Tap)
        child = source.formAction(link, "en", FormInput.Tap)
        assertEquals(listOf(null, "included"), child.sections[0].rows.map { it.mark })
        assertEquals("en", source.form(root.id).sections[0].rows[2].value)
        source.submitForm(link)
        assertEquals("ja", source.form(root.id).sections[0].rows[2].value)

        // The reset button clears the state, and the form shows the values from the start.
        form = source.formAction(root.id, "reset", FormInput.Tap)
        assertEquals(false, form.sections[0].rows[0].on)
        assertEquals("", form.sections[0].rows[1].value)
        assertThrows(JsException::class.java) { runBlocking { source.formAction(root.id, "secret", FormInput.Tap) } }
        source.close()
    }

    @Test fun signsInWithOAuthAndPkce() = runBlocking {
        val seen = mutableListOf<HttpRequest>()
        val state = state()
        val fetcher: Fetcher = { request ->
            seen += request
            HttpResponse(200, request.url, mapOf("content-type" to "application/json"), """{"access_token":"access-1","refresh_token":"refresh-1"}""".toByteArray())
        }
        val source = PaperbackExtension.load(info, bundle, state, "Test", fetcher)
        val root = source.settingsForm()!!
        val login = root.sections[0].rows.first { it.id == "login" }.login!!
        assertEquals("oauth", login.method)
        assertEquals("quiet", login.callbackScheme)
        val page = URI(login.url)
        fun query(name: String) = page.rawQuery.split('&').map { it.split('=', limit = 2) }.firstOrNull { it[0] == name }?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
        assertEquals("auth.quiet.test", page.host)
        assertEquals("code", query("response_type"))
        assertEquals("quiet", query("client_id"))
        assertEquals("read list", query("scope"))
        assertEquals("S256", query("code_challenge_method"))
        val challenge = query("code_challenge")!!
        val stateValue = query("state")!!
        // The verifier stays the same until the sign-in ends.
        assertEquals(login.url, source.form(root.id).sections[0].rows.first { it.id == "login" }.login?.url)

        source.formAction(root.id, "login", FormInput.Callback("quiet://signed-in?code=abc&state=$stateValue"))
        val request = seen.last()
        assertEquals("https://auth.quiet.test/token", request.url)
        assertEquals("POST", request.method)
        val grant = String(request.body!!).split('&').associate { part ->
            val pair = part.split('=', limit = 2).map { URLDecoder.decode(it, "UTF-8") }
            pair[0] to pair.getOrElse(1) { "" }
        }
        assertEquals("authorization_code", grant["grant_type"])
        assertEquals("abc", grant["code"])
        assertEquals("quiet://signed-in", grant["redirect_uri"])
        val verifier = grant["code_verifier"]!!
        assertEquals(64, verifier.length)
        // The challenge of the page is the SHA-256 of the verifier.
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        assertEquals(expected, challenge)
        // The extension gets the refresh token and the access token.
        assertTrue("refresh-1|access-1" in state.readText())
        assertThrows(JsException::class.java) {
            runBlocking { source.formAction(root.id, "login", FormInput.Callback("quiet://signed-in?code=abc&state=other")) }
        }
        source.close()
    }

    @Test fun runsAPageInAWebViewThatTheAppGives() = runBlocking {
        var asked = ""
        val source = PaperbackExtension.load(info, bundle, state(), "Test", fixedFetcher(emptyMap())) { input ->
            asked = input
            """{"result":"page-seen=yes","cookies":[{"name":"seen","value":"yes","domain":"quiet.test","path":"/"}]}"""
        }
        assertEquals("page-seen=yes with seen", source.search("web", 1).items.first().title)
        assertTrue(asked, "\"inject\":\"return window.__answer\"" in asked)
        source.close()
    }

    @Test fun readsTheSeriesTheChaptersAndThePagesWithTheStateAndTheCrypto() = runBlocking {
        val state = state()
        state.writeText("""{"plain":{"secret":"\"${encrypted("chapter-one")}\""},"secure":{}}""")
        val source = load(mutableListOf(), state)
        val details = source.details(OnlineSeries("m1", ""))
        assertEquals("Morning Tea", details.series.title)
        assertEquals("Warm.", details.summary)
        assertEquals(listOf("Calm"), details.genres)
        assertEquals(listOf("Ch. 1: Kettle", "Ch. 2"), details.chapters.map { it.title })
        assertEquals("2026-10-01", details.chapters.first().date)
        val (html, pages) = source.content(details.chapters[0], details.series)
        assertEquals(null, html)
        assertEquals(listOf("https://quiet.test/chapter-one/1.jpg", "https://quiet.test/chapter-one/2.jpg"), pages.map { it.url })
        assertEquals("https://quiet.test/", pages.first().headers.entries.first { it.key.equals("referer", true) }.value)
        assertEquals(listOf("Found rain"), source.search("rain", 1).items.map { it.title })
        source.close()
    }

    @Test fun removesTheSpacesAndTheInvisibleMarksAtTheEndsOfATitle() {
        assertEquals("Since I'm Interested In Your Hair", cleanTitle("Since I'm Interested In Your Hair       \u200D"))
        // A mark in the middle of a title stays.
        assertEquals("A\u200DB", cleanTitle("\uFEFF A\u200DB "))
    }

    @Test fun readsTheRepositoryAndSkipsTheTrackers() {
        val json = """{"buildTime":"x","builtWith":{"toolchain":"1","types":"1"},"repository":{"name":"Inkdex","description":""},"sources":[{"id":"Toonily","name":"Toonily","description":"","version":"1.0.1","icon":"icon.png","language":"en","contentRating":"ADULT","badges":[],"capabilities":[1,16,4,32,64],"developers":[]},{"id":"AniList","name":"AniList","description":"","version":"1","icon":"icon.png","language":"en","contentRating":"SAFE","badges":[],"capabilities":[4,2,32,64,8],"developers":[]}]}"""
        val list = PaperbackIndex.parse(json.toByteArray(), "https://inkdex.test/0.9/stable/versioning.json")
        assertEquals(listOf("Toonily"), list.map { it.id })
        assertEquals("https://inkdex.test/0.9/stable/Toonily/index.js", list[0].code)
        assertEquals("https://inkdex.test/0.9/stable/Toonily/static/icon.png", list[0].icon)
        assertTrue(list[0].adult)
    }

    /** Runs real extensions of Inkdex from the web. It runs only with MOEKOMA_LIVE=1. */
    @Test fun readsRealExtensions() = runBlocking {
        assumeTrue(System.getenv("MOEKOMA_LIVE") == "1")
        val agent = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"
        val web: Fetcher = { request ->
            val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = request.method
            connection.setRequestProperty("User-Agent", agent)
            request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            request.body?.let { body -> connection.doOutput = true; connection.outputStream.use { it.write(body) } }
            val status = connection.responseCode
            val body = (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
            HttpResponse(status, connection.url.toString(), connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(", ") }, body)
        }
        val repo = "https://inkdex.github.io/extensions/0.9/stable/versioning.json"
        val infos = PaperbackIndex.parse(web(HttpRequest(repo)).body, repo)
        for (id in listOf("WeebCentral", "MangaDex")) {
            val info = infos.first { it.id == id }
            val source = PaperbackExtension.load(info, String(web(HttpRequest(info.code)).body), null, agent, web)
            val popular = source.popular(1)
            assertTrue("$id has titles", popular.items.isNotEmpty())
            val details = source.details(popular.items[0])
            assertTrue("$id has chapters", details.chapters.isNotEmpty())
            val (_, pages) = source.content(details.chapters.last(), details.series)
            assertNotNull(pages)
            var forms = 0
            if (source.hasSettingsForm()) {
                val root = source.settingsForm()!!
                for (link in root.sections.flatMap { it.rows }.mapNotNull { it.form }) forms += source.form(link).sections.flatMap { it.rows }.size
            }
            println("LIVE $id: ${popular.items.size} series; ${details.series.title}, ${details.chapters.size} chapters, ${pages.size} pages; $forms rows in the child forms")
            source.close()
        }
    }
}
