package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.AppContainer
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.OptionValue
import io.github.stivengjekaj.moekoma.core.SourceOption
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/** One filter or setting of a source, in the form of its kind. */
@Composable
fun OptionEditor(option: SourceOption, value: OptionValue, onChange: (OptionValue) -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        when (val kind = option.kind) {
            is SourceOption.Kind.Toggle -> {
                val on = (value as? OptionValue.Toggle)?.on ?: false
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(OptionValue.Toggle(!on)) }) {
                    Text(option.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(on, null, colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent))
                }
            }
            is SourceOption.Kind.Text -> OutlinedTextField(
                value = (value as? OptionValue.Text)?.text.orEmpty(), onValueChange = { onChange(OptionValue.Text(it)) }, singleLine = true,
                label = { Text(option.title) }, placeholder = kind.placeholder?.let { { Text(it) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            is SourceOption.Kind.Select -> {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                val chosen = (value as? OptionValue.Select)?.choice
                ChoiceChips(kind.choices.map { it.label }, kind.choices.indexOfFirst { it.value == chosen }) { onChange(OptionValue.Select(kind.choices[it].value)) }
            }
            is SourceOption.Kind.Sort -> {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                val sort = value as? OptionValue.Sort ?: OptionValue.Sort(0, false)
                ChoiceChips(kind.choices.map { it.label }, sort.index) { onChange(sort.copy(index = it)) }
                if (kind.canAscend) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(sort.copy(ascending = !sort.ascending)) }) {
                        Text("Ascending", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(sort.ascending, null, colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent))
                    }
                }
            }
            is SourceOption.Kind.Multi -> {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                val multi = value as? OptionValue.Multi ?: OptionValue.Multi(emptyList())
                if (kind.excludable) Text("Tap once to include, two times to exclude.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kind.choices.forEach { choice ->
                        val state = when (choice.value) {
                            in multi.included -> 1
                            in multi.excluded -> 2
                            else -> 0
                        }
                        val label = when (state) { 1 -> "+ " ; 2 -> "− " ; else -> "" } + choice.label
                        Text(
                            label, style = MaterialTheme.typography.labelLarge,
                            color = if (state == 0) palette.textPrimary else palette.onAccent,
                            modifier = Modifier.clip(CircleShape).background(when (state) { 1 -> palette.accent; 2 -> palette.danger; else -> palette.surfaceHigh })
                                .clickable {
                                    // Off, included, excluded when the source can exclude, then off.
                                    val next = if (state == 0) 1 else if (state == 1 && kind.excludable) 2 else 0
                                    onChange(OptionValue.Multi(
                                        multi.included.filter { it != choice.value } + if (next == 1) listOf(choice.value) else emptyList(),
                                        multi.excluded.filter { it != choice.value } + if (next == 2) listOf(choice.value) else emptyList(),
                                    ))
                                }
                                .heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                                .semantics { stateDescription = when (state) { 1 -> "Included"; 2 -> "Excluded"; else -> "Off" } },
                        )
                    }
                }
            }
            is SourceOption.Kind.Range -> {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                val range = value as? OptionValue.Range ?: OptionValue.Range(null, null)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("From" to range.from, "To" to range.to).forEachIndexed { index, (label, number) ->
                        OutlinedTextField(
                            value = number?.let { if (kind.decimal) it.toString() else it.toLong().toString() }.orEmpty(),
                            onValueChange = { text ->
                                val parsed = text.toDoubleOrNull()
                                onChange(if (index == 0) range.copy(from = parsed) else range.copy(to = parsed))
                            },
                            singleLine = true, label = { Text(label) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accent, cursorColor = palette.accent),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val palette = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                label, style = MaterialTheme.typography.labelLarge, color = if (on) palette.onAccent else palette.textPrimary,
                modifier = Modifier.clip(CircleShape).background(if (on) palette.accent else palette.surfaceHigh).clickable { onSelect(index) }
                    .heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = label + if (on) ", selected" else "" },
            )
        }
    }
}

/** The filters of a source in a sheet. Apply gives the values that differ from the first values. */
@Composable
fun FilterSheet(options: List<SourceOption>, applied: Map<String, OptionValue>, onApply: (Map<String, OptionValue>) -> Unit) {
    var values by remember(applied) { mutableStateOf(options.associate { it.id to (applied[it.id] ?: it.initial) }) }
    Column(Modifier.navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { values = options.associate { it.id to it.initial } }) { Text("Reset", color = LocalPalette.current.accent) }
            PillButton("Apply", { onApply(values.filter { (id, value) -> options.firstOrNull { it.id == id }?.initial != value }) })
        }
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(options, key = { it.id }) { option ->
                OptionEditor(option, values[option.id] ?: option.initial) { values = values + (option.id to it) }
            }
        }
    }
}

/** The settings of a source. Each change goes to the source at once. */
@Composable
fun SourceSettingsScreen(container: AppContainer, installed: InstalledSource, open: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<List<SourceOption>?>(null) }
    var values by remember { mutableStateOf<Map<String, OptionValue>>(emptyMap()) }
    var asForm by remember { mutableStateOf(false) }
    LaunchedEffect(installed.id) {
        val source = runCatching { container.sources.load(installed) }.getOrNull()
        asForm = runCatching { source?.hasSettingsForm() == true }.getOrDefault(false)
        options = source?.settingOptions().orEmpty()
        values = source?.settingValues().orEmpty()
    }
    // A source with a settings form shows the form, with its groups, pages, and sign-ins.
    if (asForm) return SourceFormScreen(container, installed, "root", "${installed.info.name} settings", open, onBack)
    SubScreen("${installed.info.name} settings", onBack) {
        val list = options
        if (list == null) {
            CircularProgressIndicator(color = palette.accent, modifier = Modifier.padding(32.dp))
        } else if (list.isEmpty()) {
            EmptyArt("No settings", "This source has no settings.")
        } else {
            LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(list, key = { it.id }) { option ->
                    OptionEditor(option, values[option.id] ?: option.initial) { value ->
                        values = values + (option.id to value)
                        scope.launch { runCatching { container.sources.load(installed).setSetting(option.id, value) } }
                    }
                }
                item {
                    Text("A change takes effect at the next load of a list.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
        }
    }
}
