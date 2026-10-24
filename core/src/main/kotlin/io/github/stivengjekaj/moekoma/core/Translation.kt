package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A table that gives a text in a different language. The English text is the key, as in a String Catalog.
 * A key can hold a value: "%@" for a text and "%d" for a number. The translation puts the values back
 * in order, or at "%1$@" and "%2$d" when the order changes.
 */
class Translation(table: Map<String, String>) {
    private val exact = table.filterKeys { !it.contains('%') }
    private val templates = table.filterKeys { it.contains('%') }.entries
        // A key with more fixed text is more specific, so it goes first.
        .sortedByDescending { it.key.replace(VALUE, "").length }
        .map { (key, value) -> pattern(key) to value }

    /** The translation of [text], or [text] when the table does not have it. */
    fun of(text: String): String {
        exact[text]?.let { return it }
        if (text.isBlank()) return text
        for ((pattern, value) in templates) {
            val match = pattern.matchEntire(text) ?: continue
            return fill(value, match.groupValues.drop(1))
        }
        return text
    }

    companion object {
        private val VALUE = Regex("%(?:\\d+\\$)?[@d]")

        fun parse(json: String): Translation =
            Translation(Json.parseToJsonElement(json).let { it as JsonObject }.mapValues { it.value.jsonPrimitive.content })

        private fun pattern(key: String): Regex {
            val text = StringBuilder()
            var last = 0
            VALUE.findAll(key).forEach { found ->
                text.append(Regex.escape(key.substring(last, found.range.first)))
                text.append(if (found.value.endsWith("d")) "(-?\\d+)" else "(.+?)")
                last = found.range.last + 1
            }
            text.append(Regex.escape(key.substring(last)))
            return Regex(text.toString(), RegexOption.DOT_MATCHES_ALL)
        }

        private fun fill(value: String, values: List<String>): String {
            var next = 0
            return VALUE.replace(value) { found ->
                val position = found.value.drop(1).substringBefore('$', "").toIntOrNull()
                values.getOrNull(if (position != null) position - 1 else next++) ?: ""
            }
        }
    }
}
