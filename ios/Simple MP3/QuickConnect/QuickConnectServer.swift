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
    private(set) var accessCode: String = ""
    private(set) var isLockedOut = false

    private var sessionToken = ""
    private var failedAttempts = 0
    private var lockedUntil: Date?
    private static let cookieName = "sm3_qc"
    private static let maxAuthAttempts = 5
    private static let maxPermanentLockout = 10

    init(repository: MusicRepository) {
        self.repository = repository
    }

    var portalURLs: [String] {
        localAddresses.map { "http://\($0):\(port)" }
    }

    var unlockURLs: [String] {
        guard !accessCode.isEmpty else { return portalURLs }
        return portalURLs.map { "\($0)/?code=\(accessCode)" }
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
        accessCode = String(format: "%06d", Int.random(in: 0...999_999))
        sessionToken = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        failedAttempts = 0
        lockedUntil = nil
        isLockedOut = false
        refreshAddresses()
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            let listener = try NWListener(using: params, on: NWEndpoint.Port(rawValue: port)!)
            listener.stateUpdateHandler = { state in
                Task { @MainActor [weak self] in
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
            listener.newConnectionHandler = { conn in
                Task { @MainActor [weak self] in
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
        isLockedOut = false
        accessCode = ""
        sessionToken = ""
        statusMessage = "Stopped"
    }

    private func accept(_ connection: NWConnection) {
        connections.append(connection)
        connection.start(queue: .global(qos: .userInitiated))
        receive(on: connection, buffer: Data())
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1024 * 256) { data, _, isComplete, error in
            Task { @MainActor [weak self] in
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
        var cookieHeader = ""
        for line in lines.dropFirst() {
            let lower = line.lowercased()
            if lower.hasPrefix("content-length:") {
                contentLength = Int(line.split(separator: ":").last?.trimmingCharacters(in: .whitespaces) ?? "0") ?? 0
            } else if lower.hasPrefix("content-type:") {
                contentType = String(line.dropFirst("content-type:".count)).trimmingCharacters(in: .whitespaces)
            } else if lower.hasPrefix("cookie:") {
                cookieHeader = String(line.dropFirst("cookie:".count)).trimmingCharacters(in: .whitespaces)
            }
        }

        let bodyStart = headerEnd.upperBound
        let bodyReceived = buffer.count - bodyStart
        if bodyReceived < contentLength { return false }

        let body = buffer.subdata(in: bodyStart..<(bodyStart + contentLength))

        if isLockedOut {
            respondJSON(connection, status: 403, object: [
                "ok": false,
                "error": "Portal locked — too many incorrect access codes. Reopen Quick Connect to try again."
            ])
            return true
        }

        if method == "GET" && (path == "/" || path.hasPrefix("/?")) {
            respond(connection, status: 200, body: Self.portalHTML, contentType: "text/html; charset=utf-8")
            return true
        }

        if method == "POST" && (path == "/api/auth" || path.hasPrefix("/api/auth?")) {
            handleAuth(body: body, connection: connection)
            return true
        }

        if method == "POST" && path.hasPrefix("/upload") {
            guard isAuthorized(cookieHeader: cookieHeader) else {
                respondJSON(connection, status: 401, object: [
                    "ok": false,
                    "error": "Unlock with the access code shown on the phone"
                ])
                return true
            }
            Task {
                await handleUpload(body: body, contentType: contentType, connection: connection)
            }
            return true
        }

        respond(connection, status: 404, body: "Not found", contentType: "text/plain")
        return true
    }

    private func isAuthorized(cookieHeader: String) -> Bool {
        guard !sessionToken.isEmpty else { return false }
        return cookieValue(cookieHeader, name: Self.cookieName) == sessionToken
    }

    private func cookieValue(_ header: String, name: String) -> String? {
        for part in header.split(separator: ";") {
            let trimmed = part.trimmingCharacters(in: .whitespaces)
            if trimmed.lowercased().hasPrefix("\(name)=") {
                return String(trimmed.dropFirst(name.count + 1))
            }
        }
        return nil
    }

    private func handleAuth(body: Data, connection: NWConnection) {
        if let lockedUntil, Date() < lockedUntil {
            let wait = Int(lockedUntil.timeIntervalSinceNow.rounded(.up))
            respondJSON(connection, status: 429, object: [
                "ok": false,
                "error": "Too many attempts — try again in \(max(wait, 1))s"
            ])
            return
        }
        var submitted = ""
        if let obj = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
           let code = obj["code"] as? String {
            submitted = code.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        guard Self.constantTimeEquals(submitted, accessCode), !accessCode.isEmpty else {
            failedAttempts += 1
            statusMessage = "Failed access code attempt"
            if failedAttempts >= Self.maxPermanentLockout {
                isLockedOut = true
                isRunning = false
                statusMessage = "Portal locked — too many incorrect codes. Stop and start again."
                respondJSON(connection, status: 403, object: [
                    "ok": false,
                    "error": "Portal locked — too many incorrect access codes. Reopen Quick Connect to try again."
                ])
                return
            }
            if failedAttempts >= Self.maxAuthAttempts {
                let overage = failedAttempts - Self.maxAuthAttempts
                let seconds = min(30 * (1 << min(overage, 4)), 300)
                lockedUntil = Date().addingTimeInterval(TimeInterval(seconds))
            }
            respondJSON(connection, status: 401, object: ["ok": false, "error": "Invalid access code"])
            return
        }
        failedAttempts = 0
        lockedUntil = nil
        statusMessage = "Desktop unlocked the portal"
        respondJSON(
            connection,
            status: 200,
            object: ["ok": true],
            extraHeaders: [
                "Set-Cookie": "\(Self.cookieName)=\(sessionToken); Path=/; HttpOnly; SameSite=Lax"
            ]
        )
    }

    static func sanitizeFileName(_ name: String) -> String {
        var base = name.components(separatedBy: "/").last ?? name
        base = base.components(separatedBy: "\\").last ?? base
        base = base.trimmingCharacters(in: .whitespacesAndNewlines)
        while base.hasPrefix(".") || base.hasPrefix(" ") {
            base = String(base.dropFirst())
        }
        base = base.replacingOccurrences(of: #"[^A-Za-z0-9._\- ]"#, with: "_", options: .regularExpression)
        while base.contains("..") {
            base = base.replacingOccurrences(of: "..", with: ".")
        }
        base = base.trimmingCharacters(in: CharacterSet(charactersIn: ". "))
        if base.count > 180 {
            base = String(base.prefix(180))
        }
        return base.isEmpty ? "upload.mp3" : base
    }

    private static func constantTimeEquals(_ a: String, _ b: String) -> Bool {
        let aa = Array(a.utf8)
        let bb = Array(b.utf8)
        guard aa.count == bb.count else { return false }
        var acc: UInt8 = 0
        for i in 0..<aa.count { acc |= aa[i] ^ bb[i] }
        return acc == 0
    }

    private func handleUpload(body: Data, contentType: String, connection: NWConnection) async {
        guard contentType.contains("multipart/form-data"),
              let boundaryKey = contentType.split(separator: ";")
                .map({ $0.trimmingCharacters(in: .whitespaces) })
                .first(where: { $0.lowercased().hasPrefix("boundary=") }) else {
            respondJSON(connection, status: 400, object: ["ok": false, "error": "Expected multipart form"])
            return
        }
        var boundary = String(boundaryKey.dropFirst("boundary=".count))
        if boundary.hasPrefix("\"") && boundary.hasSuffix("\"") && boundary.count >= 2 {
            boundary = String(boundary.dropFirst().dropLast())
        }
        let files = parseMultipart(body: body, boundary: boundary)
        guard !files.isEmpty else {
            respondJSON(connection, status: 400, object: ["ok": false, "error": "No file part"])
            return
        }

        let allowed = MediaLibraryScanner.audioExtensions
        var uploaded: [[String: String]] = []
        var errors: [[String: String]] = []

        for file in files {
            let safeName = Self.sanitizeFileName(file.filename)
            let ext = (safeName as NSString).pathExtension.lowercased()
            if !allowed.contains(ext) {
                errors.append(["filename": file.filename, "error": "Unsupported type"])
                continue
            }
            do {
                let dir = await repository.mediaDirectory(for: .lan)
                let dest = dir.appendingPathComponent("\(UUID().uuidString)_\(safeName)").standardizedFileURL
                let dirPath = dir.standardizedFileURL.path
                guard dest.path.hasPrefix(dirPath.hasSuffix("/") ? dirPath : dirPath + "/") else {
                    errors.append(["filename": file.filename, "error": "Invalid destination path"])
                    continue
                }
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
                uploaded.append(["title": track.title, "filename": file.filename])
            } catch {
                errors.append(["filename": file.filename, "error": error.localizedDescription])
            }
        }

        if let last = uploaded.last?["title"] {
            lastUploadName = uploaded.count == 1 ? last : "\(uploaded.count) files"
            statusMessage = uploaded.count == 1
                ? "Received \(last)"
                : "Received \(uploaded.count) files"
        }

        let ok = !uploaded.isEmpty
        respondJSON(connection, status: ok ? 200 : (errors.isEmpty ? 400 : 415), object: [
            "ok": ok,
            "uploaded": uploaded,
            "errors": errors
        ])
    }

    private struct MultipartFile {
        let filename: String
        let data: Data
    }

    private func parseMultipart(body: Data, boundary: String) -> [MultipartFile] {
        let delim = Data("--\(boundary)".utf8)
        var search = body.startIndex
        var files: [MultipartFile] = []
        while let r = body.range(of: delim, in: search..<body.endIndex) {
            let partStart = r.upperBound
            var start = partStart
            if body[start..<body.endIndex].starts(with: Data("\r\n".utf8)) {
                start = body.index(start, offsetBy: 2)
            }
            guard let next = body.range(of: delim, in: start..<body.endIndex) else { break }
            var part = body.subdata(in: start..<next.lowerBound)
            if part.count >= 2, part.suffix(2) == Data("\r\n".utf8) {
                part = part.subdata(in: 0..<(part.count - 2))
            }
            if let sep = part.range(of: Data("\r\n\r\n".utf8)) {
                let headers = String(data: part.subdata(in: 0..<sep.lowerBound), encoding: .utf8) ?? ""
                let fileData = part.subdata(in: sep.upperBound..<part.endIndex)
                if headers.lowercased().contains("filename="),
                   let name = Self.multipartFilename(in: headers),
                   !name.isEmpty {
                    files.append(MultipartFile(filename: name, data: fileData))
                }
            }
            search = next.lowerBound
        }
        return files
    }

    private static func multipartFilename(in headers: String) -> String? {
        if let quoted = headers.groups(for: #"filename="([^"]*)""#).first {
            return quoted
        }
        return headers.groups(for: #"filename=([^;\s]+)"#).first
    }

    private func respondJSON(
        _ connection: NWConnection,
        status: Int,
        object: Any,
        extraHeaders: [String: String] = [:]
    ) {
        let payload: Data
        if JSONSerialization.isValidJSONObject(object),
           let data = try? JSONSerialization.data(withJSONObject: object) {
            payload = data
        } else {
            payload = Data(#"{"ok":false,"error":"encode failed"}"#.utf8)
        }
        let body = String(data: payload, encoding: .utf8) ?? #"{"ok":false}"#
        respond(connection, status: status, body: body, contentType: "application/json", extraHeaders: extraHeaders)
    }

    private func respond(
        _ connection: NWConnection,
        status: Int,
        body: String,
        contentType: String,
        extraHeaders: [String: String] = [:]
    ) {
        let reason = status == 200 ? "OK" : "ERR"
        let payload = Data(body.utf8)
        var headers: [String: String] = [
            "X-Content-Type-Options": "nosniff",
            "X-Frame-Options": "DENY",
            "X-XSS-Protection": "1; mode=block",
            "Content-Security-Policy": "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data:; connect-src 'self'"
        ]
        for (key, value) in extraHeaders {
            headers[key] = value
        }
        var extra = ""
        for (key, value) in headers {
            extra += "\(key): \(value)\r\n"
        }
        let header = """
        HTTP/1.1 \(status) \(reason)\r
        Content-Type: \(contentType)\r
        Content-Length: \(payload.count)\r
        Connection: close\r
        \(extra)\r

        """
        var data = Data(header.utf8)
        data.append(payload)
        connection.send(content: data, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private static let portalHTML = #"""
    <!DOCTYPE html>
    <html lang="en"><head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width,initial-scale=1"/>
    <title>Simple MP3 — Quick Connect</title>
    <style>
      :root {
        --bg: #0C0A12;
        --card: #1C1828;
        --elevated: #2A2438;
        --teal: #00E5C0;
        --text: #F5F5F7;
        --muted: #B0B0BC;
        --coral: #FF6B6B;
        --radius: 16px;
      }
      * { box-sizing: border-box; }
      body {
        font-family: -apple-system, system-ui, sans-serif;
        background: radial-gradient(900px 500px at 10% -10%, #142422 0%, var(--bg) 55%);
        color: var(--text); margin: 0; min-height: 100vh; padding: 24px;
      }
      .card {
        background: var(--card); border-radius: var(--radius);
        padding: 22px; max-width: 560px; margin: 0 auto;
        border: 1px solid rgba(255,255,255,0.04);
      }
      h1 { color: var(--teal); font-size: 1.4rem; margin: 0 0 8px; }
      .lead { color: var(--muted); line-height: 1.45; margin: 0 0 18px; }
      .drop {
        border: 2px dashed rgba(0,229,192,0.4); border-radius: var(--radius);
        padding: 40px 20px; text-align: center;
        background: rgba(0,229,192,0.05);
        cursor: pointer; transition: border-color .15s, background .15s, transform .15s;
      }
      .drop:hover { border-color: var(--teal); background: rgba(0,229,192,0.08); }
      .drop.drag {
        border-color: var(--teal); background: rgba(0,229,192,0.16);
        transform: scale(1.01);
      }
      .drop h2 { margin: 0 0 8px; font-size: 1.15rem; }
      .drop p { margin: 0; color: var(--muted); font-size: 0.92rem; }
      .drop input { display: none; }
      .pick {
        appearance: none; border: 0; cursor: pointer;
        background: var(--teal); color: var(--bg); font-weight: 700;
        padding: 12px 18px; border-radius: 12px; font-size: 0.95rem; margin-top: 16px;
      }
      .pick:disabled { opacity: 0.5; cursor: not-allowed; }
      .progress {
        display: none; height: 6px; background: var(--elevated);
        border-radius: 999px; overflow: hidden; margin-top: 16px;
      }
      .progress.on { display: block; }
      .progress > i { display: block; height: 100%; width: 0; background: var(--teal); transition: width .2s; }
      .status { margin-top: 14px; font-size: 0.95rem; min-height: 1.2em; }
      .status.ok { color: var(--teal); }
      .status.err { color: var(--coral); }
      .results { margin-top: 16px; display: flex; flex-direction: column; gap: 8px; }
      .item {
        background: var(--elevated); border-radius: 12px;
        padding: 10px 12px; font-size: 0.9rem;
        display: flex; gap: 10px; align-items: flex-start;
      }
      .item .mark { font-weight: 700; flex: 0 0 auto; }
      .item.ok .mark { color: var(--teal); }
      .item.err .mark { color: var(--coral); }
      .item .meta { color: var(--muted); font-size: 0.8rem; margin-top: 2px; }
      .hidden { display: none !important; }
      .code-input {
        width: 100%; padding: 14px; border-radius: 12px; border: 1px solid rgba(255,255,255,0.08);
        background: var(--elevated); color: var(--text); font-size: 1.5rem; font-weight: 700;
        letter-spacing: 0.35em; text-align: center; outline: none; box-sizing: border-box;
      }
      .code-input:focus { border-color: var(--teal); }
      .unlock {
        appearance: none; border: 0; cursor: pointer; width: 100%; margin-top: 12px;
        background: var(--teal); color: var(--bg); font-weight: 700;
        padding: 12px 18px; border-radius: 12px; font-size: 0.95rem;
      }
      .unlock:disabled { opacity: 0.5; cursor: not-allowed; }
    </style>
    </head><body>
    <div class="card" id="login-card">
      <h1>Simple MP3</h1>
      <p class="lead">Enter the 6-digit access code shown on the iPhone, then upload audio to <b>LAN Imports</b>.</p>
      <input id="code" class="code-input" type="text" inputmode="numeric" maxlength="6" placeholder="000000" autocomplete="one-time-code"/>
      <button type="button" class="unlock" id="unlock">Unlock</button>
      <div id="login-err" class="status err"></div>
    </div>
    <div class="card hidden" id="app">
      <h1>Simple MP3</h1>
      <p class="lead">Drop audio onto this page or choose multiple files. They stay on this iPhone in <b>LAN Imports</b> and play offline / on CarPlay.</p>
      <div class="drop" id="drop" role="button" tabindex="0" aria-label="Drop audio files or click to browse">
        <h2>Drop MP3s here</h2>
        <p>MP3, M4A, AAC, FLAC, WAV, OGG, Opus · multiple files OK</p>
        <input id="file" type="file" name="file" accept="audio/*,.mp3,.m4a,.aac,.flac,.ogg,.opus,.wav,.aiff,.aif,.caf" multiple/>
        <button type="button" class="pick" id="pick">Choose files</button>
      </div>
      <div class="progress" id="progress"><i id="bar"></i></div>
      <div class="status" id="status"></div>
      <div class="results" id="results"></div>
    </div>
    <script>
    (function () {
      const drop = document.getElementById('drop');
      const input = document.getElementById('file');
      const pick = document.getElementById('pick');
      const progress = document.getElementById('progress');
      const bar = document.getElementById('bar');
      const status = document.getElementById('status');
      const results = document.getElementById('results');
      const loginCard = document.getElementById('login-card');
      const appCard = document.getElementById('app');
      const unlockBtn = document.getElementById('unlock');
      const codeInput = document.getElementById('code');
      const loginErr = document.getElementById('login-err');
      const allowedExt = /\.(mp3|m4a|aac|wav|aiff|aif|flac|caf|ogg|opus)$/i;
      let dragDepth = 0;
      let busy = false;
      let authorized = false;

      function showLoginError(msg) {
        loginErr.textContent = msg || '';
      }

      async function unlock() {
        const code = (codeInput.value || '').trim();
        if (!code) { showLoginError('Enter the 6-digit code from the iPhone'); return; }
        unlockBtn.disabled = true;
        showLoginError('');
        try {
          const res = await fetch('/api/auth', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ code: code })
          });
          let data = {};
          try { data = await res.json(); } catch (_) { data = {}; }
          if (!res.ok) throw new Error(data.error || 'Invalid code');
          authorized = true;
          loginCard.classList.add('hidden');
          appCard.classList.remove('hidden');
        } catch (e) {
          showLoginError(e.message || 'Invalid code');
        } finally {
          unlockBtn.disabled = false;
        }
      }

      unlockBtn.addEventListener('click', unlock);
      codeInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') unlock();
      });
      try {
        const fromQuery = new URLSearchParams(window.location.search || '').get('code') || '';
        if (fromQuery) {
          codeInput.value = fromQuery.trim();
          unlock();
          if (window.history && window.history.replaceState) {
            window.history.replaceState({}, '', window.location.pathname || '/');
          }
        }
      } catch (_) {}

      function setStatus(msg, kind) {
        status.textContent = msg || '';
        status.className = 'status' + (kind ? ' ' + kind : '');
      }

      function addResult(ok, title, detail) {
        const row = document.createElement('div');
        row.className = 'item ' + (ok ? 'ok' : 'err');
        const mark = document.createElement('span');
        mark.className = 'mark';
        mark.textContent = ok ? '✓' : '✕';
        const body = document.createElement('div');
        const name = document.createElement('div');
        name.textContent = title;
        body.appendChild(name);
        if (detail) {
          const meta = document.createElement('div');
          meta.className = 'meta';
          meta.textContent = detail;
          body.appendChild(meta);
        }
        row.appendChild(mark);
        row.appendChild(body);
        results.prepend(row);
      }

      function isAudio(file) {
        if (allowedExt.test(file.name)) return true;
        return !!(file.type && file.type.indexOf('audio/') === 0);
      }

      async function uploadFiles(fileList) {
        const files = Array.from(fileList || []);
        if (!files.length || busy) return;
        const audio = files.filter(isAudio);
        const skipped = files.filter(function (f) { return !isAudio(f); });
        skipped.forEach(function (f) {
          addResult(false, f.name, 'Not an audio file');
        });
        if (!audio.length) {
          setStatus(skipped.length ? 'No audio files in that drop' : 'No files selected', 'err');
          return;
        }

        busy = true;
        pick.disabled = true;
        progress.classList.add('on');
        bar.style.width = '4%';
        setStatus('Uploading ' + audio.length + ' file' + (audio.length === 1 ? '' : 's') + '…');

        let okCount = 0;
        let failCount = skipped.length;
        for (let i = 0; i < audio.length; i++) {
          const file = audio[i];
          const fd = new FormData();
          fd.append('file', file, file.name);
          try {
            const res = await fetch('/upload', { method: 'POST', body: fd, credentials: 'same-origin' });
            let data = {};
            try { data = await res.json(); } catch (_) { data = {}; }
            const uploaded = data.uploaded || [];
            const errors = data.errors || [];
            if (uploaded.length) {
              uploaded.forEach(function (u) {
                addResult(true, u.title || u.filename || file.name, u.filename && u.title !== u.filename ? u.filename : 'Added to LAN Imports');
                okCount++;
              });
            }
            if (errors.length) {
              errors.forEach(function (e) {
                addResult(false, e.filename || file.name, e.error || 'Upload failed');
                failCount++;
              });
            }
            if (!uploaded.length && !errors.length) {
              if (res.ok) {
                addResult(true, file.name, 'Added to LAN Imports');
                okCount++;
              } else {
                addResult(false, file.name, data.error || ('HTTP ' + res.status));
                failCount++;
              }
            }
          } catch (e) {
            addResult(false, file.name, e.message || 'Network error');
            failCount++;
          }
          bar.style.width = Math.round(((i + 1) / audio.length) * 100) + '%';
        }

        if (failCount && okCount) {
          setStatus('Uploaded ' + okCount + ', ' + failCount + ' failed', 'err');
        } else if (failCount) {
          setStatus('Upload failed', 'err');
        } else {
          setStatus('Uploaded ' + okCount + ' file' + (okCount === 1 ? '' : 's') + '. Drop more anytime.', 'ok');
        }

        input.value = '';
        busy = false;
        pick.disabled = false;
        setTimeout(function () { progress.classList.remove('on'); }, 700);
      }

      function openPicker() {
        if (busy) return;
        input.click();
      }

      pick.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        openPicker();
      });
      drop.addEventListener('click', function (e) {
        if (e.target === pick) return;
        openPicker();
      });
      drop.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          openPicker();
        }
      });
      input.addEventListener('change', function () {
        uploadFiles(input.files);
      });

      window.addEventListener('dragenter', function (e) {
        e.preventDefault();
        dragDepth++;
        drop.classList.add('drag');
      });
      window.addEventListener('dragover', function (e) {
        e.preventDefault();
        if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
      });
      window.addEventListener('dragleave', function (e) {
        e.preventDefault();
        dragDepth--;
        if (dragDepth <= 0) {
          dragDepth = 0;
          drop.classList.remove('drag');
        }
      });
      window.addEventListener('drop', function (e) {
        e.preventDefault();
        dragDepth = 0;
        drop.classList.remove('drag');
        if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
          uploadFiles(e.dataTransfer.files);
        }
      });
    })();
    </script>
    </body></html>
    """#
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
