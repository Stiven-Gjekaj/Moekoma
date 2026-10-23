package io.github.stivengjekaj.moekoma.core

import java.util.Locale

/** Chooses the sources of a list that the app installs with no question. */
object SourceLanguages {
    /** Codes of sources for all languages. */
    private val everyLanguage = setOf("", "multi", "all", "global", "international")

    /** True when the source is in one of the languages, such as "en" or "en-US", or in all languages. A list can give a code, such as "en", or a name, such as "English". */
    fun matches(language: String, preferred: List<String>): Boolean {
        val value = language.trim().lowercase()
        if (value in everyLanguage) return true
        val codes = preferred.map(::code).toSet()
        if (code(value) in codes) return true
        return codes.any { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).lowercase() == value }
    }

    /** The sources to install: in the languages, and not for adults when [adult] is false. */
    fun pick(sources: List<SourceInfo>, preferred: List<String>, adult: Boolean): List<SourceInfo> =
        sources.filter { (adult || !it.adult) && matches(it.language, preferred) }

    /** The changes to the sources of one list after a save of the source list. */
    data class Plan(val install: List<SourceInfo>, val remove: Set<String>)

    /**
     * The app installs the English sources that are not on the device yet, and removes the installed sources that
     * left the list. The other installed sources stay as they are, so a save does not download them again.
     */
    fun sync(available: List<SourceInfo>, installed: Set<String>, adult: Boolean): Plan {
        val install = pick(available, listOf("en"), adult).filter { it.id !in installed }
        val remove = installed - available.map { it.id }.toSet()
        return Plan(install, remove)
    }

    /** "en" from "en-US" or "en_GB". */
    private fun code(language: String): String = language.lowercase().split('-', '_').first()
}
