package io.github.stivengjekaj.moekoma.core

/** The pairs of pages of a reader on a wide screen, as in Asabunko. */
object Spreads {
    /**
     * The pages of each spread, in the order of the book. The first page is alone, as the cover of a book is. A wide
     * page, such as a page of two pages in one image, is alone. With [shift], each pair moves by one page, for a book
     * where the scan puts the pairs on the other pages.
     */
    fun make(count: Int, wide: (Int) -> Boolean, shift: Boolean): List<List<Int>> {
        if (count <= 0) return emptyList()
        val spreads = mutableListOf<List<Int>>()
        var index = 0
        if (!shift) {
            spreads += listOf(0)
            index = 1
        }
        while (index < count) {
            if (wide(index) || index + 1 >= count || wide(index + 1)) {
                spreads += listOf(index)
                index += 1
            } else {
                spreads += listOf(index, index + 1)
                index += 2
            }
        }
        return spreads
    }

    /** The spread that has the page. A page out of the book gives the nearest spread. */
    fun index(page: Int, spreads: List<List<Int>>): Int {
        val found = spreads.indexOfFirst { page in it }
        if (found >= 0) return found
        return if (page < 0) 0 else maxOf(spreads.size - 1, 0)
    }
}
