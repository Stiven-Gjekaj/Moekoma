package io.github.stivengjekaj.moekoma.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

/**
 * The settings form of an Aidoku source. The settings have the shapes of real sources: a sign-in with a link that
 * needs it, a page with a button, and an editable list. As in Asabunko.
 */
class AidokuSettingsTest {
    private val json = """
        [
          {"type": "group", "title": "Account", "footer": "Sign in to read more.", "items": [
            {"type": "login", "key": "login", "title": "Sign in", "logoutTitle": "Sign out", "method": "basic", "useEmail": true},
            {"type": "link", "title": "Content warnings", "url": "https://site.test/settings", "external": true, "requires": "login"}
          ]},
          {"type": "group", "title": "Reading", "items": [
            {"type": "switch", "key": "hd", "title": "HD pages", "default": true},
            {"type": "select", "key": "server", "title": "Server", "values": ["a", "b"], "titles": ["Alpha", "Beta"], "default": "a"},
            {"type": "segment", "key": "quality", "title": "Quality", "options": ["Low", "High"], "default": 1},
            {"type": "stepper", "key": "count", "title": "Count", "minimumValue": 1, "maximumValue": 5, "default": 2},
            {"type": "text", "key": "token", "title": "Token", "secure": true, "requiresFalse": "hd"},
            {"type": "group", "items": [{"type": "multi-select", "key": "types", "title": "Types", "values": ["m", "w"], "titles": ["Manga", "Webtoon"]}]}
          ]},
          {"type": "page", "key": "filters", "title": "Content filters", "items": [
            {"type": "group", "items": [{"type": "editable-list", "key": "blocklist", "title": "Blocked tags", "placeholder": "Tag", "default": []}]},
            {"type": "button", "key": "reset", "title": "Reset", "confirmText": "Remove all filters?", "destructive": true}
          ]}
        ]
    """.trimIndent().toByteArray()

    private fun settings(defaults: AidokuDefaults, languages: List<String> = listOf("en")) = AidokuSettings(json, null, defaults, languages, false)
    private fun empty() = AidokuDefaults(null, emptyMap())
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun AidokuDefaults.text(key: String) = this[key]?.let { runCatching { PostcardReader(it).string() }.getOrNull() }

    @Test fun showsTheGroupsAsSectionsAndHidesTheRowsThatNeedASetting() {
        val form = settings(empty()).form("root")
        assertEquals(listOf("Account", "Reading", null), form.sections.map { it.header })
        assertEquals("Sign in to read more.", form.sections[0].footer)
        // The link needs the sign-in, and the token needs HD off. The group in the group gives its row to the outer group.
        assertEquals(listOf(SourceForm.Row.Kind.Login), form.sections[0].rows.map { it.kind })
        assertEquals(listOf("hd", "server", "quality", "count", "types"), form.sections[1].rows.map { it.id })
        assertEquals(
            listOf(SourceForm.Row.Kind.Toggle, SourceForm.Row.Kind.Link, SourceForm.Row.Kind.Link, SourceForm.Row.Kind.Stepper, SourceForm.Row.Kind.Link),
            form.sections[1].rows.map { it.kind },
        )
        // A setting with no value has its default.
        assertEquals(true, form.sections[1].rows[0].on)
        assertEquals("Alpha", form.sections[1].rows[1].value)
        assertEquals("High", form.sections[1].rows[2].value)
        assertEquals(2.0, form.sections[1].rows[3].number)
        assertEquals("None", form.sections[1].rows[4].value)
        assertEquals(listOf("page:filters"), form.sections[2].rows.map { it.form })
        val login = form.sections[0].rows[0].login!!
        assertTrue(login.method == "basic" && login.useEmail == true && !login.signedIn)
    }

