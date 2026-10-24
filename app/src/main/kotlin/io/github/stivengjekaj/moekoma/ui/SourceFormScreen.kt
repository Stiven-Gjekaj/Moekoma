package io.github.stivengjekaj.moekoma.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.FormInput
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.SourceForm
import io.github.stivengjekaj.moekoma.data.BrowserChecks
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/** The ID of the first form of the settings of a source. */
const val ROOT_FORM = "root"

/**
 * A settings form of a source, as in Asabunko. The source gives the form again after each action, so the rows can
 * change. A link opens the child form as the next screen.
 */
@Composable
fun SourceFormScreen(container: AppContainer, installed: InstalledSource, id: String, title: String, open: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<SourceForm?>(null) }
    var fault by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(installed.id, id) {
        try {
            val source = container.sources.load(installed)
            form = if (id == ROOT_FORM) source.settingsForm() else source.form(id)
        } catch (e: Exception) {
            fault = e.message ?: "The settings did not open."
        }
    }

    fun act(row: SourceForm.Row, input: FormInput) {
        val current = form ?: return
        busy = true
        scope.launch {
            try {
                form = container.sources.load(installed).formAction(current.id, row.id, input)
                fault = null
            } catch (e: Exception) {
                fault = e.message ?: "The setting did not change."
            } finally {
                busy = false
            }
        }
    }

    fun save() {
        val current = form ?: return
        busy = true
        scope.launch {
            try {
                container.sources.load(installed).submitForm(current.id)
                onBack()
            } catch (e: Exception) {
                fault = e.message ?: "The settings were not saved."
            } finally {
                busy = false
            }
        }
    }

    // A form that the user must save has a Save button. Its changes go to the source only then.
    SubScreen(title, onBack, actions = { if (form?.submit == true) TextButton(onClick = ::save, enabled = !busy) { Text("Save") } }) {
        val shown = form
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            fault?.let { text -> item { Text(text, color = palette.danger, style = MaterialTheme.typography.bodyMedium) } }
            if (shown == null && fault == null) item { CircularProgressIndicator(color = palette.accent, modifier = Modifier.padding(32.dp)) }
            if (shown != null) items(shown.sections, key = { it.id }) { section ->
                Section(section.header, section.footer) {
                    section.rows.forEachIndexed { index, row ->
                        if (index > 0) Divider()
                        FormRow(row, enabled = !busy, act = { input -> act(row, input) }) { child ->
                            open(SettingsRoute.SourceForm(installed, child, row.title))
                        }
                    }
                }
            }
        }
    }
}

/** One row of a settings form. */
@Composable
private fun FormRow(row: SourceForm.Row, enabled: Boolean, act: (FormInput) -> Unit, openForm: (String) -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    when (row.kind) {
        SourceForm.Row.Kind.Label -> LabelRow(row, onClick = if (row.selectable == true && enabled) ({ act(FormInput.Tap) }) else null)
        SourceForm.Row.Kind.Link -> LabelRow(row, onClick = { row.form?.let(openForm) })
        SourceForm.Row.Kind.Input -> InputRow(row, enabled) { act(FormInput.Text(it)) }
        SourceForm.Row.Kind.Toggle -> Toggle(row.title, row.on ?: false) { if (enabled) act(FormInput.Toggle(it)) }
        SourceForm.Row.Kind.Button -> {
            LinkRow(row.title, color = if (row.destructive == true) palette.danger else palette.accent) {
                if (!enabled) return@LinkRow
                if (row.confirm == null) act(FormInput.Tap) else asking = true
            }
            if (asking) AlertDialog(
                onDismissRequest = { asking = false },
                title = { Text(row.confirm ?: row.title) },
                confirmButton = { TextButton(onClick = { asking = false; act(FormInput.Tap) }) { Text(row.title, color = if (row.destructive == true) palette.danger else palette.accent) } },
                dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
            )
        }
        SourceForm.Row.Kind.Stepper -> StepperRow(row, enabled) { act(FormInput.Number(it)) }
        SourceForm.Row.Kind.Web -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                row.url?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
            }.padding(horizontal = 16.dp),
        ) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Opens in the browser", tint = palette.textSecondary)
        }
        SourceForm.Row.Kind.Login -> SignInRow(row, enabled, act)
        SourceForm.Row.Kind.Unsupported -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(row.title, color = palette.textSecondary, style = MaterialTheme.typography.bodyLarge)
            Text("Moekoma cannot do this action.", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LabelRow(row: SourceForm.Row, onClick: (() -> Unit)?) {
    val palette = LocalPalette.current
    val base = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { if (row.mark == "included") selected = true },
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            // A subtitle that only says the value again does not show.
            row.subtitle?.takeIf { it != row.value }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
        }
        row.value?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, maxLines = 2, modifier = Modifier.padding(start = 12.dp))
        }
        when (row.mark) {
            "included" -> Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.padding(start = 8.dp))
            "excluded" -> Icon(Icons.Outlined.Close, contentDescription = "Excluded", tint = palette.danger, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A text row. The text goes to the source when the user presses Done or leaves the field. */
@Composable
private fun InputRow(row: SourceForm.Row, enabled: Boolean, send: (String) -> Unit) {
    var text by remember(row.id, row.value) { mutableStateOf(row.value ?: "") }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        if (text == (row.value ?: "")) return
        send(text)
        // A field with no value adds an entry to a list, so it is empty again for the next entry.
        if (row.value == null) text = ""
    }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, enabled = enabled, singleLine = true,
        label = { Text(row.title) },
        visualTransformation = if (row.secure == true) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false, keyboardType = if (row.secure == true) KeyboardType.Password else KeyboardType.Text),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.fillMaxWidth().padding(12.dp).onFocusChanged { state ->
            if (focused && !state.isFocused) commit()
            focused = state.isFocused
        },
    )
}

