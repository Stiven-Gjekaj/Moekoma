package io.github.stivengjekaj.moekoma.core

/** The versions of the app are `MAJOR.MINOR.PATCH`, with an optional test label such as `-rc1`. */
object AppVersion {
    /** Tells if [candidate] is newer than [current]. A "v" before the numbers is not a part of the version. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /** A version with a test label comes before the same version without one. */
    fun compare(a: String, b: String): Int {
        val (coreA, testA) = split(a)
        val (coreB, testB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val x = coreA.getOrElse(i) { 0 }
            val y = coreB.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return when {
            testA == testB -> 0
            testA == null -> 1
            testB == null -> -1
            else -> compareTest(testA, testB)
        }
    }

    private fun split(version: String): Pair<List<Int>, String?> {
        val text = version.trim().removePrefix("v")
        val core = text.substringBefore('-')
        val test = text.substringAfter('-', "").ifEmpty { null }
        return core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 } to test
    }

    /** Compares two test labels, such as `rc2` and `rc10`, by their letters and then by their number. */
    private fun compareTest(a: String, b: String): Int {
        val letters = a.takeWhile { !it.isDigit() }.compareTo(b.takeWhile { !it.isDigit() })
        if (letters != 0) return letters
        val x = a.filter { it.isDigit() }.toIntOrNull() ?: 0
        val y = b.filter { it.isDigit() }.toIntOrNull() ?: 0
        return x.compareTo(y)
    }
}
