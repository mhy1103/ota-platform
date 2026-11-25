// 录网页：离屏 WKWebView 定时截图，AVAssetWriter 编成 H.264 MP4。
// 用法：record <配置.json>
// 配置：url、width、height（窗口大小，点）、fps、duration（秒）、warmup（加载后等几秒再开录）、
//      outputs：[{path, size:[w,h], crop:[x,y,w,h] 可选（点，相对窗口）, scale（截图倍数）}]
//      steps：页面每完成一次自动刷新（ota:live）就执行下一步：{cmd: "shell 命令"} 或 {js: "脚本"}，可带 delay（秒）
import AppKit
import AVFoundation
import WebKit

setvbuf(stdout, nil, _IONBF, 0)

struct Output: Decodable {
    let path: String
    let size: [Int]
    let crop: [Double]?
    let scale: Double?
    let selector: String?
    let pad: Double?
}

struct Step: Decodable {
    let cmd: String?
    let js: String?
    let delay: Double?
    let note: String?
}

struct Config: Decodable {
    let url: String
    let width: Double
    let height: Double
    let fps: Double
    let duration: Double
    let warmup: Double
    let outputs: [Output]
    let steps: [Step]
    let tailAfterSteps: Double?
    let inflight: Int?
    let afterUpdates: Bool?
    let noCompositing: Bool?
}

final class Sink {
    let writer: AVAssetWriter
    let input: AVAssetWriterInput
    let adaptor: AVAssetWriterInputPixelBufferAdaptor
    let size: CGSize
    var crop: CGRect?
    let scale: Double
    let queue = DispatchQueue(label: "sink")
    var frames = 0
    var last = CMTime.invalid

    init(_ o: Output) throws {
        let url = URL(fileURLWithPath: o.path)
        try? FileManager.default.removeItem(at: url)
        writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        size = CGSize(width: o.size[0], height: o.size[1])
        crop = o.crop.map { CGRect(x: $0[0], y: $0[1], width: $0[2], height: $0[3]) }
        scale = o.scale ?? 2
        let bits = Int(size.width * size.height * 9)
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: Int(size.width),
            AVVideoHeightKey: Int(size.height),
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: bits,
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                AVVideoMaxKeyFrameIntervalKey: 60,
            ],
        ]
        input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = true
        adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: Int(size.width),
            kCVPixelBufferHeightKey as String: Int(size.height),
        ])
        writer.add(input)
        writer.startWriting()
        writer.startSession(atSourceTime: .zero)
    }

    func append(_ img: CGImage, at t: CMTime) {
        guard input.isReadyForMoreMediaData, let pool = adaptor.pixelBufferPool else { return }
        if last.isValid && CMTimeCompare(t, last) <= 0 { return }
        var pb: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, pool, &pb)
        guard let buf = pb else { return }
        CVPixelBufferLockBaseAddress(buf, [])
        if let ctx = CGContext(data: CVPixelBufferGetBaseAddress(buf), width: Int(size.width), height: Int(size.height), bitsPerComponent: 8,
                               bytesPerRow: CVPixelBufferGetBytesPerRow(buf), space: CGColorSpaceCreateDeviceRGB(),
                               bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) {
            ctx.interpolationQuality = .high
            ctx.setFillColor(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
            ctx.fill(CGRect(origin: .zero, size: size))
            ctx.draw(img, in: CGRect(origin: .zero, size: size))
        }
        CVPixelBufferUnlockBaseAddress(buf, [])
        if adaptor.append(buf, withPresentationTime: t) {
            frames += 1
            last = t
        }
    }

    func finish(_ done: @escaping () -> Void) {
        input.markAsFinished()
        writer.finishWriting(completionHandler: done)
    }
}

final class Recorder: NSObject, NSApplicationDelegate, WKNavigationDelegate, WKScriptMessageHandler {
    let cfg: Config
    var window: NSWindow!
    var web: WKWebView!
    var sinks: [Sink] = []
    var start: CFTimeInterval = 0
    var recording = false
    var stepIndex = 0
    var stepsDone: CFTimeInterval = 0
    var busy: [Int] = []
    var timer: Timer?
    var lives = 0
    var rects: [[Double]] = []

    init(_ c: Config) { cfg = c }