@Composable
private fun StepperRow(row: SourceForm.Row, enabled: Boolean, change: (Double) -> Unit) {
    val value = row.number ?: 0.0
    val step = row.step ?: 1.0
    val text = if (value == Math.rint(value)) value.toLong().toString() else value.toString()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp)) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { change(value - step) }, enabled = enabled && value - step >= (row.minimum ?: Double.NEGATIVE_INFINITY)) {
            Icon(Icons.Outlined.Remove, contentDescription = "Less")
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { contentDescription = "${row.title}, $text" })
        IconButton(onClick = { change(value + step) }, enabled = enabled && value + step <= (row.maximum ?: Double.POSITIVE_INFINITY)) {
            Icon(Icons.Outlined.Add, contentDescription = "More")
        }
    }
}

/** A sign-in: a user name and a password, a page in a web view, or an OAuth page. A tap on a done sign-in signs out. */
@Composable
private fun SignInRow(row: SourceForm.Row, enabled: Boolean, act: (FormInput) -> Unit) {
    val palette = LocalPalette.current
    val login = row.login ?: return
    var asking by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var webPage by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LabelRow(row) {
        if (!enabled) return@LabelRow
        when {
            login.signedIn -> asking = true
            login.method == "basic" -> {
                username = login.username ?: ""
                password = ""
                signingIn = true
            }
            else -> webPage = true
        }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(row.title) },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                act(FormInput.Logout)
                if (login.clearsCookies == true) login.url?.let(::removeCookies)
            }) { Text(row.title, color = palette.danger) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
    )
    if (signingIn) AlertDialog(
        onDismissRequest = { signingIn = false },
        title = { Text(row.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    username, { username = it }, singleLine = true, label = { Text(if (login.useEmail == true) "Email" else "User name") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (login.useEmail == true) KeyboardType.Email else KeyboardType.Text, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    password, { password = it }, singleLine = true, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { signingIn = false; act(FormInput.Login(username, password)) }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = { signingIn = false }) { Text("Cancel") } },
    )
    if (webPage) {
        SignInPage(login, onClose = { webPage = false }) { input ->
            webPage = false
            act(input)
        }
    }
}

/** Removes the cookies of the site of a sign-in. */
private fun removeCookies(url: String) {
    val manager = CookieManager.getInstance()
    val header = manager.getCookie(url) ?: return
    for (part in header.split(';')) {
        val name = part.substringBefore('=').trim().ifEmpty { continue }
        manager.setCookie(url, "$name=; Max-Age=0; Path=/")
    }
    manager.flush()
}

/**
 * The page of a web or OAuth sign-in in a web view. A web sign-in ends when the user taps Done: the source gets the
 * cookies of the site and the values of its local storage. An OAuth sign-in ends at the URL of its callback scheme.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SignInPage(login: SourceForm.Login, onClose: () -> Unit, done: (FormInput) -> Unit) {
    val url = login.url ?: return onClose()
    val scheme = login.callbackScheme ?: "aidoku"
    var view by remember { mutableStateOf<WebView?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SubScreen(if (login.method == "oauth") "Sign in" else "Sign in, then tap Done", onClose, actions = {
            if (login.method != "oauth") TextButton(onClick = {
                val page = view ?: return@TextButton
                val cookies = CookieManager.getInstance().getCookie(page.url ?: url).orEmpty().split(';')
                    .filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                val keys = login.storageKeys.orEmpty()
                val script = "JSON.stringify(Object.fromEntries(${JSONObject.wrap(keys)}.map((k) => [k, localStorage.getItem(k)]).filter((e) => e[1] !== null)))"
                page.evaluateJavascript(script) { result ->
                    val storage = runCatching {
                        val json = JSONObject(JSONTokener(result).nextValue() as String)
                        json.keys().asSequence().associateWith { json.getString(it) }
                    }.getOrDefault(emptyMap())
                    done(FormInput.Web(cookies, storage))
                }
            }) { Text("Done") }
        }) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = BrowserChecks.userAgent(context)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(page: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url.toString()
                                if (login.method == "oauth" && target.startsWith("$scheme:")) {
                                    done(FormInput.Callback(target))
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(url)
                        view = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
