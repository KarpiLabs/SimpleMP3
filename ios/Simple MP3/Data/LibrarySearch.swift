//
//  LibrarySearch.swift
//  Simple MP3
//

import Foundation

struct LibrarySearchResults: Sendable {
    var tracks: [Track] = []
    var albums: [AlbumGroup] = []
    var artists: [AlbumGroup] = []
    var playlists: [PlaylistMeta] = []

    var isEmpty: Bool { tracks.isEmpty && albums.isEmpty && artists.isEmpty && playlists.isEmpty }
}

enum LibrarySearch {
    static func query(
        _ raw: String,
        tracks: [Track],
        albums: [AlbumGroup],
        artists: [AlbumGroup],
        playlists: [PlaylistMeta]
    ) -> LibrarySearchResults {
        let q = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return LibrarySearchResults() }
        return LibrarySearchResults(
            tracks: tracks.filter { trackMatches($0, q) },
            albums: albums.filter { collectionMatches($0, q) },
            artists: artists.filter { collectionMatches($0, q) },
            playlists: playlists.filter { playlistMatches($0, q) }
        )
    }

    static func trackMatches(_ track: Track, _ query: String) -> Bool {
        track.title.localizedCaseInsensitiveContains(query)
            || track.artist.localizedCaseInsensitiveContains(query)
            || track.album.localizedCaseInsensitiveContains(query)
            || (track.genre?.localizedCaseInsensitiveContains(query) ?? false)
    }

    static func collectionMatches(_ row: AlbumGroup, _ query: String) -> Bool {
        row.name.localizedCaseInsensitiveContains(query)
            || row.subtitle.localizedCaseInsensitiveContains(query)
    }

    static func playlistMatches(_ playlist: PlaylistMeta, _ query: String) -> Bool {
        playlist.name.localizedCaseInsensitiveContains(query)
            || playlist.description.localizedCaseInsensitiveContains(query)
    }
}
