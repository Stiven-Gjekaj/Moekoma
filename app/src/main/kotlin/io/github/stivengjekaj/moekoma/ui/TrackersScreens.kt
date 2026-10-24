package io.github.stivengjekaj.moekoma.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.TrackerHit
import io.github.stivengjekaj.moekoma.core.TrackerKind
import io.github.stivengjekaj.moekoma.core.label
import io.github.stivengjekaj.moekoma.core.redirect
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import java.util.UUID
import kotlinx.coroutines.launch

/** The sign-in to AniList and MyAnimeList. The app has no client of its own on these sites, so the user registers a client and gives its ID here. */
@Composable
fun TrackersScreen(container: AppContainer, onBack: () -> Unit) {
    val accounts by container.trackers.accounts.collectAsState()
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf<Pair<TrackerKind, String>?>(null) }
    SubScreen("Trackers", onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
        ) {
            TrackerKind.entries.forEach { kind ->
                var client by remember(kind) { mutableStateOf(container.trackers.clientId(kind)) }
                Section(
                    kind.label,
                    footer = "Register a client on ${kind.label} with the redirect URI ${kind.redirect}, and paste its ID here. Then hold a book in the Library and choose Track.",
                ) {
                    OutlinedTextField(
                        value = client, singleLine = true, label = { Text("${kind.label} client ID") },
                        onValueChange = { client = it; container.trackers.setClientId(kind, it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Divider()
                    if (kind in accounts) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp)) {
                            Text(kind.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("Signed in", color = palette.textSecondary)
                        }
                        Divider()
                        LinkRow("Sign out of ${kind.label}", color = palette.danger) { container.trackers.signOut(kind) }
                    } else {
                        val ready = client.isNotBlank()
                        LinkRow("Sign in to ${kind.label}", bold = true, color = if (ready) palette.accent else palette.textSecondary) {
                            if (ready) signingIn = kind to container.trackers.signInUrl(kind)
                        }
                    }
                }
            }
        }
    }
    signingIn?.let { (kind, url) ->
        TrackerSignIn(url, kind.redirect.substringBefore(':'), onClose = { signingIn = null }) { callback ->
            signingIn = null
            scope.launch {
                runCatching { container.trackers.finishSignIn(kind, callback) }
                    .onFailure { container.model.fault.value = "The sign-in to ${kind.label} did not work: ${it.message}" }
            }
        }
    }
}

/** The page of the sign-in of a tracker. The sign-in ends at the redirect URI of the app, which the page does not open. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrackerSignIn(url: String, scheme: String, onClose: () -> Unit, done: (String) -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SubScreen("Sign in", onClose) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(page: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url.toString()
                                if (target.startsWith("$scheme:")) {
                                    done(target)
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Links a book to an entry of a tracker: a search with the title of the book, then a tap on the entry. */
@Composable
fun TrackSheet(container: AppContainer, bookId: UUID, onDone: () -> Unit) {
    val library by container.model.library.collectAsState()
    val accounts by container.trackers.accounts.collectAsState()
    val palette = LocalPalette.current
    val book = library.book(bookId) ?: return onDone()
    var kind by remember { mutableStateOf(TrackerKind.entries.firstOrNull { it in accounts } ?: TrackerKind.AniList) }
    var query by remember { mutableStateOf(book.title) }
    var hits by remember { mutableStateOf(emptyList<TrackerHit>()) }
    var searching by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun search() {
        searching = true
        failure = null
        scope.launch {
            try {
                hits = container.trackers.tracker(kind).search(query)
                if (hits.isEmpty()) failure = "${kind.label} has no title with these words."
            } catch (e: Exception) {
                failure = e.message
            } finally {
                searching = false
            }
        }
    }
    LaunchedEffect(kind) {
        hits = emptyList()
        if (kind in accounts) search()
    }
    Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Track", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackerKind.entries.forEach { option -> ChoiceChip(option.label, option == kind) { kind = option } }
        }
        container.trackers.link(book, kind)?.let { link ->
            Text("Linked to ${link.title}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { container.trackers.untrack(bookId, kind) }) { Text("Stop tracking", color = palette.danger) }
        }
        if (kind !in accounts) {
            Text("Sign in to ${kind.label} in Settings, Trackers.", color = palette.textSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Title") }, modifier = Modifier.weight(1f))
                TextButton(onClick = ::search, enabled = query.isNotBlank() && !searching) { Text("Search", color = palette.accent) }
            }
            if (searching) CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent)
            failure?.let { Text(it, color = palette.danger) }
            hits.forEach { hit ->
                Column(Modifier.fillMaxWidth().clickable { container.trackers.track(bookId, hit, kind); onDone() }.padding(vertical = 8.dp)) {
                    Text(hit.title, style = MaterialTheme.typography.bodyLarge)
                    hit.chapters?.let { Text(if (it == 1) "1 chapter" else "$it chapters", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
                }
            }
            Text("The tracker gets the number of chapters that you read, each time it goes up.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
    }
}

/** A chip that chooses one of a few options. */
@Composable
private fun ChoiceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (selected) palette.accent else palette.surfaceHigh)
            .selectable(selected = selected, onClick = onClick).padding(horizontal = 14.dp),
    ) { Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) palette.onAccent else palette.textPrimary) }
}
