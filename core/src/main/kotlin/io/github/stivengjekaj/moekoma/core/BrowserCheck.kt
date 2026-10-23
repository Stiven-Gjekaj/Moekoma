package io.github.stivengjekaj.moekoma.core

/**
 * Finds the answer of a site that asks for a check in a browser, such as the challenge of Cloudflare.
 * The app passes the check in a web view, and the cookie of the check goes to the network client. As in Asabunko.
 */
object BrowserCheck {
    private val marks = listOf("challenge-platform", "cf-chl", "Just a moment...", "cf_chl_opt", "Attention Required!")

    fun isChallenge(status: Int, headers: Map<String, String>, body: ByteArray): Boolean {
        val lower = headers.entries.associate { it.key.lowercase() to it.value.lowercase() }
        if (lower["cf-mitigated"] == "challenge") return true
        if (status != 403 && status != 503 && status != 429) return false
        if (lower["server"]?.contains("cloudflare") != true) return false
        val text = String(body, 0, minOf(body.size, 16_384), Charsets.UTF_8)
        return marks.any { it in text }
    }

    /** True when the cookies, as name and domain, hold the mark of a passed check for the host. */
    fun passed(cookies: List<Pair<String, String>>, host: String): Boolean = cookies.any { (name, domain) ->
        val bare = domain.trimStart('.')
        name == "cf_clearance" && (host == bare || host.endsWith(".$bare"))
    }
}
