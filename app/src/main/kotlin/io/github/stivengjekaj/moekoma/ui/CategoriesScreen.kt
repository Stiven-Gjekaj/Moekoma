package io.github.stivengjekaj.moekoma.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.stivengjekaj.moekoma.core.BookCategory
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.ui.theme.Gutter
import io.github.stivengjekaj.moekoma.ui.theme.LocalPalette

/** Adds, renames, orders, and removes the categories of the read list. */
@Composable
fun CategoriesScreen(model: AppModel, onBack: () -> Unit) {
    val library by model.library.collectAsState()
    val palette = LocalPalette.current
    var name by remember { mutableStateOf("") }
    fun add() {
        val text = name.trim()
        if (text.isEmpty()) return
        model.change { it.copy(categories = it.categories + BookCategory(name = text)) }
        name = ""
    }
    fun move(index: Int, by: Int) = model.change { lib ->
        val list = lib.categories.toMutableList()
        val to = index + by
        if (to !in list.indices) return@change lib
        list.add(to, list.removeAt(index))
        lib.copy(categories = list)
    }

    SubScreen("Categories", onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("New category") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = ::add, enabled = name.isNotBlank()) { Text("Add", color = if (name.isNotBlank()) palette.accent else palette.textSecondary) }
            }
            if (library.categories.isNotEmpty()) {
                Section(null, footer = "Hold a book of the read list to put it in a category. A book stays in the read list when its category goes.") {
                    library.categories.forEachIndexed { index, item ->
                        if (index > 0) Divider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            OutlinedTextField(
                                value = item.name, singleLine = true, modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                onValueChange = { new -> model.change { lib -> lib.copy(categories = lib.categories.map { if (it.id == item.id) it.copy(name = new) else it }) } },
                            )
                            IconButton(onClick = { move(index, -1) }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move ${item.name} up") }
                            IconButton(onClick = { move(index, 1) }, enabled = index < library.categories.size - 1) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move ${item.name} down") }
                            IconButton(onClick = { model.change { it.removeCategory(item.id) } }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove ${item.name}", tint = palette.danger) }
                        }
                    }
                }
            } else {
                Text("Put the books of your read list in categories, such as \"Reading\" or \"On hold\".", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
    }
}
