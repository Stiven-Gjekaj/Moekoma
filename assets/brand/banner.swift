// Makes logo-full.png, the banner of Moekoma, from the logo of Asabunko, logo-asabunko.png.
// The mark keeps its place. The word "asabunko" becomes "moekoma", made from the letters of
// the old word, so that the style stays: m from two arches of n, e from o, and the o with the
// star of the old word as the second o. Then the red becomes the green of Moekoma.
// Run it in this folder: swift banner.swift
import AppKit

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let src = NSImage(contentsOfFile: root.appendingPathComponent("logo-asabunko.png").path)!
var rect = CGRect(origin: .zero, size: src.size)
let image = src.cgImage(forProposedRect: &rect, context: nil, hints: nil)!
let w = image.width, h = image.height

/// The pixels of an image as RGBA, with the first row at the top.
struct Pixels {
    var w: Int, h: Int, data: [UInt8]
    init(_ w: Int, _ h: Int) { self.w = w; self.h = h; data = [UInt8](repeating: 0, count: w * h * 4) }
    init(_ image: CGImage) {
        self.init(image.width, image.height)
        let ctx = CGContext(data: &data, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
    }
    func crop(_ x0: Int, _ x1: Int, _ y0: Int, _ y1: Int) -> Pixels {
        var out = Pixels(x1 - x0 + 1, y1 - y0 + 1)
        for y in 0..<out.h { for x in 0..<out.w { for c in 0..<4 { out.data[(y * out.w + x) * 4 + c] = data[((y0 + y) * w + x0 + x) * 4 + c] } } }
        return out
    }
    /// True when the pixel is cream at the given alpha. The colours are premultiplied, so a soft pixel is cream at its alpha.
    func isLight(_ x: Int, _ y: Int, alpha minimum: Int) -> Bool {
        guard x >= 0, y >= 0, x < w, y < h else { return false }
        let p = (y * w + x) * 4
        let alpha = Int(data[p + 3])
        guard alpha > minimum else { return false }
        return Int(data[p]) * 255 / alpha > 200 && Int(data[p + 1]) * 255 / alpha > 190
    }
    /// A pixel of a cream line, with the one soft pixel at its edge. The glow around a line is light too,
    /// but it is not next to a solid pixel.
    func isCream(_ x: Int, _ y: Int) -> Bool {
        if isLight(x, y, alpha: 235) { return true }
        guard isLight(x, y, alpha: 40) else { return false }
        return isLight(x - 1, y, alpha: 235) || isLight(x + 1, y, alpha: 235) || isLight(x, y - 1, alpha: 235) || isLight(x, y + 1, alpha: 235)
    }
    mutating func clear(_ x: Int, _ y: Int) { for c in 0..<4 { data[(y * w + x) * 4 + c] = 0 } }
    /// Keeps only the cream lines of a letter, with no glow, so that the new glow is the same for each letter.
    func letter() -> Pixels {
        var out = self
        for y in 0..<h { for x in 0..<w where !isCream(x, y) { out.clear(x, y) } }
        return out
    }
    func cgImage() -> CGImage {
        var copy = data
        let ctx = CGContext(data: &copy, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        return ctx.makeImage()!
    }
}

let logo = Pixels(image)
// The letters of the old word. Each row is from 873, the top of b and k, to 1028.
let top = 873, bottom = 1028
let a = logo.crop(167, 279, top, bottom).letter()
let n = logo.crop(740, 848, top, bottom).letter()
let k = logo.crop(862, 967, top, bottom).letter()
// The o with the star keeps its cream ring and the red pixels of its star, with no glow around them.
let starO: Pixels = {
    var out = logo.crop(968, 1085, top, bottom)
    for y in 0..<out.h { for x in 0..<out.w {
        let p = (y * out.w + x) * 4
        let red = out.data[p + 3] > 120 && Int(out.data[p]) > Int(out.data[p + 1]) + 60
        let r = hypot(Double(x) - (Double(out.w) / 2 - 1), Double(y - (971 - top)))
        // In the old word the k touches the o. A pixel out of the ring is a part of the k.
        if (!out.isCream(x, y) && !red) || r > 58 { out.clear(x, y) }
    } }
    return out
}()

/// The o with no star: the ring only.
var o = logo.crop(968, 1085, top, bottom).letter()
let center = (x: Double(o.w) / 2 - 1, y: Double(971 - top))
for y in 0..<o.h { for x in 0..<o.w {
    let r = hypot(Double(x) - center.x, Double(y) - center.y)
    if r < 33 || r > 58 { o.clear(x, y) }
} }

/// The e: the ring of the o, a bar across its middle, and an opening at the lower right.
var e = o
let stroke = 26
for y in 0..<e.h { for x in 0..<e.w {
    let dx = Double(x) - center.x, dy = Double(y) - center.y
    let r = hypot(dx, dy)
    let angle = atan2(dy, dx) * 180 / .pi
    if r > 20 && angle > 6 && angle < 52 { e.clear(x, y) }
    if abs(dy + 2) <= Double(stroke) / 2 && r < 50 {
        let p = (y * e.w + x) * 4
        e.data[p] = 0xFB; e.data[p + 1] = 0xF4; e.data[p + 2] = 0xED; e.data[p + 3] = 255
    }
} }

/// The m: the n, then the arch and the right stem of the n again, from the right stem of the first.
var m = Pixels(n.w + (848 - 766 + 1) - 4, n.h)
for y in 0..<n.h {
    for x in 0..<n.w { for c in 0..<4 { m.data[(y * m.w + x) * 4 + c] = n.data[(y * n.w + x) * 4 + c] } }
    for x in (766 - 740)..<n.w {
        let target = n.w - 4 + x - (766 - 740)
        let p = (y * n.w + x) * 4
        // Where the two arches meet, the stronger pixel stays, so that no seam shows.
        if n.data[p + 3] > m.data[(y * m.w + target) * 4 + 3] { for c in 0..<4 { m.data[(y * m.w + target) * 4 + c] = n.data[p + c] } }
    }
}

// The letters of the new word, with the gaps of the old word.
let word: [Pixels] = [m, o, e, k, starO, m, a]
let gap = 10
let width = word.map(\.w).reduce(0, +) + gap * (word.count - 1)

// The new banner: the logo with the old word gone. The glow of the mark fades out over the old word.
let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
var faded = logo
for y in 800..<h {
    let keep = max(0, 1 - Double(y - 800) / 70)
    for x in 0..<w { for c in 0..<4 { let p = (y * w + x) * 4 + c; faded.data[p] = UInt8(Double(faded.data[p]) * keep) } }
}
ctx.draw(faded.cgImage(), in: CGRect(x: 0, y: 0, width: w, height: h))
// The word goes into one image first, so that one glow goes around the full word.
var line = Pixels(width, word[0].h)
var x = 0
for letter in word {
    for y in 0..<letter.h { for lx in 0..<letter.w {
        let p = (y * letter.w + lx) * 4
        guard letter.data[p + 3] > 0 else { continue }
        for c in 0..<4 { line.data[(y * width + x + lx) * 4 + c] = letter.data[p + c] }
    } }
    x += letter.w + gap
}
// The word gets a soft cream glow, as the old word has. CoreGraphics counts y from the bottom.
ctx.setShadow(offset: .zero, blur: 22, color: CGColor(srgbRed: 0.98, green: 0.96, blue: 0.93, alpha: 0.4))
ctx.draw(line.cgImage(), in: CGRect(x: (w - width) / 2, y: h - 1 - bottom, width: width, height: line.h))
let banner = ctx.makeImage()!

// The red of the star and of the sun becomes green, as in render.swift.
func recolor(_ image: CGImage) -> CGImage {
    var px = Pixels(image)
    for i in 0..<(px.w * px.h) {
        let p = i * 4
        let alpha = CGFloat(px.data[p + 3]) / 255
        guard alpha > 0 else { continue }
        let color = NSColor(srgbRed: CGFloat(px.data[p]) / 255 / alpha, green: CGFloat(px.data[p + 1]) / 255 / alpha,
                            blue: CGFloat(px.data[p + 2]) / 255 / alpha, alpha: 1)
        var hue: CGFloat = 0, sat: CGFloat = 0, bri: CGFloat = 0, a: CGFloat = 0
        color.getHue(&hue, saturation: &sat, brightness: &bri, alpha: &a)
        let degrees = hue * 360
        guard sat > 0.12, degrees > 300 || degrees < 40 else { continue }
        let green = NSColor(hue: 151.0 / 360, saturation: min(sat * 1.09, 1), brightness: bri * 0.84, alpha: 1).usingColorSpace(.sRGB)!
        px.data[p] = UInt8((green.redComponent * alpha * 255).rounded())
        px.data[p + 1] = UInt8((green.greenComponent * alpha * 255).rounded())
        px.data[p + 2] = UInt8((green.blueComponent * alpha * 255).rounded())
    }
    return px.cgImage()
}

try! NSBitmapImageRep(cgImage: recolor(banner)).representation(using: .png, properties: [:])!
    .write(to: root.appendingPathComponent("logo-full.png"))
