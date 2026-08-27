//
//  StreamSaver.swift
//  Simple MP3
//
//  Best-effort artwork for a live stream bookmark (embedded picture, ICY station
//  page, HLS logo, Open Graph). Mirrors Android StreamArtworkFetcher.
//

import AVFoundation
import Foundation
import UIKit

enum StreamSaveError: LocalizedError {
    case invalidURL

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "That doesn't look like a valid stream URL."
        }
    }
}

enum StreamSaver {
    struct Probe {
        var icyName: String?
        var imageData: Data?
    }

    static func validateURL(_ urlString: String) throws -> URL {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              url.scheme == "http" || url.scheme == "https"
        else {
            throw StreamSaveError.invalidURL
        }
        return url
    }

    /// Stable bookmark id for a stream URL (FNV-1a, not Swift's per-process hash).
    static func streamKey(for urlString: String) -> String {
        var h: UInt64 = 0xcbf29ce484222325
        for b in urlString.utf8 {
            h ^= UInt64(b)
            h &*= 0x100000001b3
        }
        return "stream:\(String(h, radix: 16))"
    }

    static func captureArtwork(from streamURL: URL) async -> Probe {
        if let embedded = await embeddedPicture(url: streamURL) {
            return Probe(imageData: embedded)
        }
        let icy = await icyHeaders(url: streamURL)
        if let imageURL = icy.imageURL, let data = await downloadImage(imageURL) {
            return Probe(icyName: icy.name, imageData: data)
        }
        if let station = icy.stationURL, let data = await imageFromHTML(page: station) {
            return Probe(icyName: icy.name, imageData: data)
        }
        if isHLS(streamURL), let data = await hlsImage(url: streamURL) {
            return Probe(icyName: icy.name, imageData: data)
        }
        if let origin = origin(of: streamURL), let data = await imageFromHTML(page: origin) {
            return Probe(icyName: icy.name, imageData: data)
        }
        return Probe(icyName: icy.name)
    }

    static func persistArtwork(_ data: Data, to destination: URL) throws -> URL {
        try? FileManager.default.removeItem(at: destination)
        try data.write(to: destination, options: [.atomic])
        return destination
    }

    static func imageURLFromHTML(_ html: String, base: URL) -> URL? {
        let patterns = [
            #"<meta[^>]+property\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']"#,
            #"<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:image["']"#,
            #"<meta[^>]+name\s*=\s*["']twitter:image["'][^>]+content\s*=\s*["']([^"']+)["']"#,
            #"<link[^>]+rel\s*=\s*["'][^"']*apple-touch-icon[^"']*["'][^>]+href\s*=\s*["']([^"']+)["']"#,
        ]
        for pattern in patterns {
            if let match = firstGroup(pattern, in: html) {
                return resolve(match, base: base)
            }
        }
        return nil
    }

    static func imageURLFromHLS(_ playlist: String, base: URL) -> URL? {
        if let logo = firstGroup(#"tvg-logo\s*=\s*["']([^"']+)["']"#, in: playlist) {
            return resolve(logo, base: base)
        }
        if let found = firstGroup(
            #"https?://[^\s"'<>\\]+?\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>\\]*)?"#,
            in: playlist
        ) {
            return URL(string: found)
        }
        return nil
    }

    // MARK: - Private

    private struct IcyHeaders {
        var name: String?
        var stationURL: URL?
        var imageURL: URL?
    }

    private static func embeddedPicture(url: URL) async -> Data? {
        await withTimeout(seconds: 3.5) {
            let asset = AVURLAsset(url: url)
            let metadata = try await asset.load(.commonMetadata)
            let artItems = AVMetadataItem.metadataItems(
                from: metadata,
                filteredByIdentifier: .commonIdentifierArtwork
            )
            guard let item = artItems.first else { return nil }
            return try await item.load(.dataValue)
        }
    }

