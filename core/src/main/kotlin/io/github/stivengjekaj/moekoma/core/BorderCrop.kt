package io.github.stivengjekaj.moekoma.core

import kotlin.math.abs

/** Finds the plain borders of a scanned page, so that the reader can show the page with no white or black frame. */
object BorderCrop {
    /** A rectangle of an image in pixels, with the right and the bottom edges out of it. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * The part of an image with its content. [gray] gives the gray value, from 0 to 255, of a pixel of a small copy of
     * the image with [width] and [height] pixels, which is fast to read. The result is in pixels of the full image of
     * [fullWidth] and [fullHeight]. A border is a band at an edge where each pixel is near the color of the top left
     * corner. A page with no border, or a page that is all one color, gives the full image.
     */
    fun content(width: Int, height: Int, fullWidth: Int, fullHeight: Int, tolerance: Int = 24, gray: (Int, Int) -> Int): Box {
        val full = Box(0, 0, fullWidth, fullHeight)
        if (width <= 0 || height <= 0) return full
        val border = gray(0, 0)
        fun plainRow(y: Int) = (0 until width).all { abs(gray(it, y) - border) <= tolerance }
        fun plainColumn(x: Int, top: Int, bottom: Int) = (top until bottom).all { abs(gray(x, it) - border) <= tolerance }
        var top = 0
        var bottom = height
        while (top < bottom && plainRow(top)) top++
        while (bottom > top && plainRow(bottom - 1)) bottom--
        if (bottom <= top) return full
        var left = 0
        var right = width
        while (left < right && plainColumn(left, top, bottom)) left++
        while (right > left && plainColumn(right - 1, top, bottom)) right--
        // A small margin stays, so that a line at the edge of the content does not touch the screen.
        left = maxOf(left - 1, 0); top = maxOf(top - 1, 0); right = minOf(right + 1, width); bottom = minOf(bottom + 1, height)
        val sx = fullWidth.toDouble() / width
        val sy = fullHeight.toDouble() / height
        val box = Box(
            (left * sx).toInt().coerceIn(0, fullWidth), (top * sy).toInt().coerceIn(0, fullHeight),
            Math.ceil(right * sx).toInt().coerceIn(0, fullWidth), Math.ceil(bottom * sy).toInt().coerceIn(0, fullHeight),
        )
        // A crop that keeps almost all of the page is no crop.
        return if (box.width.toLong() * box.height > fullWidth.toLong() * fullHeight * 0.97) full else box
    }
}
