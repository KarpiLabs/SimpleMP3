//
//  JellyfinService.swift
//  Simple MP3
//

import Foundation
import Observation

@Observable
@MainActor
final class JellyfinService {
    private let client = JellyfinClient()
    private let preferences: AppPreferences
    private let repository: MusicRepository

    private(set) var session: JellyfinSession?
    private(set) var remoteItems: [JellyfinItem] = []
    private(set) var remoteAlbums: [JellyfinItem] = []
    private(set) var isLoading = false
    private(set) var statusMessage: String?
    private(set) var syncProgress = SyncProgress()
    /// When true, network library refreshes are skipped (screenshot demo).
    private(set) var isDemoSession = false

    init(preferences: AppPreferences, repository: MusicRepository) {
        self.preferences = preferences
        self.repository = repository
        if preferences.isJellyfinLoggedIn {
            session = JellyfinSession(
                serverUrl: preferences.jellyfinServerUrl,
                accessToken: preferences.jellyfinToken,
                userId: preferences.jellyfinUserId,
                userName: preferences.jellyfinUser,
                serverId: nil,
                deviceId: preferences.jellyfinDeviceId
            )
        }
    }

    var isLoggedIn: Bool { session != nil }

    /// Fake signed-in server + offline-looking library for App Store screenshots.
    func applyDemoSession(tracks: [Track]) {
        isDemoSession = true
        session = JellyfinSession(
            serverUrl: preferences.jellyfinServerUrl.isEmpty
                ? "http://192.168.1.50:8096"
                : preferences.jellyfinServerUrl,
            accessToken: preferences.jellyfinToken.isEmpty ? "demo-token" : preferences.jellyfinToken,
            userId: preferences.jellyfinUserId.isEmpty ? "demo-user" : preferences.jellyfinUserId,
            userName: preferences.jellyfinUser.isEmpty ? "alex" : preferences.jellyfinUser,
            serverId: "demo-server",
            deviceId: preferences.jellyfinDeviceId
        )
        preferences.jellyfinEnabled = true
        remoteItems = tracks.map { t in
            JellyfinItem.demo(
                id: t.externalId ?? t.id,
                title: t.title,
                artist: t.artist,
                album: t.album,
                durationMs: t.duration
            )
        }
        if remoteItems.isEmpty {
            remoteItems = [
                .demo(id: "jf-1", title: "Coastline", artist: "Atlas", album: "Salt Air", durationMs: 198_000),
                .demo(id: "jf-2", title: "Static Bloom", artist: "Kite", album: "Greenhouse", durationMs: 214_000),
                .demo(id: "jf-3", title: "Glass Harbor", artist: "Northbound", album: "Ports", durationMs: 187_000),
                .demo(id: "jf-4", title: "Violet Hour", artist: "Lumen", album: "Dusk Collection", durationMs: 192_000)
            ]
        }
        remoteAlbums = [
            .demo(id: "alb-1", title: "Salt Air", artist: "Atlas", album: "Salt Air", durationMs: 0),
            .demo(id: "alb-2", title: "Greenhouse", artist: "Kite", album: "Greenhouse", durationMs: 0),
            .demo(id: "alb-3", title: "Ports", artist: "Northbound", album: "Ports", durationMs: 0)
        ]
        statusMessage = "Home Media · offline ready"
    }

    func login(serverUrl: String, username: String, password: String) async {
        isLoading = true
        statusMessage = nil
        defer { isLoading = false }
        do {
            let s = try await client.authenticate(
                serverUrl: serverUrl,
                username: username,
                password: password,
                deviceId: preferences.jellyfinDeviceId
            )
            session = s
            preferences.jellyfinServerUrl = s.serverUrl
            preferences.jellyfinUser = s.userName
            preferences.jellyfinToken = s.accessToken
            preferences.jellyfinUserId = s.userId
            preferences.jellyfinEnabled = true
            statusMessage = "Signed in as \(s.userName)"
            await loadLibrary()
        } catch {
            statusMessage = error.localizedDescription
            session = nil
        }
    }

