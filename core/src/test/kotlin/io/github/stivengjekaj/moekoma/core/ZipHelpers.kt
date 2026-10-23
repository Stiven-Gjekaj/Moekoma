package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Writes a ZIP with these files. A name can hold "..", as a damaged archive can. */
fun writeZip(file: File, files: List<Pair<String, ByteArray>>) {
    ZipOutputStream(file.outputStream()).use { zip ->
        for ((name, data) in files) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
}

/** The files of a ZIP, for the tests. The app reads each archive with libarchive. */
class ZipFiles(private val file: File) : ComicFiles {
    override fun forEach(body: (path: String, open: () -> InputStream) -> Unit) {
        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.isDirectory) body(entry.name) { zip.getInputStream(entry) }
            }
        }
    }
}
