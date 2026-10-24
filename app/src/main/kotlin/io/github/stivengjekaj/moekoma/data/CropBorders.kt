package io.github.stivengjekaj.moekoma.data

import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import coil3.transform.Transformation
import io.github.stivengjekaj.moekoma.core.BorderCrop

/** Shows a scanned page with no plain white or black frame. A page with no frame stays as it is. */
class CropBorders : Transformation() {
    override val cacheKey: String = "crop-borders"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // A small copy is enough to find the borders, and it is fast.
        val scale = minOf(1.0, 256.0 / maxOf(input.width, input.height))
        val width = maxOf((input.width * scale).toInt(), 1)
        val height = maxOf((input.height * scale).toInt(), 1)
        val small = Bitmap.createScaledBitmap(input.copyIfHardware(), width, height, false)
        val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
        val box = BorderCrop.content(width, height, input.width, input.height) { x, y ->
            val color = pixels[y * width + x]
            (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        }
        if (box.left == 0 && box.top == 0 && box.right == input.width && box.bottom == input.height) return input
        return Bitmap.createBitmap(input.copyIfHardware(), box.left, box.top, box.width, box.height)
    }

    /** A hardware bitmap cannot give its pixels, so the crop reads a copy. */
    private fun Bitmap.copyIfHardware(): Bitmap =
        if (android.os.Build.VERSION.SDK_INT >= 26 && config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    override fun equals(other: Any?) = other is CropBorders
    override fun hashCode() = cacheKey.hashCode()
}