    func applicationDidFinishLaunching(_ n: Notification) {
        let conf = WKWebViewConfiguration()
        conf.websiteDataStore = .nonPersistent()
        if cfg.noCompositing ?? false {
            let sel = NSSelectorFromString("_setAcceleratedCompositingEnabled:")
            if conf.preferences.responds(to: sel) {
                typealias G = @convention(c) (AnyObject, Selector, Bool) -> Void
                unsafeBitCast(conf.preferences.method(for: sel), to: G.self)(conf.preferences, sel, false)
                print("关掉了合成层")
            }
        }
        conf.userContentController.add(self, name: "live")
        let hook = "window.addEventListener('ota:live', function (e) { if (e.detail && e.detail.ok) window.webkit.messageHandlers.live.postMessage(String(Date.now())); });"
        conf.userContentController.addUserScript(WKUserScript(source: hook, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        web = WKWebView(frame: NSRect(x: 0, y: 0, width: cfg.width, height: cfg.height), configuration: conf)
        let sel = NSSelectorFromString("_setWindowOcclusionDetectionEnabled:")
        if web.responds(to: sel) {
            typealias F = @convention(c) (AnyObject, Selector, Bool) -> Void
            unsafeBitCast(web.method(for: sel), to: F.self)(web, sel, false)
            print("关掉了遮挡检测")
        }
        window = NSWindow(contentRect: NSRect(x: -20000, y: -20000, width: cfg.width, height: cfg.height), styleMask: [.borderless], backing: .buffered, defer: false)
        window.contentView = web
        window.orderFrontRegardless()
        web.navigationDelegate = self
        web.load(URLRequest(url: URL(string: cfg.url)!))
        _ = ProcessInfo.processInfo.beginActivity(options: [.userInitiated, .latencyCritical, .idleSystemSleepDisabled, .idleDisplaySleepDisabled], reason: "record")
    }

    func webView(_ w: WKWebView, didFinish nav: WKNavigation!) {
        if recording || !sinks.isEmpty { return }
        print("加载完成，\(cfg.warmup) 秒后开录")
        DispatchQueue.main.asyncAfter(deadline: .now() + cfg.warmup) { self.begin() }
    }

    func begin() {
        let sels = cfg.outputs.compactMap { $0.selector }
        if !sels.isEmpty && rects.isEmpty {
            let js = "JSON.stringify(" + String(data: try! JSONEncoder().encode(sels), encoding: .utf8)! + ".map(function (s) { var r = document.querySelector(s).getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; }))"
            web.evaluateJavaScript(js) { r, err in
                if let text = r as? String, let d = text.data(using: .utf8), let list = try? JSONDecoder().decode([[Double]].self, from: d) {
                    self.rects = list
                    print("裁剪区域：\(list)")
                } else {
                    print("找不到裁剪区域：\(String(describing: err))")
                    self.rects = sels.map { _ in [0, 0, self.cfg.width, self.cfg.height] }
                }
                self.begin()
            }
            return
        }
        do {
            var k = 0
            sinks = try cfg.outputs.map { o in
                let sink = try Sink(o)
                if o.selector != nil {
                    let r = rects[k], pad = o.pad ?? 0
                    k += 1
                    sink.crop = CGRect(x: r[0] - pad, y: r[1] - pad, width: r[2] + pad * 2, height: r[3] + pad * 2)
                }
                return sink
            }
        } catch {
            print("建不了输出：\(error)")
            exit(1)
        }
        busy = sinks.map { _ in 0 }
        start = CACurrentMediaTime()
        recording = true
        web.evaluateJavaScript("document.visibilityState + ' ' + document.getAnimations().length") { r, _ in print("页面：\(r ?? "?")") }
        timer = Timer.scheduledTimer(withTimeInterval: 1.0 / cfg.fps, repeats: true) { _ in self.tick() }
        RunLoop.main.add(timer!, forMode: .common)
    }

    func tick() {
        let t = CACurrentMediaTime() - start
        let limit = stepIndex >= cfg.steps.count && stepsDone > 0 ? min(cfg.duration, stepsDone - start + (cfg.tailAfterSteps ?? 8)) : cfg.duration
        if t >= limit {
            stop()
            return
        }
        for (i, s) in sinks.enumerated() where busy[i] < (cfg.inflight ?? 3) {
            busy[i] += 1
            let shot = WKSnapshotConfiguration()
            let rect = s.crop ?? CGRect(x: 0, y: 0, width: cfg.width, height: cfg.height)
            shot.rect = rect
            shot.snapshotWidth = NSNumber(value: Double(rect.width) * s.scale / (window.backingScaleFactor))
            shot.afterScreenUpdates = cfg.afterUpdates ?? false
            let at = CMTime(seconds: t, preferredTimescale: 600)
            web.takeSnapshot(with: shot) { img, err in
                self.busy[i] -= 1
                guard let img = img else { return }
                s.queue.async {
                    if let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil) { s.append(cg, at: at) }
                }
            }
        }
    }

    func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage) {
        lives += 1
        let t = recording ? CACurrentMediaTime() - start : -1
        print(String(format: "刷新 #%d（%.1fs）", lives, t))
        guard recording, stepIndex < cfg.steps.count else { return }
        let step = cfg.steps[stepIndex]
        stepIndex += 1
        DispatchQueue.main.asyncAfter(deadline: .now() + (step.delay ?? 0.4)) { self.run(step) }
    }

    func run(_ step: Step) {
        let t = CACurrentMediaTime() - start
        print(String(format: "  第 %d 步（%.1fs）：%@", stepIndex, t, step.note ?? step.cmd ?? step.js ?? ""))
        if let js = step.js { web.evaluateJavaScript(js, completionHandler: nil) }
        if let cmd = step.cmd {
            let p = Process()
            p.executableURL = URL(fileURLWithPath: "/bin/zsh")
            p.arguments = ["-c", cmd]
            p.terminationHandler = { _ in DispatchQueue.main.async { self.ranStep() } }
            try? p.run()
        } else {
            ranStep()
        }
    }

    func ranStep() {
        if stepIndex >= cfg.steps.count { stepsDone = CACurrentMediaTime() + 10.5 }
    }

    func stop() {
        timer?.invalidate()
        recording = false
        let g = DispatchGroup()
        for s in sinks {
            g.enter()
            s.queue.async {
                print("\(s.writer.outputURL.lastPathComponent)：\(s.frames) 帧")
                s.finish { g.leave() }
            }
        }
        g.notify(queue: .main) {
            print("写完了")
            exit(0)
        }
    }
}

let path = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "config.json"
let cfg = try! JSONDecoder().decode(Config.self, from: Data(contentsOf: URL(fileURLWithPath: path)))
let app = NSApplication.shared
app.setActivationPolicy(.prohibited)
let rec = Recorder(cfg)
app.delegate = rec
app.run()
