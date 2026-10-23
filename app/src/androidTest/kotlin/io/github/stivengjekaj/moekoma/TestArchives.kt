package io.github.stivengjekaj.moekoma

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/** The kinds of archive that the tests write with libarchive. No tool on the device writes a RAR. */
enum class TestArchive { Zip, SevenZip, Tar }

/** Writes an archive with these files. A name can hold "..", as a damaged archive can. */
fun writeArchive(kind: TestArchive, file: File, files: List<Pair<String, ByteArray>>) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY).use { fd ->
        val archive = Archive.writeNew()
        try {
            when (kind) {
                TestArchive.Zip -> Archive.writeSetFormatZip(archive)
                TestArchive.SevenZip -> Archive.writeSetFormat7zip(archive)
                TestArchive.Tar -> Archive.writeSetFormatPaxRestricted(archive)
            }
            Archive.writeOpenFd(archive, fd.fd)
            for ((name, data) in files) {
                val entry = ArchiveEntry.new2(archive)
                ArchiveEntry.setPathnameUtf8(entry, name)
                ArchiveEntry.setFiletype(entry, ArchiveEntry.AE_IFREG)
                ArchiveEntry.setPerm(entry, "644".toInt(8))
                ArchiveEntry.setSize(entry, data.size.toLong())
                Archive.writeHeader(archive, entry)
                Archive.writeData(archive, ByteBuffer.wrap(data))
                ArchiveEntry.free(entry)
            }
            Archive.writeClose(archive)
        } finally {
            Archive.writeFree(archive)
        }
    }
}

/** A real PNG of one grey, so that the app can decode it and the tests can tell the pages apart. */
fun png(gray: Int, width: Int = 60, height: Int = 90): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.rgb(gray, gray, gray))
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
