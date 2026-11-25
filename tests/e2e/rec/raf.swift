import AppKit
import WebKit
setvbuf(stdout, nil, _IONBF, 0)
final class D: NSObject, NSApplicationDelegate, WKNavigationDelegate {
    var w: NSWindow!; var web: WKWebView!
    let onScreen = CommandLine.arguments.count > 1
    func applicationDidFinishLaunching(_ n: Notification) {
        web = WKWebView(frame: NSRect(x: 0, y: 0, width: 400, height: 300))
        let sel = NSSelectorFromString("_setWindowOcclusionDetectionEnabled:")
        typealias F = @convention(c) (AnyObject, Selector, Bool) -> Void
        unsafeBitCast(web.method(for: sel), to: F.self)(web, sel, false)
        let origin = onScreen ? NSPoint(x: 0, y: 0) : NSPoint(x: -20000, y: -20000)
        w = NSWindow(contentRect: NSRect(origin: origin, size: NSSize(width: 400, height: 300)), styleMask: [.borderless], backing: .buffered, defer: false)
        if onScreen { w.alphaValue = 0.01; w.level = .normal }
        w.contentView = web; w.orderFrontRegardless()
        web.navigationDelegate = self
        web.loadHTMLString("<html><body><div id=b style='width:50px;height:50px;background:red;animation:s 2s linear infinite'></div><style>@keyframes s{to{transform:translateX(300px)}}</style><script>window.n=0;(function f(){window.n++;requestAnimationFrame(f)})();</script></body></html>", baseURL: nil)
    }
    func webView(_ v: WKWebView, didFinish nav: WKNavigation!) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            self.web.evaluateJavaScript("[window.n, document.getAnimations()[0].currentTime, document.visibilityState]") { a, _ in
                print("t0", a ?? "")
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    self.web.evaluateJavaScript("[window.n, document.getAnimations()[0].currentTime]") { b, _ in print("t3", b ?? ""); exit(0) }
                }
            }
        }
    }
}
let app = NSApplication.shared; app.setActivationPolicy(.prohibited)
let d = D(); app.delegate = d; app.run()
