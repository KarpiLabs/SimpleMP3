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
