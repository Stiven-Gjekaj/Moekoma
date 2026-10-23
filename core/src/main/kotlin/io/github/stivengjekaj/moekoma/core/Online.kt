package io.github.stivengjekaj.moekoma.core

import kotlinx.serialization.Serializable
import java.util.UUID

/** The online source of a book, so that the app can get its new chapters. */
@Serializable
data class BookOrigin(
    /** The line of the source list. */
    @Serializable(with = UuidSerializer::class) val list: UUID,
    /** The source in that list, such as the ID of an extension. Empty for an OPDS catalog or a server. */
    val source: String,
    /** The series or the novel in the source. */
    val series: String,
)

/** The facts of one source of an ecosystem, from its list. */
@Serializable
data class SourceInfo(
    val id: String,
    val name: String,
    val language: String,
    val version: String,
    val icon: String? = null,
    /** The URL of the code of the source. */
    val code: String,
    /** True for novels, false for comics. */
    val novels: Boolean,
    /** True when the source can show adult content. */
    val adult: Boolean = false,
    /** True when the source has a list of the latest updates. */
    val hasLatest: Boolean = true,
)

/** A source that the user installed from a list. */
@Serializable
data class InstalledSource(
    @Serializable(with = UuidSerializer::class) val list: UUID,
    val info: SourceInfo,
) {
    val id: String get() = "${list.toString().uppercase()}/${info.id}"
}

/** A series or a novel of an online source, in a list. */
@Serializable
data class OnlineSeries(
    /** The ID of the series in its source, such as a path or a key. */
    val id: String,
    val title: String,
    val cover: String? = null,
)

/** One chapter of a series or of a novel. */
@Serializable
data class OnlineChapter(
    val id: String,
    val title: String,
    val number: Double? = null,
    val date: String? = null,
    val group: String? = null,
)
