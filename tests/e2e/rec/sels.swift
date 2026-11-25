import WebKit
import ObjectiveC
var count: UInt32 = 0
for cls in [WKPreferences.self as AnyClass] {
    if let list = class_copyMethodList(cls, &count) {
        for i in 0..<Int(count) {
            let n = NSStringFromSelector(method_getName(list[i]))
            let l = n.lowercased()
            if l.contains("accelerat") || l.contains("composit") || l.contains("animation") || l.contains("threaded") { print(n) }
        }
    }
}
