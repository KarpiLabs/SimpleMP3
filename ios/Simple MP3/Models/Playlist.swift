//
//  Playlist.swift
//  Simple MP3
//

import Foundation

nonisolated struct Playlist: Identifiable, Codable, Hashable, Sendable {
    var id: String
    var name: String
    var description: String
    var coverUri: String?
    var createdAt: Int64
    var updatedAt: Int64
    var isSystem: Bool
    var systemType: String?
    /// Ordered track ids.
    var trackIds: [String]

    init(
        id: String = UUID().uuidString,
        name: String,
        description: String = "",
        coverUri: String? = nil,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        isSystem: Bool = false,
        systemType: String? = nil,
        trackIds: [String] = []
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.coverUri = coverUri
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isSystem = isSystem
        self.systemType = systemType
        self.trackIds = trackIds
    }

    mutating func touch() {
        updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
    }
}

/// Computed ("auto") playlists derived from track metadata rather than stored
/// track ids. Contents are queried on demand so they always stay fresh.
nonisolated enum SmartPlaylist: String, CaseIterable, Identifiable, Sendable {
    case mostPlayed = "most_played"
    case recentlyAdded = "recently_added"
    case neverPlayed = "never_played"
    case notRecently = "not_recently"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .mostPlayed: return "Most Played"
        case .recentlyAdded: return "Recently Added"
        case .neverPlayed: return "Never Played"
        case .notRecently: return "Not in a while"
        }
    }

    var detail: String {
        switch self {
        case .mostPlayed: return "Your top tracks by play count"
        case .recentlyAdded: return "The latest additions to your library"
        case .neverPlayed: return "Tracks you haven't started yet"
        case .notRecently: return "Played before, but not in the last 90 days"
        }
    }

    var systemImage: String {
        switch self {
        case .mostPlayed: return "chart.line.uptrend.xyaxis"
        case .recentlyAdded: return "clock.arrow.circlepath"
        case .neverPlayed: return "music.note.slash"
        case .notRecently: return "hourglass"
        }
    }

    static let notRecentlyWindowMs: Int64 = 90 * 24 * 60 * 60 * 1000
}

struct ResumeSnapshot: Codable, Equatable, Sendable {
    var trackIds: [String]
    var index: Int
    var positionMs: Int64

    var hasSession: Bool { !trackIds.isEmpty }

    static let empty = ResumeSnapshot(trackIds: [], index: 0, positionMs: 0)
}

enum ThemeMode: String, Codable, CaseIterable, Sendable, Hashable {
    case system
    case dark
    case light

    var label: String {
        switch self {
        case .system: return "System"
        case .dark: return "Dark"
        case .light: return "Light"
        }
    }
}
