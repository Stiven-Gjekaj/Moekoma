package io.github.stivengjekaj.moekoma.core

import java.io.File
import java.nio.file.Files

fun temporaryFolder(): File = Files.createTempDirectory("moekoma").toFile().apply { deleteOnExit() }

/** Different bytes for each page, so that a test can tell the pages apart. */
fun page(mark: Int): ByteArray = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), mark.toByte())

fun comicInfo(fields: Map<String, String>): ByteArray {
    val body = fields.entries.joinToString("") { (key, value) -> "<$key>$value</$key>" }
    return "<?xml version=\"1.0\"?><ComicInfo>$body</ComicInfo>".toByteArray()
}
