//
//  QuickConnectServer.swift
//  Simple MP3
//
//  Lightweight LAN HTTP portal for uploading audio from another device.
//

import Foundation
import Network
import Observation

@Observable
@MainActor
final class QuickConnectServer {
    private let repository: MusicRepository
    private var listener: NWListener?
    private var connections: [NWConnection] = []

    private(set) var isRunning = false
    private(set) var port: UInt16 = 8765
    private(set) var statusMessage: String?
    private(set) var lastUploadName: String?
    private(set) var localAddresses: [String] = []

    init(repository: MusicRepository) {
        self.repository = repository
    }

    var portalURLs: [String] {
        localAddresses.map { "http://\($0):\(port)" }
    }

    func refreshAddresses() {
        var result: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return }
        defer { freeifaddrs(ifaddr) }
        for ptr in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let iface = ptr.pointee
            guard iface.ifa_addr.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: iface.ifa_name)
            guard name.hasPrefix("en") || name.hasPrefix("bridge") else { continue }
            var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            getnameinfo(
                iface.ifa_addr,
                socklen_t(iface.ifa_addr.pointee.sa_len),
                &hostname,
                socklen_t(hostname.count),
                nil,
                0,
                NI_NUMERICHOST
            )
            let ip = String(cString: hostname)
            if ip.hasPrefix("127.") { continue }
            result.append(ip)
        }
        localAddresses = result
    }

    func start(port: UInt16 = 8765) {
        stop()
        self.port = port
        refreshAddresses()
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            let listener = try NWListener(using: params, on: NWEndpoint.Port(rawValue: port)!)
            listener.stateUpdateHandler = { [weak self] state in
                Task { @MainActor in
                    guard let self else { return }
                    switch state {
                    case .ready:
                        self.isRunning = true
                        self.statusMessage = "Portal ready"
                    case .failed(let err):
                        self.isRunning = false
                        self.statusMessage = err.localizedDescription
                    case .cancelled:
                        self.isRunning = false
                    default:
                        break
                    }
                }
            }
            listener.newConnectionHandler = { [weak self] conn in
                Task { @MainActor in
                    self?.accept(conn)
                }
            }
            listener.start(queue: .global(qos: .userInitiated))
            self.listener = listener
            statusMessage = "Starting on port \(port)…"
        } catch {
            statusMessage = error.localizedDescription
            isRunning = false
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
        connections.forEach { $0.cancel() }
        connections.removeAll()
        isRunning = false
        statusMessage = "Stopped"
    }

    private func accept(_ connection: NWConnection) {
        connections.append(connection)
        connection.start(queue: .global(qos: .userInitiated))
        receive(on: connection, buffer: Data())
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1024 * 256) { [weak self] data, _, isComplete, error in
            Task { @MainActor in
                guard let self else { return }
                var buf = buffer
                if let data { buf.append(data) }
                if let error {
                    self.statusMessage = error.localizedDescription
                    connection.cancel()
                    return
                }
                if self.tryHandleHTTP(buffer: buf, connection: connection) {
                    return
                }
                if isComplete {
                    connection.cancel()
                    return
                }
                // Cap buffer
                if buf.count > 80 * 1024 * 1024 {
                    self.respond(connection, status: 413, body: "Too large", contentType: "text/plain")
                    return
                }
                self.receive(on: connection, buffer: buf)
            }
        }
    }

    private func tryHandleHTTP(buffer: Data, connection: NWConnection) -> Bool {
        guard let headerEnd = buffer.range(of: Data("\r\n\r\n".utf8)) else { return false }
        let headerData = buffer.subdata(in: 0..<headerEnd.lowerBound)
        guard let headerText = String(data: headerData, encoding: .utf8) else { return false }
        let lines = headerText.split(separator: "\r\n", omittingEmptySubsequences: false).map(String.init)
        guard let requestLine = lines.first else { return false }
        let parts = requestLine.split(separator: " ")
        guard parts.count >= 2 else { return false }
        let method = String(parts[0])
        let path = String(parts[1])

        var contentLength = 0
        var contentType = ""
        for line in lines.dropFirst() {
            let lower = line.lowercased()
            if lower.hasPrefix("content-length:") {
                contentLength = Int(line.split(separator: ":").last?.trimmingCharacters(in: .whitespaces) ?? "0") ?? 0
            } else if lower.hasPrefix("content-type:") {
                contentType = String(line.dropFirst("content-type:".count)).trimmingCharacters(in: .whitespaces)
            }
        }

        let bodyStart = headerEnd.upperBound
        let bodyReceived = buffer.count - bodyStart
        if bodyReceived < contentLength { return false }

        let body = buffer.subdata(in: bodyStart..<(bodyStart + contentLength))

        if method == "GET" && (path == "/" || path.hasPrefix("/?")) {
            respond(connection, status: 200, body: Self.portalHTML, contentType: "text/html; charset=utf-8")
            return true
        }

        if method == "POST" && path.hasPrefix("/upload") {
            Task {
                await handleUpload(body: body, contentType: contentType, connection: connection)
            }
            return true
        }

        respond(connection, status: 404, body: "Not found", contentType: "text/plain")
        return true
    }

    private func handleUpload(body: Data, contentType: String, connection: NWConnection) async {
        // multipart/form-data
        guard contentType.contains("multipart/form-data"),
              let boundaryKey = contentType.split(separator: ";")
                .map({ $0.trimmingCharacters(in: .whitespaces) })
                .first(where: { $0.lowercased().hasPrefix("boundary=") }) else {
            respond(connection, status: 400, body: "Expected multipart form", contentType: "text/plain")
            return
        }
        let boundary = String(boundaryKey.dropFirst("boundary=".count))
        guard let file = parseMultipart(body: body, boundary: boundary) else {
            respond(connection, status: 400, body: "No file part", contentType: "text/plain")
            return
        }

        let ext = (file.filename as NSString).pathExtension
        let allowed = MediaLibraryScanner.audioExtensions
        if !allowed.contains(ext.lowercased()) {
            respond(connection, status: 415, body: "Unsupported type", contentType: "text/plain")
            return
        }

        do {
            let dir = await repository.mediaDirectory(for: .lan)
            let dest = dir.appendingPathComponent("\(UUID().uuidString)_\(file.filename)")
            try file.data.write(to: dest)
            var track = await MediaLibraryScanner.metadataTrack(from: dest, source: .lan)
                ?? Track(
                    id: "lan-\(UUID().uuidString)",
                    title: (file.filename as NSString).deletingPathExtension,
                    uri: dest.absoluteString,
                    source: .lan
                )
            track.source = .lan
            await repository.upsertTrack(track)
            if let pl = await repository.systemPlaylist(.lanImports) {
                await repository.addToPlaylist(playlistId: pl.id, trackId: track.id)
            }
            lastUploadName = track.title
            statusMessage = "Received \(track.title)"
            respond(connection, status: 200, body: "OK: \(track.title)", contentType: "text/plain")
        } catch {
            respond(connection, status: 500, body: error.localizedDescription, contentType: "text/plain")
        }
    }

    private struct MultipartFile {
        let filename: String
        let data: Data
    }

    private func parseMultipart(body: Data, boundary: String) -> MultipartFile? {
        let delim = Data("--\(boundary)".utf8)
        var search = body.startIndex
        while let r = body.range(of: delim, in: search..<body.endIndex) {
            let partStart = r.upperBound
            // skip leading CRLF
            var start = partStart
            if body[start..<body.endIndex].starts(with: Data("\r\n".utf8)) {
                start = body.index(start, offsetBy: 2)
            }
            guard let next = body.range(of: delim, in: start..<body.endIndex) else { break }
            var part = body.subdata(in: start..<next.lowerBound)
            // strip trailing CRLF
            if part.count >= 2, part.suffix(2) == Data("\r\n".utf8) {
                part = part.subdata(in: 0..<(part.count - 2))
            }
            if let sep = part.range(of: Data("\r\n\r\n".utf8)) {
                let headers = String(data: part.subdata(in: 0..<sep.lowerBound), encoding: .utf8) ?? ""
                let fileData = part.subdata(in: sep.upperBound..<part.endIndex)
                if headers.lowercased().contains("filename="),
                   let name = headers.groups(for: #"filename="([^"]+)""#).first {
                    return MultipartFile(filename: name, data: fileData)
                }
            }
            search = next.lowerBound
        }
        return nil
    }

    private func respond(_ connection: NWConnection, status: Int, body: String, contentType: String) {
        let reason = status == 200 ? "OK" : "ERR"
        let payload = Data(body.utf8)
        let header = """
        HTTP/1.1 \(status) \(reason)\r
        Content-Type: \(contentType)\r
        Content-Length: \(payload.count)\r
        Connection: close\r
        Access-Control-Allow-Origin: *\r
        \r

        """
        var data = Data(header.utf8)
        data.append(payload)
        connection.send(content: data, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private static let portalHTML = """
    <!DOCTYPE html>
    <html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
    <title>Simple MP3 — Quick Connect</title>
    <style>
    body{font-family:-apple-system,system-ui,sans-serif;background:#0C0A12;color:#F5F5F7;margin:0;padding:24px}
    h1{color:#00E5C0;font-size:1.4rem}
    .card{background:#1C1828;border-radius:16px;padding:20px;max-width:480px;margin:0 auto}
    input,button{width:100%;padding:14px;border-radius:12px;border:none;margin-top:12px;font-size:16px;box-sizing:border-box}
    button{background:#00E5C0;color:#0C0A12;font-weight:700}
    p{color:#B0B0BC;line-height:1.4}
    </style></head><body>
    <div class="card">
    <h1>Simple MP3</h1>
    <p>Upload audio to this iPhone over your LAN. Files land in <b>LAN Imports</b> and play offline / on CarPlay.</p>
    <form method="POST" action="/upload" enctype="multipart/form-data">
    <input type="file" name="file" accept="audio/*" required/>
    <button type="submit">Upload</button>
    </form>
    </div></body></html>
    """
}

private extension String {
    func groups(for pattern: String) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let range = NSRange(startIndex..., in: self)
        guard let match = regex.firstMatch(in: self, range: range), match.numberOfRanges > 1,
              let r = Range(match.range(at: 1), in: self) else { return [] }
        return [String(self[r])]
    }
}