    func logout() {
        session = nil
        preferences.clearJellyfinSession()
        remoteItems = []
        remoteAlbums = []
        statusMessage = "Signed out"
    }

    func loadLibrary() async {
        guard let session else { return }
        if isDemoSession {
            statusMessage = "Home Media · offline ready"
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            async let audio = client.getAudioItems(session: session, limit: 300)
            async let albums = client.getAlbums(session: session, limit: 200)
            let (a, al) = try await (audio, albums)
            remoteItems = a.Items ?? []
            remoteAlbums = al.Items ?? []
            statusMessage = "\(remoteItems.count) tracks on server"
        } catch {
            statusMessage = error.localizedDescription
        }
    }

    func search(_ query: String) async {
        guard let session else { return }
        if isDemoSession { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let result = try await client.getAudioItems(session: session, limit: 100, searchTerm: query)
            remoteItems = result.Items ?? []
        } catch {
            statusMessage = error.localizedDescription
        }
    }

    func downloadItem(_ item: JellyfinItem) async {
        guard let session else { return }
        syncProgress = SyncProgress(
            phase: "Downloading",
            current: 0,
            total: 1,
            currentTitle: item.title,
            isActive: true
        )
        do {
            let dir = await repository.mediaDirectory(for: .jellyfin)
            let fileURL = try await client.download(session: session, item: item, to: dir)
            var track = await MediaLibraryScanner.metadataTrack(
                from: fileURL,
                source: .jellyfin,
                externalId: item.Id
            ) ?? Track(
                id: "jellyfin-\(item.Id)",
                title: item.title,
                artist: item.artistName,
                album: item.albumName,
                uri: fileURL.absoluteString,
                duration: item.durationMs,
                source: .jellyfin,
                externalId: item.Id
            )
            track.source = .jellyfin
            track.externalId = item.Id
            track.artist = item.artistName
            track.album = item.albumName
            track.title = item.title
            track.duration = item.durationMs
            track.trackNumber = item.IndexNumber ?? 0
            track.year = item.ProductionYear ?? 0
            await repository.upsertTrack(track)
            if let pl = await repository.systemPlaylist(.jellyfinOffline) {
                await repository.addToPlaylist(playlistId: pl.id, trackId: track.id)
            }
            syncProgress = SyncProgress(
                phase: "Done",
                current: 1,
                total: 1,
                currentTitle: item.title,
                isActive: false,
                lastResult: "Saved \(item.title)"
            )
        } catch {
            syncProgress = SyncProgress(
                phase: "Error",
                isActive: false,
                error: error.localizedDescription
            )
        }
    }

    func downloadAllVisible() async {
        guard session != nil else { return }
        let items = remoteItems
        guard !items.isEmpty else { return }
        syncProgress = SyncProgress(phase: "Syncing", current: 0, total: items.count, isActive: true)
        for (i, item) in items.enumerated() {
            syncProgress.current = i
            syncProgress.currentTitle = item.title
            await downloadItem(item)
            syncProgress.current = i + 1
            syncProgress.total = items.count
            syncProgress.isActive = true
            syncProgress.phase = "Syncing"
        }
        syncProgress.isActive = false
        syncProgress.phase = "Done"
        syncProgress.lastResult = "Downloaded \(items.count) tracks"
    }

    func playRemote(_ item: JellyfinItem, player: PlaybackManager) async {
        // Prefer offline copy
        let offline = await repository.tracks(source: .jellyfin)
        if let local = offline.first(where: { $0.externalId == item.Id }) {
            player.play(local, queue: offline)
            return
        }
        guard let session, let url = await client.streamURL(session: session, itemId: item.Id) else {
            statusMessage = "Cannot stream track"
            return
        }
        let track = Track(
            id: "stream-\(item.Id)",
            title: item.title,
            artist: item.artistName,
            album: item.albumName,
            uri: url.absoluteString,
            duration: item.durationMs,
            source: .jellyfin,
            externalId: item.Id,
            isOffline: false
        )
        player.play(track)
    }
}
