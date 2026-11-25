// 和 expo-updates iOS 一样用 URLRequest.setValue 设请求头，看带换行的 Expo-Fatal-Error 能不能发出去
import Foundation

let ios = "Time: 1795159595052.000000\nDomain: RCTErrorDomain\nCode: 0\nDescription: Unhandled JS Exception: TypeError: undefined is not a function\n\n"
let cases: [(String, String)] = [
    ("/newline", ios),
    ("/flat", ios.replacingOccurrences(of: "\n", with: " | ")),
]

let done = DispatchSemaphore(value: 0)
for (path, value) in cases {
    var req = URLRequest(url: URL(string: "http://127.0.0.1:18880" + path)!)
    req.setValue(value, forHTTPHeaderField: "Expo-Fatal-Error")
    req.setValue("\"6f185dec-0000-0000-0000-000000000000\"", forHTTPHeaderField: "Expo-Recent-Failed-Update-IDs")
    req.setValue("ok", forHTTPHeaderField: "X-Control")
    URLSession.shared.dataTask(with: req) { data, res, err in
        if let err = err {
            print(path, "请求失败：", err.localizedDescription)
        } else {
            print(path, "HTTP", (res as? HTTPURLResponse)?.statusCode ?? 0, "服务器收到：", String(data: data ?? Data(), encoding: .utf8) ?? "")
        }
        done.signal()
    }.resume()
    done.wait()
}