    @Test fun keepsEachValueInTheFormThatTheSourceReads() {
        val defaults = empty()
        val settings = settings(defaults)
        settings.act("root", "hd", FormInput.Toggle(false), null, null)
        assertArrayEquals(bytes(0x00), defaults["hd"])
        var form = settings.form("root")
        // The token shows when HD is off.
        assertEquals(true, form.sections[1].rows.first { it.id == "token" }.secure)

        settings.act("select:server", "b", FormInput.Tap, null, null)
        assertArrayEquals(bytes(0x01, 0x62), defaults["server"])
        settings.act("select:quality", "0", FormInput.Tap, null, null)
        // A segment keeps the index of its choice.
        assertArrayEquals(bytes(0x00), defaults["quality"])
        settings.act("multi:types", "w", FormInput.Tap, null, null)
        settings.act("multi:types", "m", FormInput.Tap, null, null)
        // The choices keep the order of the source.
        assertArrayEquals(bytes(0x02, 0x01, 0x6D, 0x01, 0x77), defaults["types"])
        assertEquals(listOf("included", "included"), settings.form("multi:types").sections[0].rows.map { it.mark })
        settings.act("root", "count", FormInput.Number(9.0), null, null)
        // A stepper stays in its range.
        assertArrayEquals(PostcardWriter.write { f32(5f) }, defaults["count"])
        form = settings.form("root")
        assertTrue("Beta" in form.sections[1].rows.mapNotNull { it.value })
        assertEquals("Manga, Webtoon", form.sections[1].rows.first { it.id == "types" }.value)
    }

    @Test fun addsAndRemovesTheEntriesOfAList() {
        val settings = settings(empty())
        val page = settings.form("page:filters")
        assertEquals(listOf(SourceForm.Row.Kind.Link, SourceForm.Row.Kind.Button), page.sections.flatMap { it.rows }.map { it.kind })
        assertEquals("Remove all filters?", page.sections.flatMap { it.rows }.last().confirm)
        settings.act("list:blocklist", "add", FormInput.Text(" gore "), null, null)
        settings.act("list:blocklist", "add", FormInput.Text("drama"), null, null)
        settings.act("list:blocklist", "entry:0", FormInput.Tap, null, null)
        val list = settings.form("list:blocklist")
        assertEquals(listOf("drama"), list.sections[0].rows.map { it.title })
        assertEquals(listOf(SourceForm.Row.Kind.Input), list.sections[1].rows.map { it.kind })
        assertEquals("1", settings.form("page:filters").sections[0].rows[0].value)
    }

    @Test fun signsInAndOutWithTheKeysOfTheAidokuApp() {
        val defaults = empty()
        val settings = settings(defaults)
        settings.act("root", "login", FormInput.Login("mina@site.test", "tea"), null, null)
        assertEquals("logged_in", defaults.text("login"))
        assertEquals("mina@site.test", defaults.text("login.username"))
        assertEquals("tea", defaults.text("login.password"))
        var form = settings.form("root")
        // The link shows after the sign-in.
        assertEquals(listOf("Sign out", "Content warnings"), form.sections[0].rows.map { it.title })
        assertEquals(true, form.sections[0].rows[0].login?.signedIn)
        assertEquals("mina@site.test", form.sections[0].rows[0].subtitle)
        assertEquals("https://site.test/settings", form.sections[0].rows[1].url)
        settings.act("root", "login", FormInput.Logout, null, null)
        assertNull(defaults["login"])
        assertNull(defaults["login.password"])
        form = settings.form("root")
        assertEquals(listOf("Sign in"), form.sections[0].rows.map { it.title })
    }

