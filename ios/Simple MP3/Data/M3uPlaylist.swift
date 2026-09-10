//
//  M3uPlaylist.swift
//  Simple MP3
//

import Foundation

enum M3uPlaylist {
    struct Entry: Equatable, Sendable {
        var location: String
        var title: String?
        var artist: String?
        var durationSec: Int?

        var fileName: String {
            let trimmed = location.split(separator: "?").first.map(String.init) ?? location
            return (trimmed as NSString).lastPathComponent
        }
    }

    struct MatchResult: Sendable {
        var matched: [Track]
        var unmatched: [Entry]
        var playlistName: String
    }

    static func parse(_ text: String, defaultName: String = "Imported playlist") -> (String, [Entry]) {
        let lines = text.replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: "\n")
        var entries: [Entry] = []
        var pendingTitle: String?
        var pendingArtist: String?
        var pendingDuration: Int?
        var playlistName = defaultName

        for raw in lines {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }
            if line.caseInsensitiveCompare("#EXTM3U") == .orderedSame { continue }
            if line.uppercased().hasPrefix("#PLAYLIST:") {
                let name = String(line.dropFirst("#PLAYLIST:".count)).trimmingCharacters(in: .whitespaces)
                if !name.isEmpty { playlistName = name }
                continue
            }
            if line.uppercased().hasPrefix("#EXTINF:") {
                let body = String(line.dropFirst("#EXTINF:".count))
                let comma = body.firstIndex(of: ",")
                let durationPart = comma.map { String(body[..<$0]) } ?? body
                let titlePart = comma.map { String(body[body.index(after: $0)...]).trimmingCharacters(in: .whitespaces) } ?? ""
                pendingDuration = Int(durationPart.trimmingCharacters(in: .whitespaces).split(separator: ".").first.map(String.init) ?? "")
                let parsed = splitArtistTitle(titlePart)
                pendingArtist = parsed.0
                pendingTitle = parsed.1
                continue
            }
            if line.hasPrefix("#") { continue }
            entries.append(Entry(location: line, title: pendingTitle, artist: pendingArtist, durationSec: pendingDuration))
            pendingTitle = nil
            pendingArtist = nil
            pendingDuration = nil
        }
        return (playlistName, entries)
    }

    static func write(name: String, tracks: [Track]) -> String {
        var sb = "#EXTM3U\n"
        if !name.trimmingCharacters(in: .whitespaces).isEmpty {
            sb += "#PLAYLIST:\(name.trimmingCharacters(in: .whitespacesAndNewlines))\n"
        }
        for track in tracks {
            let seconds = track.duration > 0 ? Int(track.duration / 1000) : -1
            sb += "#EXTINF:\(seconds),\(track.artist) - \(track.title)\n"
            sb += "\(track.uri)\n"
        }
        return sb
    }

    static func match(entries: [Entry], library: [Track], playlistName: String) -> MatchResult {
        let songs = library.filter { $0.source != .stream && !$0.isHidden }
        var byURI: [String: Track] = [:]
        var byFile: [String: [Track]] = [:]
        for track in songs {
            let loc = normalizeLocation(track.uri)
            if !loc.isEmpty { byURI[loc] = track }
            let name = fileName(of: track.uri).lowercased()
            if !name.isEmpty {
                byFile[name, default: []].append(track)
            }
        }

        var seen = Set<String>()
        var matched: [Track] = []
        var unmatched: [Entry] = []
        for entry in entries {
            if let hit = matchEntry(entry, byURI: byURI, byFile: byFile, songs: songs),
               seen.insert(hit.id).inserted {
                matched.append(hit)
            } else if matchEntry(entry, byURI: byURI, byFile: byFile, songs: songs) == nil {
                unmatched.append(entry)
            }
        }
        return MatchResult(matched: matched, unmatched: unmatched, playlistName: playlistName)
    }

    static func splitArtistTitle(_ raw: String) -> (String?, String?) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return (nil, nil) }
        if let range = text.range(of: " - ") {
            let artist = String(text[..<range.lowerBound]).trimmingCharacters(in: .whitespaces)
            let title = String(text[range.upperBound...]).trimmingCharacters(in: .whitespaces)
            return (artist.isEmpty ? nil : artist, title.isEmpty ? text : title)
        }
        return (nil, text)
    }

    static func normalizeLocation(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.lowercased().hasPrefix("file://") {
            s = String(s.dropFirst("file://".count))
            s = s.removingPercentEncoding ?? s
        }
        return s.replacingOccurrences(of: "\\", with: "/").lowercased()
    }

    static func fileName(of location: String) -> String {
        let trimmed = location.split(separator: "?").first.map(String.init) ?? location
        return (trimmed as NSString).lastPathComponent
    }

    private static func matchEntry(
        _ entry: Entry,
        byURI: [String: Track],
        byFile: [String: [Track]],
        songs: [Track]
    ) -> Track? {
        let loc = normalizeLocation(entry.location)
        if !loc.isEmpty {
            if let hit = byURI[loc] { return hit }
            if let hit = byURI.first(where: { $0.key.hasSuffix(loc) || loc.hasSuffix($0.key) })?.value {
                return hit
            }
        }
        let name = entry.fileName.lowercased()
        if !name.isEmpty, let hits = byFile[name] {
            if hits.count == 1 { return hits[0] }
            if let pick = durationPick(entry, hits) { return pick }
        }
        if let title = entry.title?.trimmingCharacters(in: .whitespaces), !title.isEmpty {
            let hits = songs.filter {
                $0.title.caseInsensitiveCompare(title) == .orderedSame &&
                    (entry.artist == nil || entry.artist?.isEmpty == true
                        || $0.artist.caseInsensitiveCompare(entry.artist!) == .orderedSame)
            }
            if hits.count == 1 { return hits[0] }
            if let pick = durationPick(entry, hits) { return pick }
        }
        return nil
    }

    private static func durationPick(_ entry: Entry, _ candidates: [Track]) -> Track? {
        guard let want = entry.durationSec, want > 0 else { return nil }
        let close = candidates.filter {
            $0.duration > 0 && abs(Int($0.duration / 1000) - want) <= 2
        }
        return close.count == 1 ? close[0] : nil
    }
}
