package io.github.stivengjekaj.moekoma.core

/** Finds a device with an e-ink screen from the maker and the brand that Android gives. */
object EInk {
    /**
     * The makers that make only e-ink readers. A maker that also makes phones with a normal
     * screen, such as Hisense, is not here. The user chooses the E-ink look in Settings.
     */
    private val makers = listOf("onyx", "boox", "bigme", "meebook", "pocketbook", "boyue", "likebook", "tolino")

    fun isEInkDevice(manufacturer: String, brand: String): Boolean {
        val maker = "$manufacturer $brand".lowercase()
        return makers.any { it in maker }
    }
}
