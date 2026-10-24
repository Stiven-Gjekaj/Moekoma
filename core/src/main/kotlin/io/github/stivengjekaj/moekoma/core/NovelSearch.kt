package io.github.stivengjekaj.moekoma.core

import java.text.Normalizer

/** The search of the words of a novel in the chapters that are on the device, as in Asabunko. */
object NovelSearch {
    /** One place in the book where the words are, with some words around them. */
    data class Result(val chapter: Int, val snippet: String)

    /** The results, at most [limit], and the number of chapters that are not on the device. */
    data class Found(val results: List<Result>, val missing: Int)

    /**
     * Searches the HTML of each chapter. [html] gives null for a chapter that is not on the device. The search does
     * not see the tags, the case, or the accents. A search of less than two letters finds nothing.
     */
    fun search(words: String, count: Int, limit: Int = 100, html: (Int) -> String?): Found {
        val query = words.trim()
        if (query.length < 2) return Found(emptyList(), 0)
        val key = fold(query)
        val results = mutableListOf<Result>()
        var missing = 0
        for (index in 0 until count) {
            val page = html(index)
            if (page == null) { missing++; continue }
            val text = page.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
            // The folded text keeps the length of the text, so a place in it is a place in the text.
            val at = fold(text).indexOf(key)
            if (at < 0) continue
            val before = text.substring(maxOf(at - 40, 0), at)
            val after = text.substring(at + query.length, minOf(at + query.length + 60, text.length))
            results += Result(index, "…" + before + text.substring(at, at + query.length) + after + "…")
            if (results.size >= limit) break
        }
        return Found(results, missing)
    }

    /** The text in lower case with no accents. Each letter stays one letter, so the length stays the same. */
    private fun fold(text: String): String = buildString(text.length) {
        for (char in text) {
            val plain = Normalizer.normalize(char.toString(), Normalizer.Form.NFD).firstOrNull() ?: char
            append(plain.lowercaseChar())
        }
    }
}
