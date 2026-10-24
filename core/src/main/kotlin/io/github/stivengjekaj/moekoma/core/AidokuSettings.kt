package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/** One setting of an Aidoku source, with the keys of settings.json. Strings, booleans, numbers, lists, and maps. */
private typealias Item = Map<String, Any?>

/**
 * The settings of an Aidoku source as a form: groups, pages, choices, lists, buttons, links, steppers, and sign-ins.
 * The settings come from settings.json and from `get_settings`. The values stay in the defaults of the source, with the
 * keys that the sources of aidoku-rs read. As in Asabunko.
 */
class AidokuSettings(json: ByteArray?, dynamic: ByteArray?, private val defaults: AidokuDefaults, languages: List<String>, multiLanguage: Boolean) {
    private val items: List<Item>
    /** Each setting by the ID of its row. */
    private val byId = HashMap<String, Item>()

    val isEmpty: Boolean get() = items.isEmpty()

    init {
        val list = json?.let { runCatching { plain(Json.parseToJsonElement(String(it, Charsets.UTF_8))) as? List<*> }.getOrNull() }
            .orEmpty().filterIsInstance<Map<String, Any?>>().toMutableList()
        if (dynamic != null) runCatching { PostcardReader(dynamic).list { readSetting(this) } }.getOrNull()?.let(list::addAll)
        // A source of more than one language gets a choice of them.
        if (languages.size > 1) {
            val names = languages.map { Locale.forLanguageTag(it).getDisplayName(Locale.ENGLISH).ifEmpty { it } }
            val choice = mapOf("type" to if (multiLanguage) "multi-select" else "select", "key" to "languages", "title" to "Languages", "values" to languages, "titles" to names)
            list.add(0, mapOf("type" to "group", "title" to "Languages", "items" to listOf(choice)))
        }
        items = list
        fun visit(item: Item) {
            byId[id(item)] = item
            children(item).forEach(::visit)
        }
        items.forEach(::visit)
        // A setting of get_settings has its default only here.
        for (item in byId.values) if (defaults[key(item)] == null) encodeDefault(item)?.let { defaults[key(item)] = it }
    }

