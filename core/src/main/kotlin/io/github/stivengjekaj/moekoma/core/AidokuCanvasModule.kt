package io.github.stivengjekaj.moekoma.core

import com.dylibso.chicory.wasm.types.ValType
import kotlin.math.floor
import kotlin.math.roundToInt

/** A path of the canvas module: `Path { ops: Vec<PathOp> }` in postcard. Null when the data is not a path. */
internal fun aidokuPath(data: ByteArray): List<AidokuPathOp>? = runCatching {
    val r = PostcardReader(data)
    List(r.unsigned()) {
        when (r.varint()) {
            0L -> AidokuPathOp.Move(r.f32(), r.f32())
            1L -> AidokuPathOp.Line(r.f32(), r.f32())
            2L -> AidokuPathOp.Quad(r.f32(), r.f32(), r.f32(), r.f32())
            3L -> AidokuPathOp.Cubic(r.f32(), r.f32(), r.f32(), r.f32(), r.f32(), r.f32())
            4L -> AidokuPathOp.Arc(r.f32(), r.f32(), r.f32(), r.f32(), r.f32())
            5L -> AidokuPathOp.Close
            else -> throw PostcardException("The path operation is not known.")
        }
    }
}.getOrNull()

/** A font of the platform, in the store of the host. */
private class Font(val value: Any)

/** The canvas module of aidoku-rs. The platform draws. As in Asabunko, which draws with CoreGraphics. */
internal fun AidokuHost.installCanvas(define: Define) {
    val i32 = ValType.I32
    val f32 = ValType.F32
    fun result(value: Int) = longArrayOf(value.toLong())
    fun float(value: Long) = AidokuHost.float(value)
    fun canvas(rid: Long) = item(rid.toInt()) as? AidokuCanvas
    fun picture(rid: Long) = item(rid.toInt()) as? AidokuHost.Picture
    fun color(a: LongArray, from: Int) = AidokuColor(float(a[from]), float(a[from + 1]), float(a[from + 2]), float(a[from + 3]))

    define("canvas", "new_context", listOf(f32, f32), listOf(i32)) { a ->
        val width = float(a[0]).roundToInt()
        val height = float(a[1]).roundToInt()
        if (width !in 1..16_384 || height !in 1..16_384) return@define result(-6)
        result(platform?.canvas(width, height)?.let(::add) ?: -6)
    }
    define("canvas", "set_transform", listOf(i32, f32, f32, f32, f32, f32), listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        canvas.setTransform(float(a[1]), float(a[2]), float(a[3]), float(a[4]), float(a[5]))
        result(0)
    }
    define("canvas", "copy_image", listOf(i32, i32) + List(8) { f32 }, listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        val image = picture(a[1])?.image ?: return@define result(-2)
        // The part of the image has whole pixels, as CGRect.integral gives.
        val x = floor(float(a[2]))
        val y = floor(float(a[3]))
        val source = AidokuRect(x, y, kotlin.math.ceil(float(a[2]) + float(a[4])) - x, kotlin.math.ceil(float(a[3]) + float(a[5])) - y)
        if (source.width <= 0 || source.height <= 0 || source.x >= image.width || source.y >= image.height) return@define result(-4)
        val done = canvas.draw(image, source, AidokuRect(float(a[6]), float(a[7]), float(a[8]), float(a[9])))
        result(if (done) 0 else -4)
    }
    define("canvas", "draw_image", listOf(i32, i32, f32, f32, f32, f32), listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        val image = picture(a[1])?.image ?: return@define result(-2)
        canvas.draw(image, null, AidokuRect(float(a[2]), float(a[3]), float(a[4]), float(a[5])))
        result(0)
    }
    define("canvas", "fill", listOf(i32, i32, f32, f32, f32, f32), listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        val path = aidokuPath(encodedBuffer(a[1].toInt())) ?: return@define result(-7)
        canvas.fill(path, color(a, 2))
        result(0)
    }
    define("canvas", "stroke", listOf(i32, i32, i32), listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        val path = aidokuPath(encodedBuffer(a[1].toInt())) ?: return@define result(-7)
        val style = runCatching {
            val r = PostcardReader(encodedBuffer(a[2].toInt()))
            val color = AidokuColor(r.f32(), r.f32(), r.f32(), r.f32())
            AidokuStroke(color, r.f32(), r.varint().toInt(), r.varint().toInt(), r.f32(), r.list { f32() }, r.f32())
        }.getOrNull() ?: return@define result(-8)
        canvas.stroke(path, style)
        result(0)
    }
    define("canvas", "draw_text", listOf(i32, i32, i32, f32, f32, f32, i32, f32, f32, f32, f32), listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        val font = (item(a[6].toInt()) as? Font)?.value
        canvas.text(string(a[1].toInt(), a[2].toInt()), float(a[3]), font, float(a[4]), float(a[5]), color(a, 7))
        result(0)
    }
    define("canvas", "get_image", listOf(i32), listOf(i32)) { a ->
        val canvas = canvas(a[0]) ?: return@define result(-1)
        val image = canvas.image() ?: return@define result(-5)
        result(add(AidokuHost.Picture(null, image, platform)))
    }
    define("canvas", "new_font", listOf(i32, i32), listOf(i32)) { a ->
        result(platform?.font(string(a[0].toInt(), a[1].toInt()))?.let { add(Font(it)) } ?: -11)
    }
    define("canvas", "system_font", listOf(i32), listOf(i32)) { a ->
        result(platform?.systemFont(a[0].toInt().coerceIn(0, 8))?.let { add(Font(it)) } ?: -11)
    }
    define("canvas", "load_font", listOf(i32, i32), listOf(i32)) { a ->
        val request = AidokuHost.Request().apply { url = string(a[0].toInt(), a[1].toInt()) }
        if (send(request) != 0) return@define result(-11)
        val body = request.response?.body ?: return@define result(-11)
        result(platform?.loadFont(body)?.let { add(Font(it)) } ?: -11)
    }
    define("canvas", "new_image", listOf(i32, i32), listOf(i32)) { a ->
        val data = read(a[0].toInt(), a[1].toInt())
        if (data.isEmpty()) result(-3) else result(add(AidokuHost.Picture(data, null, platform)))
    }
    define("canvas", "get_image_data", listOf(i32), listOf(i32)) { a ->
        val picture = picture(a[0]) ?: return@define result(-2)
        result(picture.fileData?.let(::add) ?: -3)
    }
    define("canvas", "get_image_width", listOf(i32), listOf(f32)) { a -> longArrayOf(AidokuHost.float((picture(a[0])?.image?.width ?: 0).toFloat())) }
    define("canvas", "get_image_height", listOf(i32), listOf(f32)) { a -> longArrayOf(AidokuHost.float((picture(a[0])?.image?.height ?: 0).toFloat())) }
}
