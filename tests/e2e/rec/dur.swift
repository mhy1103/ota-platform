import AVFoundation
for p in CommandLine.arguments.dropFirst() {
    let a = AVURLAsset(url: URL(fileURLWithPath: p))
    let sem = DispatchSemaphore(value: 0)
    Task {
        let d = try? await a.load(.duration)
        let tracks = (try? await a.loadTracks(withMediaType: .video)) ?? []
        let size = tracks.first != nil ? (try? await tracks.first!.load(.naturalSize)) : nil
        let fps = tracks.first != nil ? (try? await tracks.first!.load(.nominalFrameRate)) : nil
        print(URL(fileURLWithPath: p).lastPathComponent, String(format: "%.1f 秒", d?.seconds ?? -1), size.map { "\(Int($0.width))x\(Int($0.height))" } ?? "?", fps.map { String(format: "%.0f fps", $0) } ?? "?")
        sem.signal()
    }
    sem.wait()
}
