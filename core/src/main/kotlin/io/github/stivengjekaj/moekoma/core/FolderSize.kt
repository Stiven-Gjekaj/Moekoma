package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.util.Locale

/** The space that files take on the device. */
object FolderSize {
    /** The bytes of the files in a folder and in its folders. A folder that does not exist takes 0 bytes. */
    fun bytes(folder: File): Long = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** The size for the user, such as "12.4 MB". */
    fun text(bytes: Long): String = when {
        bytes < 1024 -> "$bytes bytes"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
