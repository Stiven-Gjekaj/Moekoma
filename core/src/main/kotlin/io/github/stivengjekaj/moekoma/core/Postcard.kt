package io.github.stivengjekaj.moekoma.core

import java.io.ByteArrayOutputStream

/**
 * The postcard format of Rust, which the Aidoku sources use for their data. As in Asabunko.
 * An unsigned integer is a LEB128 varint, and a signed integer is a zigzag varint.
 * A float is little endian. A string, bytes, and a list start with a varint of their length.
 * An option starts with 0 or 1. An enum starts with the varint of its variant.
 */
class PostcardWriter {
    private val out = ByteArrayOutputStream()
    val data: ByteArray get() = out.toByteArray()

    fun varint(value: Long) {
        var rest = value
        do {
            var byte = (rest and 0x7F).toInt()
            rest = rest ushr 7
            if (rest != 0L) byte = byte or 0x80
            out.write(byte)
        } while (rest != 0L)
    }

    fun u8(value: Int) = out.write(value and 0xFF)
    fun bool(value: Boolean) = out.write(if (value) 1 else 0)
    fun unsigned(value: Int) = varint(maxOf(value, 0).toLong())
    fun signed(value: Long) = varint((value shl 1) xor (value shr 63))
    fun signed(value: Int) = signed(value.toLong())

    fun f32(value: Float) {
        val bits = java.lang.Float.floatToRawIntBits(value)
        for (shift in 0 until 32 step 8) out.write((bits ushr shift) and 0xFF)
    }

    fun f64(value: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        for (shift in 0 until 64 step 8) out.write(((bits ushr shift) and 0xFF).toInt())
    }

    fun bytes(value: ByteArray) {
        varint(value.size.toLong())
        out.write(value)
    }

    fun string(value: String) = bytes(value.toByteArray(Charsets.UTF_8))

    fun <T> option(value: T?, write: PostcardWriter.(T) -> Unit) {
        if (value == null) return u8(0)
        u8(1)
        write(value)
    }

    fun <T> list(values: List<T>, write: PostcardWriter.(T) -> Unit) {
        varint(values.size.toLong())
        for (value in values) write(value)
    }

    companion object {
        fun write(block: PostcardWriter.() -> Unit): ByteArray = PostcardWriter().apply(block).data
    }
}

class PostcardException(message: String) : Exception(message)

class PostcardReader(private val data: ByteArray) {
    private var index = 0

    val isAtEnd: Boolean get() = index >= data.size

    fun u8(): Int {
        if (index >= data.size) throw PostcardException("The data ends too early.")
        return data[index++].toInt() and 0xFF
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = u8()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw PostcardException("A number is too long.")
        }
    }

    fun bool(): Boolean = u8() != 0
    fun unsigned(): Int = varint().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun signed(): Long {
        val raw = varint()
        return (raw ushr 1) xor -(raw and 1)
    }

    fun f32(): Float {
        var bits = 0
        for (shift in 0 until 32 step 8) bits = bits or (u8() shl shift)
        return java.lang.Float.intBitsToFloat(bits)
    }

    fun f64(): Double {
        var bits = 0L
        for (shift in 0 until 64 step 8) bits = bits or (u8().toLong() shl shift)
        return java.lang.Double.longBitsToDouble(bits)
    }

    fun bytes(): ByteArray {
        val count = unsigned()
        if (data.size - index < count) throw PostcardException("The data ends too early.")
        return data.copyOfRange(index, index + count).also { index += count }
    }

    fun string(): String = String(bytes(), Charsets.UTF_8)

    fun <T> option(read: PostcardReader.() -> T): T? = when (val tag = u8()) {
        0 -> null
        1 -> read()
        else -> throw PostcardException("The option tag $tag is not 0 or 1.")
    }

    fun <T> list(read: PostcardReader.() -> T): List<T> {
        val count = unsigned()
        val values = ArrayList<T>(minOf(count, 4096))
        repeat(count) { values += read() }
        return values
    }
}