    private static func icyHeaders(url: URL) async -> IcyHeaders {
        var request = URLRequest(url: url, timeoutInterval: 6)
        request.setValue("SimpleMP3/1.1", forHTTPHeaderField: "User-Agent")
        request.setValue("1", forHTTPHeaderField: "Icy-MetaData")
        request.httpMethod = "GET"
        guard let (bytes, response) = try? await URLSession.shared.bytes(for: request),
              let http = response as? HTTPURLResponse
        else {
            return IcyHeaders()
        }
        bytes.task.cancel()
        func header(_ name: String) -> String? {
            let value = http.value(forHTTPHeaderField: name)?.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let value, !value.isEmpty, value != "0" else { return nil }
            return value
        }
        return IcyHeaders(
            name: header("icy-name") ?? header("ice-name"),
            stationURL: (header("icy-url") ?? header("ice-url")).flatMap(URL.init(string:)),
            imageURL: (header("icy-logo") ?? header("ice-logo") ?? header("icy-image")).flatMap(URL.init(string:))
        )
    }

    private static func hlsImage(url: URL) async -> Data? {
        guard let text = await downloadText(url),
              let imageURL = imageURLFromHLS(text, base: url)
        else { return nil }
        return await downloadImage(imageURL)
    }

    private static func imageFromHTML(page: URL) async -> Data? {
        guard let html = await downloadText(page),
              let imageURL = imageURLFromHTML(html, base: page)
        else { return nil }
        return await downloadImage(imageURL)
    }

    private static func downloadText(_ url: URL) async -> String? {
        var request = URLRequest(url: url, timeoutInterval: 6)
        request.setValue("SimpleMP3/1.1", forHTTPHeaderField: "User-Agent")
        guard let (bytes, response) = try? await URLSession.shared.bytes(for: request),
              let http = response as? HTTPURLResponse,
              (200 ..< 300).contains(http.statusCode)
        else { return nil }
        let type = http.value(forHTTPHeaderField: "Content-Type") ?? ""
        if type.hasPrefix("audio/") || type.hasPrefix("video/") {
            bytes.task.cancel()
            return nil
        }
        var collected = Data()
        do {
            for try await byte in bytes {
                collected.append(byte)
                if collected.count >= 256_000 { break }
            }
        } catch {
            if collected.isEmpty { return nil }
        }
        bytes.task.cancel()
        return String(data: collected, encoding: .utf8) ?? String(data: collected, encoding: .isoLatin1)
    }

    private static func downloadImage(_ url: URL) async -> Data? {
        var request = URLRequest(url: url, timeoutInterval: 6)
        request.setValue("SimpleMP3/1.1", forHTTPHeaderField: "User-Agent")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse,
              (200 ..< 300).contains(http.statusCode),
              data.count >= 16,
              data.count <= 4 * 1024 * 1024,
              looksLikeImage(data)
        else { return nil }
        return data
    }

    private static func looksLikeImage(_ data: Data) -> Bool {
        guard data.count >= 12 else { return false }
        let b = [UInt8](data.prefix(12))
        if b[0] == 0xFF && b[1] == 0xD8 { return true }
        if b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47 { return true }
        if b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 { return true }
        if b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46 &&
            b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50 { return true }
        return false
    }

    private static func isHLS(_ url: URL) -> Bool {
        url.path.lowercased().hasSuffix(".m3u8")
    }

    private static func origin(of url: URL) -> URL? {
        var bits = URLComponents(url: url, resolvingAgainstBaseURL: false)
        bits?.path = "/"
        bits?.query = nil
        bits?.fragment = nil
        return bits?.url
    }

    private static func resolve(_ raw: String, base: URL) -> URL? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.lowercased().hasPrefix("http://") || trimmed.lowercased().hasPrefix("https://") {
            return URL(string: trimmed)
        }
        return URL(string: trimmed, relativeTo: base)?.absoluteURL
    }

    private static func firstGroup(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else {
            return nil
        }
        let range = NSRange(text.startIndex..., in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range) else { return nil }
        let idx = match.numberOfRanges > 1 ? 1 : 0
        guard let swiftRange = Range(match.range(at: idx), in: text) else { return nil }
        return String(text[swiftRange])
    }

    private static func withTimeout<T: Sendable>(
        seconds: Double,
        operation: @escaping @Sendable () async throws -> T?
    ) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask {
                try? await operation()
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }
}
