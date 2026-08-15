//
//  Track.swift
//  Simple MP3
//
//  Port of Android TrackEntity — local / Jellyfin / YouTube / LAN sources.
//

import Foundation

enum TrackSource: String, Codable, CaseIterable, Sendable {
    case local
    case jellyfin
    case youtube
    case lan
}

enum StorageState: String, Codable, Sendable {
    case hot
    case cold
}

struct Track: Identifiable, Codable, Hashable, Sendable {
    var id: String
    var title: String
    var artist: String
    var album: String
    var albumId: String
    var artistId: String
    /// File URL (file://) or remote stream URL.
    var uri: String
    /// Duration in milliseconds.
    var duration: Int64
    var artworkUri: String?
    var dateAdded: Int64
    var year: Int
    var trackNumber: Int
    var genre: String?
    var folderPath: String
    var size: Int64
    var source: TrackSource
    /// Jellyfin item GUID or YouTube video id.
    var externalId: String?
    var isOffline: Bool
    var storageState: StorageState
    var coldUri: String?
    var isSizeOptimized: Bool
    var lastPlayedAt: Int64
    var neverCompress: Bool

    init(
        id: String = UUID().uuidString,
        title: String,
        artist: String = "Unknown Artist",
        album: String = "Unknown Album",
        albumId: String = "",
        artistId: String = "",
        uri: String,
        duration: Int64 = 0,
        artworkUri: String? = nil,
        dateAdded: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        year: Int = 0,
        trackNumber: Int = 0,
        genre: String? = nil,
        folderPath: String = "",
        size: Int64 = 0,
        source: TrackSource = .local,
        externalId: String? = nil,
        isOffline: Bool = true,
        storageState: StorageState = .hot,
        coldUri: String? = nil,
        isSizeOptimized: Bool = false,
        lastPlayedAt: Int64 = 0,
        neverCompress: Bool = false
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.album = album
        self.albumId = albumId
        self.artistId = artistId
        self.uri = uri
        self.duration = duration
        self.artworkUri = artworkUri
        self.dateAdded = dateAdded
        self.year = year
        self.trackNumber = trackNumber
        self.genre = genre
        self.folderPath = folderPath
        self.size = size
        self.source = source
        self.externalId = externalId
        self.isOffline = isOffline
        self.storageState = storageState
        self.coldUri = coldUri
        self.isSizeOptimized = isSizeOptimized
        self.lastPlayedAt = lastPlayedAt
        self.neverCompress = neverCompress
    }

    var durationSeconds: Double { Double(duration) / 1000.0 }

    var fileURL: URL? {
        if uri.hasPrefix("file://") {
            return URL(string: uri)
        }
        if uri.hasPrefix("/") {
            return URL(fileURLWithPath: uri)
        }
        return URL(string: uri)
    }

    var isAppOwned: Bool {
        source == .jellyfin || source == .youtube || source == .lan
    }

    var isCold: Bool { storageState == .cold }
}

struct AlbumGroup: Identifiable, Hashable, Sendable {
    var id: String { "\(name)|\(subtitle)" }
    let name: String
    let subtitle: String
    let trackCount: Int
    let totalDuration: Int64
    let artworkUri: String?
}

nonisolated struct PlaylistMeta: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let description: String
    let coverUri: String?
    let createdAt: Int64
    let updatedAt: Int64
    let isSystem: Bool
    let systemType: String?
    let trackCount: Int
    let firstArtworkUri: String?

    var displayCover: String? { coverUri ?? firstArtworkUri }

    var acceptsManualAdds: Bool {
        if !isSystem { return true }
        return systemType == SystemPlaylist.favorites.rawValue
    }
}

nonisolated enum SystemPlaylist: String, CaseIterable, Sendable {
    case favorites
    case recentlyPlayed = "recently_played"
    case jellyfinOffline = "jellyfin_offline"
    case youtubeDownloads = "youtube_downloads"
    case lanImports = "lan_imports"

    var displayName: String {
        switch self {
        case .favorites: return "Liked Songs"
        case .recentlyPlayed: return "Recently Played"
        case .jellyfinOffline: return "Jellyfin Offline"
        case .youtubeDownloads: return "Imported Audio"
        case .lanImports: return "LAN Imports"
        }
    }

    var detail: String {
        switch self {
        case .favorites: return "Your favorite tracks"
        case .recentlyPlayed: return "Jump back in"
        case .jellyfinOffline: return "Synced from your Jellyfin server"
        case .youtubeDownloads: return "Imported audio files"
        case .lanImports: return "Uploaded via Quick Connect"
        }
    }
}
