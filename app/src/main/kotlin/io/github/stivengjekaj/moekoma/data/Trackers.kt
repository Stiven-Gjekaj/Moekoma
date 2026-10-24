package io.github.stivengjekaj.moekoma.data

import android.content.Context
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.Fetcher
import io.github.stivengjekaj.moekoma.core.TrackLink
import io.github.stivengjekaj.moekoma.core.Tracker
import io.github.stivengjekaj.moekoma.core.TrackerException
import io.github.stivengjekaj.moekoma.core.TrackerHit
import io.github.stivengjekaj.moekoma.core.TrackerKind
import io.github.stivengjekaj.moekoma.core.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID

/**
 * The accounts of AniList and MyAnimeList, and the progress that the books send to them, as in Asabunko. The client ID
 * of each site is a setting. The token is a secret of the app.
 */
class Trackers(context: Context, private val model: AppModel, private val secrets: Secrets, private val fetcher: Fetcher, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("trackers", Context.MODE_PRIVATE)
    /** A test sends the calls of the trackers to the test server. */
    var base: String? = null
    private val signedIn = MutableStateFlow(TrackerKind.entries.filter { secrets.secret(tokenKey(it)) != null }.toSet())
    /** The trackers with a token. */
    val accounts: StateFlow<Set<TrackerKind>> = signedIn.asStateFlow()

    private fun tokenKey(kind: TrackerKind) = "tracker.${kind.name.lowercase()}"

    fun clientId(kind: TrackerKind): String = prefs.getString("${kind.name}.client", "").orEmpty().trim()

    fun setClientId(kind: TrackerKind, id: String) = prefs.edit().putString("${kind.name}.client", id.trim()).apply()

    fun tracker(kind: TrackerKind) = Tracker(kind, clientId(kind), secrets.secret(tokenKey(kind)), fetcher, base)

    /** The page of the sign-in. The verifier of PKCE waits for the callback. */
    fun signInUrl(kind: TrackerKind): String {
        val verifier = verifier()
        val state = UUID.randomUUID().toString()
        prefs.edit().putString("${kind.name}.verifier", verifier).apply()
        return tracker(kind).signInUrl(verifier, state)
    }

    /** Keeps the token of the callback of a sign-in. */
    suspend fun finishSignIn(kind: TrackerKind, callback: String) {
        val token = tracker(kind).token(callback, prefs.getString("${kind.name}.verifier", "").orEmpty())
        secrets.setSecret(tokenKey(kind), token)
        prefs.edit().remove("${kind.name}.verifier").apply()
        signedIn.value = signedIn.value + kind
    }

    fun signOut(kind: TrackerKind) {
        secrets.setSecret(tokenKey(kind), null)
        signedIn.value = signedIn.value - kind
    }

    /** Sends the progress of a book to each of its trackers. A tracker gets only a number that goes up. A failed call tries again at the next change of the book. */
    fun sendProgress(id: UUID) {
        val book = model.library.value.book(id) ?: return
        for (link in book.tracking) {
            if (link.kind !in signedIn.value) continue
            val chapters = Tracker.progress(book.page, book.pageCount, book.finished, link.sent) ?: continue
            scope.launch {
                try {
                    tracker(link.kind).setProgress(link, chapters, book.finished)
                    model.update(id) { current ->
                        current.copy(tracking = current.tracking.map { if (it.kind == link.kind) it.copy(sent = maxOf(it.sent, chapters)) else it })
                    }
                } catch (e: TrackerException.NotSignedIn) {
                    model.fault.value = "Sign in to ${link.kind.label} again in Settings to send your progress."
                } catch (e: Exception) {
                    // The next change of the book tries again.
                }
            }
        }
    }

    /** Links a book to an entry of a tracker, and sends the progress at once. */
    fun track(id: UUID, hit: TrackerHit, kind: TrackerKind) {
        model.update(id) { book -> book.copy(tracking = book.tracking.filterNot { it.kind == kind } + TrackLink(kind, hit.id, hit.title)) }
        sendProgress(id)
    }

    fun untrack(id: UUID, kind: TrackerKind) = model.update(id) { book -> book.copy(tracking = book.tracking.filterNot { it.kind == kind }) }

    /** The link of a book on a tracker. */
    fun link(book: Book, kind: TrackerKind): TrackLink? = book.tracking.firstOrNull { it.kind == kind }

    private companion object {
        /** A code verifier of PKCE: 64 characters of the unreserved set. */
        fun verifier(): String {
            val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
            val random = SecureRandom()
            return String(CharArray(64) { characters[random.nextInt(characters.length)] })
        }
    }
}
