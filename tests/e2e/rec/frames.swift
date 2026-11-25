// 从视频里取几帧存成 PNG：frames <视频> <目录> <秒> [秒 ...]
import AVFoundation
import AppKit

let args = CommandLine.arguments
let asset = AVURLAsset(url: URL(fileURLWithPath: args[1]))
let gen = AVAssetImageGenerator(asset: asset)
gen.requestedTimeToleranceBefore = .zero
gen.requestedTimeToleranceAfter = .zero
let dir = URL(fileURLWithPath: args[2])
try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
for a in args.dropFirst(3) {
    let t = CMTime(seconds: Double(a)!, preferredTimescale: 600)
    if let cg = try? gen.copyCGImage(at: t, actualTime: nil) {
        let rep = NSBitmapImageRep(cgImage: cg)
        let out = dir.appendingPathComponent("f-\(a).png")
        try? rep.representation(using: .png, properties: [:])?.write(to: out)
        print(out.path)
    }
}
