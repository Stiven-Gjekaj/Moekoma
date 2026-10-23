package io.github.stivengjekaj.moekoma.core

import java.text.Normalizer

/**
 * The order of the pages: "page 2" comes before "page 10".
 * It compares a path one folder at a time, so that a folder of chapter 2 comes
 * before a folder of chapter 10. Case and full-width forms do not count, as in Asabunko.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val left = a.split('/').filter { it.isNotEmpty() }
        val right = b.split('/').filter { it.isNotEmpty() }
        for (i in 0 until minOf(left.size, right.size)) {
            val result = comparePart(left[i], right[i])
            if (result != 0) return result
        }
        if (left.size != right.size) return left.size.compareTo(right.size)
        // Two names that differ only in case keep one order each time.
        return a.compareTo(b)
    }

    fun sorted(paths: List<String>): List<String> = paths.sortedWith(this)

    private fun comparePart(a: String, b: String): Int {
        val x = fold(a)
        val y = fold(b)
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            if (x[i].isAsciiDigit() && y[j].isAsciiDigit()) {
                val startI = i
                val startJ = j
                while (i < x.length && x[i].isAsciiDigit()) i++
                while (j < y.length && y[j].isAsciiDigit()) j++
                val numberX = x.substring(startI, i).trimStart('0')
                val numberY = y.substring(startJ, j).trimStart('0')
                if (numberX.length != numberY.length) return numberX.length.compareTo(numberY.length)
                val digits = numberX.compareTo(numberY)
                if (digits != 0) return digits
            } else {
                if (x[i] != y[j]) return x[i].compareTo(y[j])
                i++
                j++
            }
        }
        return (x.length - i).compareTo(y.length - j)
    }

    /** Full-width letters and digits become their normal forms, and capitals become small letters. */
    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
