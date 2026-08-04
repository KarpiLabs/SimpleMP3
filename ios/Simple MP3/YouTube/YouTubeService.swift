//
//  YouTubeService.swift
//  Simple MP3
//
//  Imports audio via document picker / shared files, and best-effort
//  Piped-style metadata lookup for display. Full YouTube extraction is
//  limited on iOS App Store builds; users can import audio files and
//  tag them as YouTube downloads.
//

import Foundation
import Observation
import UniformTypeIdentifiers

@Observable
@MainActor
final class YouTubeService {
    private let repository: MusicRepository

    private(set) var isWorking = false
    private(set) var statusMessage: String?
    private(set) var lastImported: Track?

    init(repository: MusicRepository) {
        self.repository = repository
    }

    /// Import a local audio file into the YouTube Downloads library.
    func importAudioFile(from url: URL, videoId: String? = nil, titleOverride: String? = nil) async {
        isWorking = true
        defer { isWorking = false }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }

        do {
            let dir = await repository.mediaDirectory(for: .youtube)
            let ext = url.pathExtension.isEmpty ? "m4a" : url.pathExtension
            let base = (titleOverride ?? url.deletingPathExtension().lastPathComponent)
                .replacingOccurrences(of: "/", with: "-")
            let dest = dir.appendingPathComponent("\(UUID().uuidString)_\(base).\(ext)")
            if FileManager.default.fileExists(atPath: dest.path) {
                try FileManager.default.removeItem(at: dest)
            }
            try FileManager.default.copyItem(at: url, to: dest)

            var track = await MediaLibraryScanner.metadataTrack(
                from: dest,
                source: .youtube,
                externalId: videoId
            ) ?? Track(
                id: "youtube-\(videoId ?? UUID().uuidString)",
                title: titleOverride ?? base,
                uri: dest.absoluteString,
                source: .youtube,
                externalId: videoId
            )
            if let titleOverride { track.title = titleOverride }
            track.source = .youtube
            await repository.upsertTrack(track)
            if let pl = await repository.systemPlaylist(.youtubeDownloads) {
                await repository.addToPlaylist(playlistId: pl.id, trackId: track.id)
            }
            lastImported = track
            statusMessage = "Imported \(track.title)"
        } catch {
            statusMessage = error.localizedDescription
        }
    }

    /// Extract a YouTube video id from common URL forms.
    static func videoId(from input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count == 11, trimmed.range(of: #"^[\w-]{11}$"#, options: .regularExpression) != nil {
            return trimmed
        }
        guard let url = URL(string: trimmed) else { return nil }
        if let host = url.host, host.contains("youtu.be") {
            let id = url.pathTokens.first
            return id?.count == 11 ? id : nil
        }
        if let comps = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let v = comps.queryItems?.first(where: { $0.name == "v" })?.value,
           v.count == 11 {
            return v
        }
        // /embed/ID or /shorts/ID
        let parts = url.path.split(separator: "/").map(String.init)
        if let idx = parts.firstIndex(where: { $0 == "embed" || $0 == "shorts" || $0 == "v" }),
           idx + 1 < parts.count, parts[idx + 1].count == 11 {
            return parts[idx + 1]
        }
        return nil
    }

    /// Best-effort: fetch oEmbed title for a YouTube URL (no download).
    func fetchTitle(for urlString: String) async -> String? {
        guard let id = Self.videoId(from: urlString) else { return nil }
        guard let api = URL(string: "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=\(id)&format=json") else {
            return nil
        }
        do {
            let (data, _) = try await URLSession.shared.data(from: api)
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let title = json["title"] as? String {
                return title
            }
        } catch {}
        return nil
    }

    func prepareImportHint(for urlString: String) async {
        isWorking = true
        defer { isWorking = false }
        if let id = Self.videoId(from: urlString) {
            let title = await fetchTitle(for: urlString)
            statusMessage = title.map { "Ready to import audio for “\($0)” (id \(id)). Use Files to add the audio." }
                ?? "Video id \(id). Import an audio file to add it offline."
        } else {
            statusMessage = "Paste a YouTube link, then import the audio file via Files."
        }
    }
}

private extension URL {
    var pathTokens: [String] {
        path.split(separator: "/").map(String.init).filter { !$0.isEmpty }
    }
}
