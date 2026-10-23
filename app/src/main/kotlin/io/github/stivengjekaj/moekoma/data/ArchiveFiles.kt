package io.github.stivengjekaj.moekoma.data

import android.os.ParcelFileDescriptor
import io.github.stivengjekaj.moekoma.core.ComicFiles
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Reads a ZIP, RAR, RAR5, 7z, or TAR archive with libarchive.
 * A CBZ is a ZIP, a CBR is a RAR, a CB7 is a 7z, and a CBT is a TAR.
 * [open] gives a new descriptor of the file for each read of the archive.
 */
class ArchiveFiles(private val open: () -> ParcelFileDescriptor) : ComicFiles {
    override fun forEach(body: (path: String, open: () -> InputStream) -> Unit) {
        open().use { descriptor ->
            val archive = Archive.readNew()
            try {
                Archive.readSupportFilterAll(archive)
                Archive.readSupportFormatZip(archive)
                Archive.readSupportFormatRar(archive)
                Archive.readSupportFormatRar5(archive)
                Archive.readSupportFormat7zip(archive)
                Archive.readSupportFormatTar(archive)
                Archive.readOpenFd(archive, descriptor.fd, BLOCK_SIZE)
                while (true) {
                    val entry = Archive.readNextHeader(archive)
                    if (entry == 0L) break
                    val path = pathname(entry)
                    if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG || path == null) continue
                    // libarchive skips the data of an entry that the app does not read.
                    body(path) { EntryStream(archive) }
                }
            } finally {
                Archive.readFree(archive)
            }
        }
    }

    private fun pathname(entry: Long): String? {
        ArchiveEntry.pathnameUtf8(entry)?.let { return it }
        val raw = ArchiveEntry.pathname(entry) ?: return null
        // A name that is not UTF-8, such as Shift JIS from an old archive, keeps its bytes as Latin-1.
        return runCatching { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString() }
            .getOrElse { String(raw, Charset.forName("ISO-8859-1")) }
    }

    /** The data of the current entry of the archive. It operates only until the next entry. */
    private class EntryStream(private val archive: Long) : InputStream() {
        private var ended = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (ended) return -1
            if (len == 0) return 0
            val buffer = ByteBuffer.wrap(b, off, len)
            try {
                Archive.readData(archive, buffer)
            } catch (e: ArchiveException) {
                throw java.io.IOException(e.message ?: "The archive cannot be read.", e)
            }
            val count = buffer.position() - off
            if (count <= 0) {
                ended = true
                return -1
            }
            return count
        }
    }

    private companion object {
        const val BLOCK_SIZE = 64L * 1024
    }
}
