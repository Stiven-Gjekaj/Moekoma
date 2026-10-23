package io.github.stivengjekaj.moekoma.core

import java.text.Normalizer

/** Finds one title in many sources. Two sources can write the same title in a different form. */
object TitleMatch {
    private val marks = Regex("\\p{M}+")

    /** The title with only its letters and digits, in lowercase and with no accents. */
    fun key(title: String): String {
        // NFKD splits a letter from its accent and makes a full-width letter a normal letter.
        val folded = marks.replace(Normalizer.normalize(title, Normalizer.Form.NFKD), "").lowercase()
        return folded.filter { it.isLetterOrDigit() }
    }

    /**
     * How well a title agrees with a search: 3 for the same title, 2 when it starts with the search,
     * 1 when it holds the search, and 0 when it does not.
     */
    fun score(title: String, query: String): Int {
        val t = key(title)
        val q = key(query)
        if (q.isEmpty()) return 0
        return when {
            t == q -> 3
            t.startsWith(q) -> 2
            q in t -> 1
            else -> 0
        }
    }
}

/** One series in one source. */
data class SearchHit(
    /** The ID of the installed source. */
    val source: String,
    val series: OnlineSeries,
)

/** One title of a search, with each source that has it. */
data class SearchGroup(
    val key: String,
    /** The title as the first source writes it. */
    val title: String,
    /** The first hit that gives a cover. */
    val cover: SearchHit?,
    val hits: List<SearchHit>,
)

/** The results of a search in many sources. The results of each source come in at a different time. */
class SearchResults(val query: String) {
    private val groups = mutableListOf<SearchGroup>()
    private val index = mutableMapOf<String, Int>()

    /** Adds the series of one source. A series with the same title as a series of another source goes into its group. */
    fun add(items: List<OnlineSeries>, source: String) {
        for (series in items) {
            val key = TitleMatch.key(series.title)
            if (key.isEmpty()) continue
            val hit = SearchHit(source, series)
            val at = index[key]
            if (at != null) {
                val group = groups[at]
                // One source can give the same title two times. The group keeps the first.
                if (group.hits.any { it.source == source }) continue
                groups[at] = group.copy(hits = group.hits + hit, cover = group.cover ?: hit.takeIf { series.cover != null })
            } else {
                index[key] = groups.size
                groups += SearchGroup(key, series.title, hit.takeIf { series.cover != null }, listOf(hit))
            }
        }
    }

    /**
     * The groups, with the best match first: the same title, then the title that more sources have,
     * then the order in which the results came.
     */
    val sorted: List<SearchGroup>
        get() = groups.withIndex().sortedWith(
            compareByDescending<IndexedValue<SearchGroup>> { TitleMatch.score(it.value.title, query) }
                .thenByDescending { it.value.hits.size }
                .thenBy { it.index },
        ).map { it.value }
}

/** Chooses the source of a title. */
object SourceChoice {
    /**
     * The number of different chapters. Some sources give one chapter two or more times,
     * once for each group that translated it. A chapter with no number counts one time.
     */
    fun distinctChapters(chapters: List<OnlineChapter>): Int =
        chapters.mapNotNull { it.number }.toSet().size + chapters.count { it.number == null }

    /** The source with the most chapters. When two sources have the same number, the first one wins. A source with no answer has null. */
    fun best(chapters: List<Pair<String, Int?>>): String? {
        var best: Pair<String, Int>? = null
        for ((source, count) in chapters) {
            if (count == null || count <= 0) continue
            if (best == null || count > best.second) best = source to count
        }
        return best?.first ?: chapters.firstOrNull()?.first
    }
}

/**
 * Puts the lists of some sources into one list: the first of each source, then the second of each, and so on.
 * A title that an earlier source gave does not come two times.
 */
object TitleMix {
    fun roundRobin(lists: List<List<SearchHit>>, limit: Int = Int.MAX_VALUE): List<SearchHit> {
        val mixed = mutableListOf<SearchHit>()
        val seen = mutableSetOf<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in lists) {
                if (i >= list.size) continue
                val key = TitleMatch.key(list[i].series.title)
                if (key.isEmpty() || !seen.add(key)) continue
                mixed += list[i]
                if (mixed.size == limit) return mixed
            }
        }
        return mixed
    }
}
