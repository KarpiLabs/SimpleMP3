//
//  MusicRepository.swift
//  Simple MP3
//

import Foundation
import Observation

@Observable
@MainActor
final class MusicRepository {
    private let store = LibraryStore()
    private let preferences: AppPreferences

    private(set) var tracks: [Track] = []
    private(set) var playlists: [PlaylistMeta] = []
    private(set) var albums: [AlbumGroup] = []
    private(set) var artists: [AlbumGroup] = []
    private(set) var recentlyAdded: [Track] = []
    private(set) var continueListening: [Track] = []
    private(set) var trackCount: Int = 0
    private(set) var jellyfinCount: Int = 0
    private(set) var youtubeCount: Int = 0
    private(set) var isScanning = false
    private(set) var folderPaths: [String] = []
    private(set) var isLoaded = false
    private(set) var favoriteIds: Set<String> = []

    init(preferences: AppPreferences) {
        self.preferences = preferences
    }

    func bootstrap(onEvent: ((BootstrapEvent) -> Void)? = nil) async {
        await store.load()
        onEvent?(.storeLoaded)
        await store.ensureSystemPlaylists()
        onEvent?(.systemPlaylistsReady)
        await refresh()
        isLoaded = true

        onEvent?(.scanStarted)
        // Screenshot mode seeds a polished library; skip Media Library auth dialog.
        if !ScreenshotDemo.isEnabled {
            await scanLibrary(force: false)
        }
        onEvent?(.scanFinished(trackCount: trackCount))
        await refresh()
        onEvent?(.refreshed)
    }

    func refresh() async {
        tracks = await store.allTracks()
        playlists = await store.playlistMetas()
        albums = await store.albums()
        artists = await store.artists()
        recentlyAdded = await store.recentlyAdded(limit: 40)
        continueListening = await store.continueListening(limit: 20)
        trackCount = await store.trackCount()
        jellyfinCount = await store.count(source: .jellyfin)
        youtubeCount = await store.count(source: .youtube)
        folderPaths = await store.folderPaths()
        if let fav = await store.systemPlaylist(.favorites) {
            favoriteIds = Set(fav.trackIds)
        } else {
            favoriteIds = []
        }
        NotificationCenter.default.post(name: .libraryDidChange, object: nil)
    }

    func scanLibrary(force: Bool = true) async {
        if isScanning { return }
        if !force, trackCount > 0, preferences.shouldSkipScan() {
            return
        }
        isScanning = true
        defer { isScanning = false }

        let scanned = await MediaLibraryScanner.scan()
        let mediaPlayerTracks = scanned.filter { $0.id.hasPrefix("mp-") }
        let documentTracks = scanned.filter { !$0.id.hasPrefix("mp-") && $0.source == .local }

        let existing = await store.allTracks()
        let keepLocalFiles = existing.filter {
            $0.source == .local && !$0.id.hasPrefix("mp-")
        }

        // Prefer freshly scanned documents; keep stored local files still on disk.
        var docById = Dictionary(uniqueKeysWithValues: documentTracks.map { ($0.id, $0) })
        for t in keepLocalFiles where docById[t.id] == nil {
            if let url = t.fileURL, FileManager.default.fileExists(atPath: url.path) {
                docById[t.id] = t
            }
        }

        // Replaces local sources only; Jellyfin / YouTube / LAN rows are preserved.
        await store.replaceLocalTracks(mediaPlayerTracks + Array(docById.values))

        preferences.setLastLibraryScanMs()
        await refresh()
    }

    func search(_ query: String) async -> [Track] {
        await store.search(query)
    }

    func track(id: String) async -> Track? {
        await store.track(id: id)
    }

    func tracks(ids: [String]) async -> [Track] {
        await store.tracks(ids: ids)
    }

    func tracks(source: TrackSource) async -> [Track] {
        await store.tracks(source: source)
    }

    func tracks(album: String, artist: String?) async -> [Track] {
        await store.tracks(album: album, artist: artist)
    }

    func tracks(artist: String) async -> [Track] {
        await store.tracks(artist: artist)
    }

    func tracks(folderPath: String) async -> [Track] {
        await store.tracks(folderPath: folderPath)
    }

    func tracksForPlaylist(id: String) async -> [Track] {
        await store.tracksForPlaylist(id: id)
    }

    func playlist(id: String) async -> Playlist? {
        await store.playlist(id: id)
    }

    func systemPlaylist(_ type: SystemPlaylist) async -> Playlist? {
        await store.systemPlaylist(type)
    }

    @discardableResult
    func createPlaylist(name: String, description: String = "") async -> Playlist {
        let p = await store.createPlaylist(name: name, description: description)
        await refresh()
        return p
    }

    func renamePlaylist(id: String, name: String) async {
        await store.renamePlaylist(id: id, name: name)
        await refresh()
    }

    func deletePlaylist(id: String) async {
        await store.deletePlaylist(id: id)
        await refresh()
    }

    func addToPlaylist(playlistId: String, trackId: String) async {
        await store.addToPlaylist(playlistId: playlistId, trackId: trackId)
        await refresh()
    }

    func removeFromPlaylist(playlistId: String, trackId: String) async {
        await store.removeFromPlaylist(playlistId: playlistId, trackId: trackId)
        await refresh()
    }

    func moveTrack(playlistId: String, trackId: String, to: Int) async {
        await store.moveTrack(playlistId: playlistId, trackId: trackId, toPosition: to)
        await refresh()
    }

    func setPlaylistTrackIds(playlistId: String, trackIds: [String]) async {
        await store.setPlaylistTrackIds(playlistId: playlistId, trackIds: trackIds)
        await refresh()
    }

    @discardableResult
    func toggleFavorite(trackId: String) async -> Bool {
        let r = await store.toggleFavorite(trackId: trackId)
        await refresh()
        return r
    }

    func isFavorite(trackId: String) async -> Bool {
        await store.isFavorite(trackId: trackId)
    }

    func recordPlay(trackId: String) async {
        await store.recordPlay(trackId: trackId)
        await refresh()
    }

    func upsertTrack(_ track: Track) async {
        await store.upsert(track)
        await refresh()
    }

    func upsertTracks(_ list: [Track]) async {
        await store.upsertMany(list)
        await refresh()
    }

    func deleteTrack(id: String) async {
        if let t = await store.track(id: id), t.isAppOwned, let url = t.fileURL {
            try? FileManager.default.removeItem(at: url)
        }
        await store.deleteTrack(id: id)
        await refresh()
    }

    func mediaDirectory(for source: TrackSource) async -> URL {
        switch source {
        case .jellyfin: return await store.jellyfinDirectory()
        case .youtube: return await store.youtubeDirectory()
        case .lan: return await store.lanDirectory()
        case .local: return await store.mediaDirectory()
        }
    }

    func getLikedTracks() async -> [Track] {
        guard let p = await store.systemPlaylist(.favorites) else { return [] }
        return await store.tracksForPlaylist(id: p.id)
    }

    func getRecentlyPlayed(limit: Int = 40) async -> [Track] {
        guard let p = await store.systemPlaylist(.recentlyPlayed) else { return [] }
        return Array((await store.tracksForPlaylist(id: p.id)).prefix(limit))
    }

    func getContinueTracks() async -> [Track] {
        if let snap = preferences.resumeSnapshot, snap.hasSession {
            let ordered = await store.tracks(ids: snap.trackIds)
            if !ordered.isEmpty { return ordered }
        }
        return await getRecentlyPlayed(limit: 40)
    }
}
