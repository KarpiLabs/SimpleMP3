//
//  DuplicateDetector.swift
//  Simple MP3
//

import Foundation

enum DuplicateDetector {
    static let durationBucketMs: Int64 = 2_000

    struct Group: Identifiable, Sendable {
        var id: String { key }
        var key: String
        var tracks: [Track]
        var keepId: String

        var extras: [Track] { tracks.filter { $0.id != keepId } }
        var keeper: Track { tracks.first { $0.id == keepId } ?? tracks[0] }
    }

    static func findGroups(_ tracks: [Track]) -> [Group] {
        let songs = tracks.filter { $0.source != .stream && !$0.isHidden }
        var buckets: [String: [Track]] = [:]
        for track in songs {
            guard let key = fingerprint(track) else { continue }
            buckets[key, default: []].append(track)
        }
        return buckets.values
            .filter { $0.count >= 2 }
            .map { group in
                let ordered = group.sorted(by: keepBefore)
                return Group(
                    key: fingerprint(ordered[0]) ?? ordered[0].id,
                    tracks: ordered,
                    keepId: ordered[0].id
                )
            }
            .sorted { $0.keeper.title.localizedCaseInsensitiveCompare($1.keeper.title) == .orderedAscending }
    }

    static func fingerprint(_ track: Track) -> String? {
        let title = normalize(track.title)
        let artist = normalize(track.artist)
        if title.isEmpty || title == "unknown title" { return nil }
        if artist.isEmpty || artist == "unknown artist" { return nil }
        let bucket = track.duration > 0 ? track.duration / durationBucketMs : -1
        return "\(title)|\(artist)|\(bucket)"
    }

    static func normalize(_ value: String) -> String {
        let folded = value.lowercased()
        let stripped = folded.replacingOccurrences(of: #"[\p{Punct}]+"#, with: " ", options: .regularExpression)
        return stripped.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func keepBefore(_ a: Track, _ b: Track) -> Bool {
        if a.playCount != b.playCount { return a.playCount > b.playCount }
        let ascore = musicFolderScore(a.folderPath)
        let bscore = musicFolderScore(b.folderPath)
        if ascore != bscore { return ascore > bscore }
        if a.size != b.size { return a.size > b.size }
        let aArt = a.artworkUri?.isEmpty == false
        let bArt = b.artworkUri?.isEmpty == false
        if aArt != bArt { return aArt && !bArt }
        return a.id < b.id
    }

    private static func musicFolderScore(_ folderPath: String) -> Int {
        let path = folderPath.lowercased()
        if path == "music" || path.hasPrefix("music/") { return 2 }
        if path.contains("/music/") || path.hasSuffix("/music") { return 1 }
        return 0
    }
}
