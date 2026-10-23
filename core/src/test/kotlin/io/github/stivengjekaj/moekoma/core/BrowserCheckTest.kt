package io.github.stivengjekaj.moekoma.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCheckTest {
    @Test fun findsTheChallengeOfCloudflare() {
        assertTrue(BrowserCheck.isChallenge(403, mapOf("cf-mitigated" to "challenge"), ByteArray(0)))
        val page = "<html><title>Just a moment...</title><script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate\"></script>".toByteArray()
        assertTrue(BrowserCheck.isChallenge(503, mapOf("Server" to "cloudflare"), page))
    }

    @Test fun aRefusalWithNoChallengeIsNotACheck() {
        assertFalse(BrowserCheck.isChallenge(403, mapOf("Server" to "cloudflare"), "Forbidden".toByteArray()))
        assertFalse(BrowserCheck.isChallenge(503, mapOf("Server" to "nginx"), "Just a moment...".toByteArray()))
        assertFalse(BrowserCheck.isChallenge(200, mapOf("Server" to "cloudflare"), "cf-chl".toByteArray()))
    }

    @Test fun theClearanceCookieOfTheSiteMarksAPassedCheck() {
        assertTrue(BrowserCheck.passed(listOf("cf_clearance" to ".mangasite.test"), "www.mangasite.test"))
        assertTrue(BrowserCheck.passed(listOf("cf_clearance" to "mangasite.test"), "mangasite.test"))
        assertFalse(BrowserCheck.passed(listOf("cf_clearance" to ".other.test"), "mangasite.test"))
        assertFalse(BrowserCheck.passed(listOf("session" to ".mangasite.test"), "mangasite.test"))
        assertFalse("A host that only ends with the same letters is another site.", BrowserCheck.passed(listOf("cf_clearance" to "site.test"), "mangasite.test"))
    }
}
