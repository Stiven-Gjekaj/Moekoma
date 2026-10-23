package io.github.stivengjekaj.moekoma.core

import kotlin.math.roundToInt

/** Puts the chapters of a long series into parts: volumes, or ranges such as 1-50. */
object ChapterGroups {
    /** The chapters of one volume, by their place in the list. */
    data class Volume(
        /** "Volume 2", or "No volume" for the chapters with no volume. */
        val name: String,
        val chapters: List<Int>,
    )

    private val volumePattern = Regex("""(?i)\b(?:vol(?:ume)?\.?|v\.)\s*(\d+(?:\.\d+)?)""")
    private val numberPattern = Regex("""(?i)(?:ch(?:apter|\.)?|episode|ep\.?|#)\s*(\d+(?:\.\d+)?)""")
    private val anyNumber = Regex("""(\d+(?:\.\d+)?)""")

    /** The number of the volume in a title, such as "2" in "Vol. 2 Ch. 10" or "Volume 2: The Sea". */
    fun volume(title: String): String? {
        val text = volumePattern.find(title)?.groupValues?.get(1) ?: return null
        // "01" and "1" are the same volume.
        val value = text.toDoubleOrNull() ?: return text
        return if (value == Math.rint(value)) value.toLong().toString() else value.toString()
    }

    /** The volumes in the order of their first chapter. Null when no chapter has a volume, because then the volumes do not help. */
    fun volumes(titles: List<String>): List<Volume>? {
        val chapters = linkedMapOf<String, MutableList<Int>>()
        var found = false
        titles.forEachIndexed { index, title ->
            val number = volume(title)
            if (number != null) found = true
            chapters.getOrPut(number?.let { "Volume $it" } ?: "No volume") { mutableListOf() } += index
        }
        if (!found) return null
        return chapters.map { (name, list) -> Volume(name, list) }
    }

    /**
     * The number of a chapter in its title, such as 12 in "Vol. 2 Ch. 12: The Sea" or 7.5 in "Chapter 7.5".
     * With no word such as "Ch.", the last number of the title counts.
     */
    fun number(title: String): Double? {
        numberPattern.find(title)?.let { return it.groupValues[1].toDoubleOrNull() }
        // A volume number is not the chapter number.
        val rest = volumePattern.replace(title, "")
        return anyNumber.findAll(rest).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** The place in a new list of chapters of the chapter that the user reads in an old list: the chapter with the same number, or else the same part of the list. */
    fun place(index: Int, old: List<String>, new: List<String>): Int {
        if (new.isEmpty()) return 0
        if (index in old.indices) {
            val number = number(old[index])
            if (number != null) {
                val match = new.indexOfFirst { number(it) == number }
                if (match >= 0) return match
            }
        }
        if (old.size <= 1) return 0
        return minOf((index.toDouble() / (old.size - 1) * (new.size - 1)).roundToInt(), new.size - 1)
    }

    /** Ranges of [size] chapters, such as 0 until 50 and 50 until 100. One range when the list is short. */
    fun ranges(count: Int, size: Int = 50): List<IntRange> {
        if (count <= 0 || size <= 0) return emptyList()
        return (0 until count step size).map { it until minOf(it + size, count) }
    }

    /** The name of a range for the user, with the first chapter as 1: "1-50". */
    fun name(range: IntRange): String =
        if (range.first == range.last) "${range.first + 1}" else "${range.first + 1}-${range.last + 1}"
}
