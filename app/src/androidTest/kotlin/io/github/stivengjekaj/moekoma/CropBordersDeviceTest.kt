package io.github.stivengjekaj.moekoma

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.size.Size
import io.github.stivengjekaj.moekoma.data.CropBorders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The crop of the plain borders of a page, on a bitmap of Android. */
@RunWith(AndroidJUnit4::class)
class CropBordersDeviceTest {
    private fun page(block: Boolean): Bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        if (block) canvas.drawRect(100f, 150f, 700f, 1050f, android.graphics.Paint().apply { color = Color.DKGRAY })
    }

    @Test fun cropsTheWhiteBordersOfAPage() = runBlocking {
        val cropped = CropBorders().transform(page(block = true), Size.ORIGINAL)
        assertTrue("width ${cropped.width}", abs(cropped.width - 600) < 20)
        assertTrue("height ${cropped.height}", abs(cropped.height - 900) < 20)
    }

    @Test fun keepsAPageWithNoBorder() = runBlocking {
        val plain = page(block = false)
        assertSame(plain, CropBorders().transform(plain, Size.ORIGINAL))
    }
}
