// Makes the brand files of Moekoma from the logo of Asabunko, logo-asabunko.png.
// The red of each pixel becomes the green of Moekoma. The cream lines do not change.
// Run it in this folder: swift render.swift
import AppKit

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let res = root.appendingPathComponent("../../app/src/main/res").standardized
let src = NSImage(contentsOfFile: root.appendingPathComponent("logo-asabunko.png").path)!
var rect = CGRect(origin: .zero, size: src.size)
let full = src.cgImage(forProposedRect: &rect, context: nil, hints: nil)!
// The solid pixels of the mark are at x 319...917 and y 164...801 from the top. The word is under it.
let mark = recolor(full.cropping(to: CGRect(x: 319, y: 164, width: 599, height: 638))!)

/// Turns the red #FF5866 into the green #3DD68C. A pixel keeps its part of red: a dark red glow
/// becomes a dark green glow, and a grey pixel stays grey.
func recolor(_ image: CGImage) -> CGImage {
    let w = image.width, h = image.height
    let ctx = context(w, h)
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
    let data = ctx.data!.bindMemory(to: UInt8.self, capacity: w * h * 4)
    for i in 0..<(w * h) {
        let p = i * 4
        let a = CGFloat(data[p + 3]) / 255
        guard a > 0 else { continue }
        let color = NSColor(srgbRed: CGFloat(data[p]) / 255 / a, green: CGFloat(data[p + 1]) / 255 / a,
                            blue: CGFloat(data[p + 2]) / 255 / a, alpha: 1)
        var hue: CGFloat = 0, sat: CGFloat = 0, bri: CGFloat = 0, alpha: CGFloat = 0
        color.getHue(&hue, saturation: &sat, brightness: &bri, alpha: &alpha)
        let degrees = hue * 360
        guard sat > 0.12, degrees > 300 || degrees < 40 else { continue }
        // #FF5866 is hue 355, saturation 0.65, brightness 1. #3DD68C is hue 151, saturation 0.72, brightness 0.84.
        let green = NSColor(hue: 151.0 / 360, saturation: min(sat * 1.09, 1), brightness: bri * 0.84, alpha: 1)
            .usingColorSpace(.sRGB)!
        data[p] = UInt8((green.redComponent * a * 255).rounded())
        data[p + 1] = UInt8((green.greenComponent * a * 255).rounded())
        data[p + 2] = UInt8((green.blueComponent * a * 255).rounded())
    }
    return ctx.makeImage()!
}

func context(_ w: Int, _ h: Int) -> CGContext {
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.interpolationQuality = .high
    return ctx
}

/// The mark in a square of `size` pixels. `fill` is the part of the height that the mark takes.
func square(_ size: Int, fill: CGFloat, background: CGColor?) -> CGImage {
    let ctx = context(size, size)
    if let background {
        ctx.setFillColor(background)
        ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))
    }
    let height = CGFloat(size) * fill
    let width = height * CGFloat(mark.width) / CGFloat(mark.height)
    ctx.draw(mark, in: CGRect(x: (CGFloat(size) - width) / 2, y: (CGFloat(size) - height) / 2, width: width, height: height))
    return ctx.makeImage()!
}

/// A white mark on a transparent background, for the themed icon of Android 13.
func monochrome(_ size: Int, fill: CGFloat) -> CGImage {
    let image = square(size, fill: fill, background: nil)
    let ctx = context(size, size)
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    let data = ctx.data!.bindMemory(to: UInt8.self, capacity: size * size * 4)
    for i in 0..<(size * size) {
        let p = i * 4
        // The bright parts of the mark are its shape. Its glow is not.
        let light = (Int(data[p]) + Int(data[p + 1]) + Int(data[p + 2])) / 3
        let a = UInt8(min(255, max(0, (light - 90) * 2)))
        data[p] = a; data[p + 1] = a; data[p + 2] = a; data[p + 3] = a
    }
    return ctx.makeImage()!
}

func write(_ image: CGImage, _ path: URL) {
    try! FileManager.default.createDirectory(at: path.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])!.write(to: path)
}

let black = CGColor(srgbRed: 0, green: 0, blue: 0, alpha: 1)
write(square(1024, fill: 1, background: black), root.appendingPathComponent("icon-1024.png"))
write(square(512, fill: 1, background: black), root.appendingPathComponent("icon-512.png"))
write(square(600, fill: 1, background: nil), root.appendingPathComponent("mark.png"))
write(square(600, fill: 1, background: nil), res.appendingPathComponent("drawable-nodpi/mark.png"))

// An adaptive icon is 108 dp. The launcher shows the middle 72 dp, and a mask can cut to 66 dp.
let densities = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]
for (name, scale) in densities {
    let folder = res.appendingPathComponent("mipmap-\(name)")
    let layer = Int(108 * scale)
    write(square(layer, fill: 0.6, background: nil), folder.appendingPathComponent("ic_launcher_foreground.png"))
    write(monochrome(layer, fill: 0.6), folder.appendingPathComponent("ic_launcher_monochrome.png"))
    // The icon for a launcher that does not take adaptive icons: the mark fills the width.
    write(square(Int(48 * scale), fill: 1, background: black), folder.appendingPathComponent("ic_launcher.png"))
}
