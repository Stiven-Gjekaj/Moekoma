package io.github.stivengjekaj.moekoma.core

import com.dylibso.chicory.wabt.Wat2Wasm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** A platform of the canvas module with the images of Java, so that the JVM tests can draw. The app draws with Android. */
class AwtPlatform : AidokuPlatform {
    class Bitmap(val image: BufferedImage) : AidokuBitmap {
        override val width get() = image.width
        override val height get() = image.height
        override fun png(): ByteArray = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    class Canvas(width: Int, height: Int) : AidokuCanvas {
        private val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        private val graphics = image.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR) }

        override fun setTransform(translateX: Float, translateY: Float, scaleX: Float, scaleY: Float, rotate: Float) {
            graphics.transform = AffineTransform().apply {
                translate(translateX.toDouble(), translateY.toDouble())
                scale(scaleX.toDouble(), scaleY.toDouble())
                rotate(rotate.toDouble())
            }
        }

        override fun draw(image: AidokuBitmap, source: AidokuRect?, target: AidokuRect): Boolean {
            val from = (image as Bitmap).image
            val part = source ?: AidokuRect(0f, 0f, from.width.toFloat(), from.height.toFloat())
            return graphics.drawImage(
                from, target.x.toInt(), target.y.toInt(), (target.x + target.width).toInt(), (target.y + target.height).toInt(),
                part.x.toInt(), part.y.toInt(), (part.x + part.width).toInt(), (part.y + part.height).toInt(), null,
            )
        }

        override fun fill(path: List<AidokuPathOp>, color: AidokuColor) {}
        override fun stroke(path: List<AidokuPathOp>, style: AidokuStroke) {}
        override fun text(text: String, size: Float, font: Any?, x: Float, y: Float, color: AidokuColor) {}
        override fun image(): AidokuBitmap = Bitmap(image)
    }

    override fun canvas(width: Int, height: Int): AidokuCanvas = Canvas(width, height)
    override fun decode(bytes: ByteArray): AidokuBitmap? = ImageIO.read(ByteArrayInputStream(bytes))?.let(::Bitmap)
    override fun font(name: String): Any = name
    override fun systemFont(weight: Int): Any = "system"
    override fun loadFont(bytes: ByteArray): Any? = null
}

class AidokuCanvasTest {
    /** A module in WAT that uses the canvas and the js imports as a source of aidoku-rs does. As in Asabunko. */
    private val drawingSource: ByteArray = Wat2Wasm.parse("""
        (module
          (import "canvas" "new_context" (func ${'$'}context (param f32 f32) (result i32)))
          (import "canvas" "copy_image" (func ${'$'}copy (param i32 i32 f32 f32 f32 f32 f32 f32 f32 f32) (result i32)))
          (import "canvas" "get_image" (func ${'$'}image (param i32) (result i32)))
          (import "canvas" "get_image_width" (func ${'$'}width (param i32) (result f32)))
          (import "js" "context_create" (func ${'$'}js (result i32)))
          (import "js" "context_eval" (func ${'$'}eval (param i32 i32 i32) (result i32)))
          (import "js" "context_eval_async" (func ${'$'}evalAsync (param i32 i32 i32) (result i32)))
          (import "js" "webview_create" (func ${'$'}web (result i32)))
          (memory (export "memory") 1)
          (data (i32.const 16) "[1, 2, 3].map((n) => n * 2).join('-')")
          (data (i32.const 64) "Promise.resolve(40).then((n) => n + 2)")
          (func (export "start"))
          (func (export "free_result") (param i32))
          ;; Puts the right half of the image on the left, and the left half on the right.
          (func (export "swap") (param ${'$'}img i32) (result i32)
            (local ${'$'}ctx i32)
            (local.set ${'$'}ctx (call ${'$'}context (f32.const 2) (f32.const 1)))
            (drop (call ${'$'}copy (local.get ${'$'}ctx) (local.get ${'$'}img) (f32.const 1) (f32.const 0) (f32.const 1) (f32.const 1) (f32.const 0) (f32.const 0) (f32.const 1) (f32.const 1)))
            (drop (call ${'$'}copy (local.get ${'$'}ctx) (local.get ${'$'}img) (f32.const 0) (f32.const 0) (f32.const 1) (f32.const 1) (f32.const 1) (f32.const 0) (f32.const 1) (f32.const 1)))
            (call ${'$'}image (local.get ${'$'}ctx)))
          (func (export "width") (param ${'$'}img i32) (result f32) (call ${'$'}width (local.get ${'$'}img)))
          (func (export "script") (result i32) (call ${'$'}eval (call ${'$'}js) (i32.const 16) (i32.const 37)))
          (func (export "promise") (result i32) (call ${'$'}evalAsync (call ${'$'}js) (i32.const 64) (i32.const 38)))
          (func (export "web") (result i32) (call ${'$'}web)))
    """.trimIndent())

    private fun host(platform: AidokuPlatform?) = AidokuHost("draw", drawingSource, AidokuDefaults(null, emptyMap()), fixedFetcher(emptyMap()), platform)

    /** The red, green, and blue values of each pixel, in rows from the top. */
    private fun pixels(image: BufferedImage) = (0 until image.height).flatMap { y ->
        (0 until image.width).map { x -> image.getRGB(x, y).let { listOf((it shr 16) and 255, (it shr 8) and 255, it and 255) } }
    }

    /** An image of two pixels: red on the left and blue on the right. */
    private fun redBlue() = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, 0xFFFF0000.toInt())
        setRGB(1, 0, 0xFF0000FF.toInt())
    }

    @Test fun copiesThePartsOfAnImageToTheirPlaces() = runBlocking {
        val platform = AwtPlatform()
        val host = host(platform)
        val drawn = host.run {
            val rid = add(AidokuHost.Picture(AwtPlatform.Bitmap(redBlue()).png(), null, platform))
            assertEquals(2f, AidokuHost.float(invoke("width", rid.toLong())[0]))
            (item(invoke("swap", rid.toLong())[0].toInt()) as AidokuHost.Picture).fileData!!
        }
        // The blue pixel is on the left now.
        assertEquals(listOf(listOf(0, 0, 255), listOf(255, 0, 0)), pixels(ImageIO.read(ByteArrayInputStream(drawn))))
        host.close()
    }

    @Test fun runsAScriptAndAPromiseInQuickJs() = runBlocking {
        val host = host(null)
        val texts = host.run { listOf("script", "promise").map { name -> String(item(invoke(name)[0].toInt()) as ByteArray) } }
        assertEquals(listOf("2-4-6", "42"), texts)
        host.close()
    }

    @Test fun givesAnErrorForAWebViewWithNoPlatform() = runBlocking {
        val host = host(null)
        assertEquals(-1, host.run { invoke("web")[0].toInt() })
        host.close()
    }

    @Test fun readsAPathOfTheCanvasModule() {
        val data = PostcardWriter.write {
            unsigned(3)
            unsigned(0); f32(1f); f32(2f)
            unsigned(1); f32(5f); f32(2f)
            unsigned(5)
        }
        assertEquals(listOf(AidokuPathOp.Move(1f, 2f), AidokuPathOp.Line(5f, 2f), AidokuPathOp.Close), aidokuPath(data))
        // An unknown step gives no path.
        assertNull(aidokuPath(byteArrayOf(0x01, 0x09)))
    }
}