    @Test fun makesTheOAuthPageWithPkceAndTheTokenRequest() {
        val oauth = """
            [{"type": "login", "key": "login", "method": "oauth", "title": "Log in",
              "url": "https://auth.test/auth?client_id=neko&redirect_uri=neko://auth", "tokenUrl": "https://auth.test/token",
              "pkce": true, "callbackScheme": "neko"}]
        """.trimIndent().toByteArray()
        val defaults = empty()
        val settings = AidokuSettings(oauth, null, defaults, emptyList(), false)
        val login = settings.form("root").sections[0].rows[0].login!!
        assertEquals("neko", login.callbackScheme)
        val verifier = defaults.text("login.codeVerifier")!!
        assertTrue(login.url!!.contains("code_challenge=${settings.challenge(verifier)}&"))
        assertTrue(login.url!!.contains("client_id=neko"))
        // The verifier stays the same while the user has not signed in.
        assertEquals(login.url, settings.form("root").sections[0].rows[0].login?.url)

        val request = settings.tokenRequest("login", "neko://auth?code=abc")!!
        assertEquals("https://auth.test/token", request.url)
        assertEquals("POST", request.method)
        assertEquals("client_id=neko&code=abc&code_verifier=$verifier&grant_type=authorization_code&redirect_uri=neko%3A%2F%2Fauth", String(request.body!!))
        settings.act("root", "login", FormInput.Callback("neko://auth?code=abc"), """{"access_token":"t"}""", null)
        assertEquals("""{"access_token":"t"}""", defaults.text("login"))
    }

    @Test fun givesAChoiceOfTheLanguagesOfTheSource() {
        val defaults = empty()
        val settings = settings(defaults, listOf("en", "ja"))
        assertEquals("Languages", settings.form("root").sections[0].header)
        settings.act("select:languages", "ja", FormInput.Tap, null, null)
        // A single language is a list of one.
        assertArrayEquals(bytes(0x01, 0x02, 0x6A, 0x61), defaults["languages"])
    }

    @Test fun readsTheSettingsOfGetSettings() {
        // A group with a footer and one switch, in the postcard form of aidoku-rs.
        val data = PostcardWriter.write {
            unsigned(1)
            string("group"); string(""); string("Dynamic"); repeat(4) { u8(0) }
            unsigned(0); option("Made in code.") { string(it) }
            unsigned(1)
            string("switch"); string("fast"); string("Fast mode"); repeat(4) { u8(0) }
            unsigned(3); option<String>(null) { string(it) }; option<Boolean>(null) { bool(it) }; bool(true)
        }
        val defaults = empty()
        val form = AidokuSettings(null, data, defaults, emptyList(), false).form("root")
        assertEquals(listOf("Dynamic"), form.sections.map { it.header })
        assertEquals("Made in code.", form.sections[0].footer)
        assertEquals(listOf(true), form.sections[0].rows.map { it.on })
        // A setting of get_settings gets its default.
        assertArrayEquals(bytes(0x01), defaults["fast"])
    }

    /** Opens each form of each English source of Aidoku Community. It runs only with MOEKOMA_LIVE=1. */
    @Test fun opensTheSettingsOfRealPackages() = runBlocking {
        assumeTrue(System.getenv("MOEKOMA_LIVE") == "1")
        fun get(url: String) = (URI(url).toURL().openConnection() as HttpURLConnection).inputStream.use { it.readBytes() }
        val listUrl = "https://aidoku-community.github.io/sources/index.min.json"
        val infos = AidokuIndex.parse(get(listUrl), listUrl).filter { it.language == "en" }
        val offline: Fetcher = { throw java.io.IOException("No network in this test.") }
        val opened = mutableListOf<String>()
        var forms = 0
        val kinds = sortedMapOf<String, Int>()
        for (info in infos) {
            val file = kotlin.io.path.createTempFile(info.id, ".aix").toFile().apply { writeBytes(get(info.code)); deleteOnExit() }
            val source = runCatching { AidokuSource.load(info, file, null, offline, null) }.getOrNull() ?: continue
            if (source.hasSettingsForm) {
                opened += info.id
                val queue = mutableListOf(source.form("root"))
                while (queue.isNotEmpty()) {
                    val form = queue.removeAt(queue.size - 1)
                    forms++
                    for (row in form.sections.flatMap { it.rows }) {
                        kinds.merge(row.kind.name, 1, Int::plus)
                        row.form?.let { queue += source.form(it) }
                    }
                }
            }
            source.close()
        }
        println("LIVE settings: ${infos.size} packages, ${opened.size} with settings $opened, $forms forms, $kinds")
        assertFalse("Each row of a real form is a row that the app can do.", "Unsupported" in kinds)
    }
}