    private companion object {
        /** A JSON value as plain Kotlin values. */
        fun plain(element: JsonElement): Any? = when (element) {
            is JsonNull -> null
            is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.doubleOrNull
            is JsonArray -> element.map(::plain)
            is JsonObject -> element.mapValues { plain(it.value) }
        }

        fun Item.text(name: String) = this[name] as? String
        fun Item.flag(name: String) = this[name] as? Boolean
        fun Item.number(name: String) = (this[name] as? Number)?.toDouble()
        fun Item.strings(name: String) = (this[name] as? List<*>)?.filterIsInstance<String>()
        fun children(item: Item) = (item["items"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()

        fun key(item: Item) = item.text("key").orEmpty()

        /** The ID of the row of a setting: its key, or its type and its title for a setting with no key. */
        fun id(item: Item): String = key(item).ifEmpty { "${item.text("type").orEmpty()}:${item.text("title").orEmpty()}" }

        fun encodeDefault(item: Item): ByteArray? {
            val value = item["default"] ?: return null
            val type = item.text("type")
            return when {
                type == "switch" && value is Boolean -> PostcardWriter.write { bool(value) }
                type == "segment" && value is Number -> PostcardWriter.write { signed(value.toLong()) }
                type == "stepper" && value is Number -> PostcardWriter.write { f32(value.toFloat()) }
                value is String -> PostcardWriter.write { string(value) }
                value is List<*> -> PostcardWriter.write { list(value.filterIsInstance<String>()) { string(it) } }
                else -> null
            }
        }

        fun codeVerifier(): String {
            val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
            val random = SecureRandom()
            return String(CharArray(64) { characters[random.nextInt(characters.length)] })
        }

        /** One `Setting` of aidoku-rs in postcard, as a map with the keys of settings.json. */
        fun readSetting(r: PostcardReader): Item {
            val item = linkedMapOf<String, Any?>()
            fun strings() = r.list { string() }
            item["type"] = r.string()
            item["key"] = r.string()
            item["title"] = r.string()
            item["notification"] = r.option { string() }
            item["requires"] = r.option { string() }
            item["requiresFalse"] = r.option { string() }
            item["refreshes"] = r.option { list { string() } }
            when (val tag = r.unsigned()) {
                0 -> {
                    item["footer"] = r.option { string() }
                    item["items"] = r.list { readSetting(this) }
                }
                1, 12 -> {
                    item["values"] = strings()
                    item["titles"] = r.option { list { string() } }
                    if (item["type"] != "picker") r.option { bool() }
                    item["default"] = r.option { string() }
                }
                2 -> {
                    item["values"] = strings()
                    item["titles"] = r.option { list { string() } }
                    r.option { bool() }
                    item["default"] = r.option { list { string() } }
                }
                3 -> {
                    item["subtitle"] = r.option { string() }
                    r.option { bool() }
                    item["default"] = r.bool()
                }
                4 -> {
                    item["minimumValue"] = r.f64()
                    item["maximumValue"] = r.f64()
                    item["stepValue"] = r.option { f64() }
                    item["default"] = r.option { f64() }
                }
                5 -> {
                    item["options"] = strings()
                    item["default"] = r.option { signed().toInt() }
                }
                6 -> {
                    item["placeholder"] = r.option { string() }
                    r.option { signed() }
                    item["autocorrectionDisabled"] = r.option { bool() }
                    r.option { signed() }
                    r.option { signed() }
                    item["secure"] = r.option { bool() }
                    item["default"] = r.option { string() }
                }
                7 -> {}
                8 -> {
                    item["url"] = r.string()
                    item["external"] = r.option { bool() }
                }
                9 -> {
                    item["method"] = r.string()
                    item["url"] = r.option { string() }
                    item["urlKey"] = r.option { string() }
                    item["logoutTitle"] = r.option { string() }
                    item["pkce"] = r.bool()
                    item["tokenUrl"] = r.option { string() }
                    item["callbackScheme"] = r.option { string() }
                    item["useEmail"] = r.bool()
                    item["localStorageKeys"] = r.option { list { string() } }
                    item["clearCookiesOnLogOut"] = r.bool()
                }
                10 -> {
                    item["items"] = r.list { readSetting(this) }
                    r.option { bool() }
                    r.option { bool() }
                    r.option {
                        if (string() == "system") {
                            string(); string(); option { signed() }
                        } else {
                            string()
                        }
                    }
                    item["info"] = r.option { string() }
                }
                11 -> {
                    item["lineLimit"] = r.option { signed().toInt() }
                    item["inline"] = r.bool()
                    item["placeholder"] = r.option { string() }
                    item["default"] = r.option { list { string() } }
                }
                else -> throw PostcardException("The setting kind $tag is not known.")
            }
            return item.filterValues { it != null }
        }
    }

    // The values

    private fun <T> read(key: String, read: PostcardReader.() -> T): T? = defaults[key]?.let { runCatching { PostcardReader(it).read() }.getOrNull() }

    private fun write(key: String, write: PostcardWriter.() -> Unit) {
        defaults[key] = PostcardWriter.write(write)
    }

    private fun string(key: String): String? = read(key) { string() }
    private fun strings(key: String): List<String>? = read(key) { list { string() } }

    /** True when the setting of the key is on: a switch that is on, a sign-in that is done, or a text that is not empty. */
    private fun isOn(key: String): Boolean = when (byId[key]?.text("type")) {
        "switch" -> read(key) { bool() } ?: false
        "login" -> !string(key).isNullOrEmpty()
        else -> read(key) { bool() } ?: !string(key).isNullOrEmpty()
    }

    private fun isShown(item: Item): Boolean {
        item.text("requires")?.let { if (!isOn(it)) return false }
        item.text("requiresFalse")?.let { if (isOn(it)) return false }
        return true
    }

    /** The values of a choice and their titles. A segment keeps the index of its choice. */
    private fun choices(item: Item): List<Pair<String, String>> {
        if (item.text("type") == "segment") return item.strings("options").orEmpty().mapIndexed { index, title -> index.toString() to title }
        val titles = item.strings("titles")
        return item.strings("values").orEmpty().mapIndexed { index, value -> value to (titles?.getOrNull(index) ?: value) }
    }

    private fun chosen(item: Item): String? {
        val key = key(item)
        return when {
            item.text("type") == "segment" -> read(key) { signed() }?.toString() ?: ((item.number("default") ?: 0.0).toInt()).toString()
            key == "languages" -> strings(key)?.firstOrNull()
            else -> string(key) ?: item.text("default") ?: choices(item).firstOrNull()?.first
        }
    }

    private fun list(item: Item): List<String> = strings(key(item)) ?: item.strings("default").orEmpty()

    // The form

    fun form(id: String): SourceForm {
        val kind = id.substringBefore(':')
        val key = id.substringAfter(':', "")
        return when (kind) {
            "root" -> SourceForm(id, sections = sections(items))
            "page" -> SourceForm(id, sections = sections(children(byId[key] ?: throw SourceFormException.unknownRow())))
            "select", "multi" -> {
                val item = byId[key] ?: throw SourceFormException.unknownRow()
                val picked = if (kind == "multi") list(item).toSet() else setOfNotNull(chosen(item))
                val rows = choices(item).map { (value, title) ->
                    SourceForm.Row(value, "labelRow", title, mark = if (value in picked) "included" else null, selectable = true)
                }
                SourceForm(id, sections = listOf(SourceForm.Section("choices", rows = rows)))
            }
            "list" -> {
                val item = byId[key] ?: throw SourceFormException.unknownRow()
                val entries = list(item).mapIndexed { index, entry -> SourceForm.Row("entry:$index", "buttonRow", entry) }
                val add = SourceForm.Row("add", "inputRow", item.text("placeholder") ?: "Add")
                SourceForm(id, sections = listOf(
                    SourceForm.Section("entries", footer = if (entries.isEmpty()) null else "Tap an entry to remove it.", rows = entries),
                    SourceForm.Section("add", rows = listOf(add)),
                ))
            }
            else -> throw SourceFormException.unknownRow()
        }
    }

    /** The settings of a list in sections. A group is a section. The settings out of a group share a section. */
    private fun sections(list: List<Item>): List<SourceForm.Section> {
        val result = mutableListOf<SourceForm.Section>()
        val loose = mutableListOf<SourceForm.Row>()
        fun flush() {
            if (loose.isNotEmpty()) result += SourceForm.Section("loose-${result.size}", rows = loose.toList())
            loose.clear()
        }
        for (item in list) {
            if (item.text("type") == "group") {
                if (!isShown(item)) continue
                flush()
                val rows = rows(children(item))
                val footer = item.text("footer")
                if (rows.isNotEmpty() || footer != null) result += SourceForm.Section("group-${result.size}", item.text("title")?.ifEmpty { null }, footer, rows)
            } else {
                row(item)?.let(loose::add)
            }
        }
        flush()
        return result
    }

    /** The rows of a group. A group in a group gives its rows to the outer group. */
    private fun rows(list: List<Item>): List<SourceForm.Row> = list.flatMap { item ->
        if (item.text("type") == "group") (if (isShown(item)) rows(children(item)) else emptyList()) else listOfNotNull(row(item))
    }

    private fun row(item: Item): SourceForm.Row? {
        if (!isShown(item)) return null
        val key = key(item)
        val id = id(item)
        val title = item.text("title") ?: key
        return when (item.text("type")) {
            "switch" -> SourceForm.Row(id, "toggleRow", title, subtitle = item.text("subtitle"), on = read(key) { bool() } ?: item.flag("default") ?: false)
            "select", "picker", "segment" -> {
                val current = chosen(item)
                SourceForm.Row(id, "navigationRow", title, value = choices(item).firstOrNull { it.first == current }?.second ?: current, form = "select:$id")
            }
            "multi-select" -> {
                val picked = list(item).toSet()
                val names = choices(item).filter { it.first in picked }.map { it.second }
                SourceForm.Row(id, "navigationRow", title, value = if (names.isEmpty()) "None" else names.joinToString(", "), form = "multi:$id")
            }
            "text" -> SourceForm.Row(id, "inputRow", title, value = string(key) ?: item.text("default") ?: "", secure = item.flag("secure"))
            "stepper" -> {
                val minimum = item.number("minimumValue") ?: 0.0
                val value = read(key) { f32() }?.toDouble() ?: item.number("default") ?: minimum
                SourceForm.Row(id, "stepperRow", title, number = value, minimum = minimum, maximum = item.number("maximumValue"), step = item.number("stepValue"))
            }
            "button" -> SourceForm.Row(id, "buttonRow", title, confirm = item.text("confirmText") ?: item.text("confirmMessage"), destructive = item.flag("destructive"))
            "link" -> SourceForm.Row(id, "linkRow", title, url = item.text("url"))
            "page" -> SourceForm.Row(id, "navigationRow", title, form = "page:$id")
            "editable-list" -> {
                val count = list(item).size
                SourceForm.Row(id, "navigationRow", title, value = if (count == 0) "None" else count.toString(), form = "list:$id")
            }
            "login" -> loginRow(item, id, title)
            else -> null
        }
    }

    private fun loginRow(item: Item, id: String, title: String): SourceForm.Row {
        val key = key(item)
        val signedIn = !string(key).isNullOrEmpty()
        val method = item.text("method") ?: "basic"
        var url = item.text("url") ?: item.text("urlKey")?.let(::string)
        if (method == "oauth" && !signedIn && item.flag("pkce") == true && url != null) {
            // The code verifier stays until the sign-in ends, so the challenge of the page matches it.
            val verifier = string("$key.codeVerifier") ?: codeVerifier()
            write("$key.codeVerifier") { string(verifier) }
            val extra = "code_challenge=${challenge(verifier)}&code_challenge_method=S256&response_type=code"
            url = if ('?' in url) "$url&$extra" else "$url?$extra"
        }
        val login = SourceForm.Login(
            method = method, url = url, callbackScheme = item.text("callbackScheme") ?: "aidoku", useEmail = item.flag("useEmail"),
            signedIn = signedIn, username = string("$key.username"), clearsCookies = item.flag("clearCookiesOnLogOut"),
            storageKeys = item.strings("localStorageKeys"),
        )
        return SourceForm.Row(
            id, "loginRow", if (signedIn) item.text("logoutTitle") ?: "Sign out" else title,
            subtitle = if (signedIn) string("$key.username") else null, login = login,
        )
    }

    fun challenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    /** The request that changes the code of an OAuth sign-in with PKCE into a token. Null for other sign-ins. */
    fun tokenRequest(row: String, callback: String): HttpRequest? {
        val item = byId[row] ?: return null
        if (item.flag("pkce") != true) return null
        val token = item.text("tokenUrl") ?: return null
        val code = query(callback)["code"] ?: return null
        val verifier = string("${key(item)}.codeVerifier") ?: return null
        val parameters = sortedMapOf("grant_type" to "authorization_code", "code" to code, "code_verifier" to verifier)
        val asked = item.text("url")?.let(::query).orEmpty()
        for (name in listOf("redirect_uri", "client_id")) asked[name]?.let { parameters[name] = it }
        // The characters that RFC 3986 does not reserve, such as "~" of a code verifier, stay as they are.
        val body = parameters.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8").replace("+", "%20").replace("%7E", "~")}" }
        return HttpRequest(token, "POST", mapOf("Content-Type" to "application/x-www-form-urlencoded"), body.toByteArray())
    }

    private fun query(url: String): Map<String, String> = runCatching { URI(url).rawQuery }.getOrNull().orEmpty().split('&')
        .filter { '=' in it }.associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    // The actions

    /**
     * Does the action of a row. It runs on the thread of the source, because a sign-in and a notification call the
     * source. [token] is the answer of the token request of an OAuth sign-in. With no host, the source gets no calls.
     */
    fun act(form: String, row: String, input: FormInput, token: String?, host: AidokuHost?) {
        val kind = form.substringBefore(':')
        if (kind == "select" || kind == "multi" || kind == "list") {
            val item = byId[form.substringAfter(':', "")] ?: throw SourceFormException.unknownRow()
            val key = key(item)
            when {
                kind == "select" && input is FormInput.Tap -> {
                    if (choices(item).none { it.first == row }) throw SourceFormException.unknownRow()
                    when {
                        item.text("type") == "segment" -> write(key) { signed(row.toLongOrNull() ?: 0) }
                        key == "languages" -> write(key) { list(listOf(row)) { string(it) } }
                        else -> write(key) { string(row) }
                    }
                }
                kind == "multi" && input is FormInput.Tap -> {
                    val picked = list(item).toMutableList()
                    if (!picked.remove(row)) picked += row
                    // The choices keep the order of the list of the source.
                    val order = choices(item).map { it.first }
                    val sorted = picked.sortedBy { order.indexOf(it).coerceAtLeast(0) }
                    write(key) { list(sorted) { string(it) } }
                }
                kind == "list" && row == "add" && input is FormInput.Text -> {
                    val entry = input.text.trim()
                    if (entry.isEmpty()) return
                    write(key) { list(list(item) + entry) { string(it) } }
                }
                kind == "list" && input is FormInput.Tap -> {
                    val index = row.removePrefix("entry:").toIntOrNull() ?: throw SourceFormException.unknownRow()
                    val entries = list(item).toMutableList()
                    if (index !in entries.indices) throw SourceFormException.unknownRow()
                    entries.removeAt(index)
                    write(key) { list(entries) { string(it) } }
                }
                else -> throw SourceFormException.unknownRow()
            }
            notify(item, host)
            return
        }

        val item = byId[row] ?: throw SourceFormException.unknownRow()
        val key = key(item)
        fun text(value: String) = PostcardWriter.write { string(value) }
        val type = item.text("type")
        when {
            type == "switch" && input is FormInput.Toggle -> write(key) { bool(input.on) }
            type == "text" && input is FormInput.Text -> write(key) { string(input.text) }
            type == "stepper" && input is FormInput.Number -> {
                var number = input.value
                item.number("minimumValue")?.let { number = maxOf(number, it) }
                item.number("maximumValue")?.let { number = minOf(number, it) }
                write(key) { f32(number.toFloat()) }
            }
            type == "button" && input is FormInput.Tap -> {}
            type == "login" && input is FormInput.Login -> {
                write("$key.username") { string(input.username) }
                write("$key.password") { string(input.password) }
                write(key) { string("logged_in") }
                if (host != null && "handle_basic_login" in host.exports) {
                    val accepted = runCatching { PostcardReader(host.call("handle_basic_login", text(key), text(input.username), text(input.password))).bool() }.getOrDefault(false)
                    if (!accepted) {
                        signOut(item)
                        throw SourceFormException.refused()
                    }
                }
            }
            type == "login" && input is FormInput.Web -> {
                val names = input.cookies.keys.sorted()
                write("$key.keys") { list(names) { string(it) } }
                write("$key.values") { list(names.map { input.cookies[it].orEmpty() }) { string(it) } }
                for ((name, value) in input.storage) write("$key.ls.$name") { string(value) }
                write(key) { string("logged_in") }
                if (host != null && "handle_web_login" in host.exports) {
                    val keys = PostcardWriter.write { list(names) { string(it) } }
                    val values = PostcardWriter.write { list(names.map { input.cookies[it].orEmpty() }) { string(it) } }
                    val accepted = runCatching { PostcardReader(host.call("handle_web_login", text(key), keys, values)).bool() }.getOrDefault(false)
                    if (!accepted) {
                        signOut(item)
                        throw SourceFormException.refused()
                    }
                }
            }
            type == "login" && input is FormInput.Callback -> write(key) { string(token ?: input.url) }
            type == "login" && input is FormInput.Logout -> signOut(item)
            else -> throw SourceFormException.unknownRow()
        }
        notify(item, host)
    }

    private fun signOut(item: Item) {
        val key = key(item)
        for (suffix in listOf("", ".username", ".password", ".keys", ".values", ".codeVerifier")) defaults[key + suffix] = null
        for (name in item.strings("localStorageKeys").orEmpty()) defaults["$key.ls.$name"] = null
    }

    /** Tells the source that a setting changed, when the setting asks for it. */
    private fun notify(item: Item, host: AidokuHost?) {
        val notification = item.text("notification") ?: return
        if (host == null || "handle_notification" !in host.exports) return
        host.callWithNoResult("handle_notification", PostcardWriter.write { string(notification) })
    }
}
