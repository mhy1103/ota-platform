// 把几张图拼成一张（带标题）：sheet <输出.png> <列数> <格子宽> <图1> <标题1> [<图2> <标题2> ...]
import AppKit

let a = CommandLine.arguments
let out = a[1], cols = Int(a[2])!, cell = CGFloat(Double(a[3])!)
var items: [(String, String)] = []
var i = 4
while i + 1 < a.count { items.append((a[i], a[i + 1])); i += 2 }
let rows = (items.count + cols - 1) / cols
let gap: CGFloat = 14, label: CGFloat = 40, pad: CGFloat = 20
let W = pad * 2 + CGFloat(cols) * cell + CGFloat(cols - 1) * gap
let H = pad * 2 + CGFloat(rows) * (cell + label) + CGFloat(rows - 1) * gap
let ctx = CGContext(data: nil, width: Int(W), height: Int(H), bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
ctx.setFillColor(CGColor(red: 0.04, green: 0.045, blue: 0.06, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: W, height: H))
NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
for (k, it) in items.enumerated() {
    let r = k / cols, c = k % cols
    let x = pad + CGFloat(c) * (cell + gap)
    let yTop = H - pad - CGFloat(r) * (cell + label + gap)
    if let img = NSImage(contentsOfFile: it.0), let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil) {
        let ar = CGFloat(cg.height) / CGFloat(cg.width)
        let h = cell * ar
        ctx.interpolationQuality = .high
        ctx.draw(cg, in: CGRect(x: x, y: yTop - label - h, width: cell, height: h))
    }
    let style = NSMutableParagraphStyle()
    style.alignment = .left
    let attrs: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 17, weight: .semibold),
                                                .foregroundColor: NSColor(white: 0.92, alpha: 1), .paragraphStyle: style]
    (it.1 as NSString).draw(in: CGRect(x: x + 2, y: yTop - label + 8, width: cell, height: label - 10), withAttributes: attrs)
}
let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print(out)
