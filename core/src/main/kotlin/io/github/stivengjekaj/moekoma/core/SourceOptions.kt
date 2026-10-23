package io.github.stivengjekaj.moekoma.core

/** A filter or a setting of a source, in one form for all kinds of source. As in Asabunko. */
data class SourceOption(
    val id: String,
    val title: String,
    val kind: Kind,
    /** The value before the user changes it. */
    val initial: OptionValue,
    /** The name of the type in the source, such as "segment", for the code that saves the value. */
    val raw: String = "",
) {
    sealed interface Kind {
        data class Text(val placeholder: String?) : Kind
        data object Toggle : Kind
        /** One choice of a list. */
        data class Select(val choices: List<Choice>) : Kind
        /** Some choices of a list. With [excludable], the user can also exclude a choice. */
        data class Multi(val choices: List<Choice>, val excludable: Boolean) : Kind
        /** The order of the list, with an ascending switch when [canAscend] is true. */
        data class Sort(val choices: List<Choice>, val canAscend: Boolean) : Kind
        /** Two numbers, from and to. Each bound is optional. */
        data class Range(val min: Double?, val max: Double?, val decimal: Boolean) : Kind
    }

    data class Choice(val label: String, val value: String)
}

/** The value of a filter or a setting. */
sealed interface OptionValue {
    data class Text(val text: String) : OptionValue
    data class Toggle(val on: Boolean) : OptionValue
    data class Select(val choice: String) : OptionValue
    data class Multi(val included: List<String>, val excluded: List<String> = emptyList()) : OptionValue
    data class Sort(val index: Int, val ascending: Boolean) : OptionValue
    data class Range(val from: Double?, val to: Double?) : OptionValue
}
