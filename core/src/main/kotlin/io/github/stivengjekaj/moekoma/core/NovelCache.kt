package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The chapters of each book in memory, as in Asabunko. A book stays in memory until its file changes, because the
 * library, the menus, and the chapter lists ask for it often, and a long series has a large file.
 */
class NovelCache {
    private class Entry(val modified: Long, val size: Long, val novel: Novel)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** The novel in [folder], or null when its file does not load. */
    fun get(folder: File): Novel? {
        val file = File(folder, Novel.FILE_NAME)
        val modified = file.lastModified()
        val size = file.length()
        entries[folder.path]?.takeIf { it.modified == modified && it.size == size }?.let { return it.novel }
        val novel = runCatching { Novel.load(folder) }.getOrNull()
        if (novel == null) entries.remove(folder.path) else entries[folder.path] = Entry(modified, size, novel)
        return novel
    }
}
