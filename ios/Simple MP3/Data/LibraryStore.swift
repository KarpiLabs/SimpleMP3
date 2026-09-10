//
//  LibraryStore.swift
//  Simple MP3
//
//  JSON-backed track + playlist store (Room equivalent).
//

import Foundation

actor LibraryStore {
    private var tracks: [String: Track] = [:]
    private var playlists: [String: Playlist] = [:]
    private let fileURL: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private struct Snapshot: Codable {
        var tracks: [Track]
        var playlists: [Playlist]
    }

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let appDir = dir.appendingPathComponent("SimpleMP3", isDirectory: true)
        try? FileManager.default.createDirectory(at: appDir, withIntermediateDirectories: true)
        fileURL = appDir.appendingPathComponent("library.json")
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    }

    func load() async {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            ensureSystemPlaylists()
            persist()
            return
        }
        do {
            let data = try Data(contentsOf: fileURL)
            let snap = try decoder.decode(Snapshot.self, from: data)
            tracks = Dictionary(uniqueKeysWithValues: snap.tracks.map { ($0.id, $0) })
            playlists = Dictionary(uniqueKeysWithValues: snap.playlists.map { ($0.id, $0) })
        } catch {
            tracks = [:]
            playlists = [:]
        }
        ensureSystemPlaylists()
    }

    private func persist() {
        let snap = Snapshot(
            tracks: Array(tracks.values),
            playlists: Array(playlists.values)
        )
        guard let data = try? encoder.encode(snap) else { return }
        try? data.write(to: fileURL, options: [.atomic])
    }

    // MARK: - Tracks

    /// Non-hidden tracks only — the choke point every browse/search/playlist query filters through.
    private func visibleTracks() -> [Track] {
        tracks.values.filter { !$0.isHidden }
    }

    /// Library songs (no live streams) — All Songs / albums / artists / folders / search.
    private func librarySongs() -> [Track] {
        visibleTracks().excludingLiveStreams()
    }

    func allTracks() -> [Track] {
        librarySongs().sorted {
            $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    func hiddenTracks() -> [Track] {
        tracks.values
            .filter { $0.isHidden }
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    func setHidden(id: String, hidden: Bool) {
        guard var t = tracks[id] else { return }
        t.isHidden = hidden
        tracks[id] = t
        persist()
    }

    func track(id: String) -> Track? { tracks[id] }

    /// Persist a ReplayGain track gain (dB) parsed from file metadata.
    func setTrackGain(id: String, gainDb: Double?) {
        guard var t = tracks[id] else { return }
        t.trackGainDb = gainDb
        tracks[id] = t
        persist()
    }

    /// Remember the audio-only choice for a (video-capable) saved stream.
    func setAudioOnly(id: String, audioOnly: Bool) {
        guard var t = tracks[id] else { return }
        t.audioOnly = audioOnly
        tracks[id] = t
        persist()
    }

    func tracks(ids: [String]) -> [Track] {
        ids.compactMap { tracks[$0] }
    }

    func tracks(source: TrackSource) -> [Track] {
        visibleTracks()
            .filter { $0.source == source }
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    func recentlyAdded(limit: Int = 40) -> [Track] {
        librarySongs()
            .sorted { $0.dateAdded > $1.dateAdded }
            .prefix(limit)
            .map { $0 }
    }

    func search(_ query: String) -> [Track] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return [] }
        return visibleTracks().filter { LibrarySearch.trackMatches($0, q) }
    }

    func searchLibrary(_ query: String) -> LibrarySearchResults {
        LibrarySearch.query(
            query,
            tracks: librarySongs(),
            albums: albums(),
            artists: artists(),
            playlists: playlistMetas()
        )
    }

    func upsert(_ track: Track) {
        tracks[track.id] = track
        persist()
    }

    func upsertMany(_ list: [Track]) {
        for t in list { tracks[t.id] = t }
        persist()
    }

    func replaceLocalTracks(_ list: [Track]) {
        // Keep non-local sources intact (Jellyfin / YouTube / LAN).
        let existingLocal = tracks.filter { $0.value.source == .local }
        let keep = tracks.values.filter { $0.source != .local }
        tracks = Dictionary(uniqueKeysWithValues: keep.map { ($0.id, $0) })
        for t in list {
            if let old = existingLocal[t.id] {
                var merged = t
                merged.playCount = old.playCount
                merged.lastPlayedAt = old.lastPlayedAt
                merged.isHidden = old.isHidden
                merged.neverCompress = old.neverCompress
                merged.trackGainDb = t.trackGainDb ?? old.trackGainDb
                merged.audioOnly = old.audioOnly
                tracks[t.id] = merged
            } else {
                tracks[t.id] = t
            }
        }
        persist()
    }

    func deleteTrack(id: String) {
        tracks.removeValue(forKey: id)
        for key in playlists.keys {
            playlists[key]?.trackIds.removeAll { $0 == id }
            playlists[key]?.touch()
        }
        persist()
    }

    func deleteTracks(ids: Set<String>) {
        for id in ids { tracks.removeValue(forKey: id) }
        for key in playlists.keys {
            playlists[key]?.trackIds.removeAll { ids.contains($0) }
            playlists[key]?.touch()
        }
        persist()
    }

    func trackCount() -> Int { tracks.count }

    func count(source: TrackSource) -> Int {
        tracks.values.filter { $0.source == source }.count
    }

    func albums() -> [AlbumGroup] {
        var map: [String: (artist: String, count: Int, duration: Int64, art: String?)] = [:]
        for t in librarySongs() {
            let key = "\(t.album)|\(t.artist)"
            var cur = map[key] ?? (t.artist, 0, 0, t.artworkUri)
            cur.count += 1
            cur.duration += t.duration
            if cur.art == nil { cur.art = t.artworkUri }
            map[key] = cur
        }
        return map.map { key, v in
            let album = key.split(separator: "|", maxSplits: 1).first.map(String.init) ?? key
            return AlbumGroup(
                name: album,
                subtitle: v.artist,
                trackCount: v.count,
                totalDuration: v.duration,
                artworkUri: v.art
            )
        }
        .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    func artists() -> [AlbumGroup] {
        var map: [String: (count: Int, duration: Int64, art: String?)] = [:]
        for t in librarySongs() {
            var cur = map[t.artist] ?? (0, 0, t.artworkUri)
            cur.count += 1
            cur.duration += t.duration
            if cur.art == nil { cur.art = t.artworkUri }
            map[t.artist] = cur
        }
        return map.map { name, v in
            AlbumGroup(
                name: name,
                subtitle: "",
                trackCount: v.count,
                totalDuration: v.duration,
                artworkUri: v.art
            )
        }
        .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    func tracks(album: String, artist: String? = nil) -> [Track] {
        librarySongs()
            .filter {
                $0.album == album && (artist == nil || $0.artist == artist)
            }
            .sorted {
                if $0.trackNumber != $1.trackNumber { return $0.trackNumber < $1.trackNumber }
                return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            }
    }

    func tracks(artist: String) -> [Track] {
        librarySongs()
            .filter { $0.artist == artist }
            .sorted {
                if $0.album != $1.album {
                    return $0.album.localizedCaseInsensitiveCompare($1.album) == .orderedAscending
                }
                return $0.trackNumber < $1.trackNumber
            }
    }

    func folderPaths() -> [String] {
        Array(Set(librarySongs().map(\.folderPath).filter { !$0.isEmpty }))
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    func tracks(folderPath: String) -> [Track] {
        librarySongs()
            .filter { $0.folderPath == folderPath }
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    // MARK: - Playlists

    func ensureSystemPlaylists() {
        for sys in SystemPlaylist.allCases {
            if playlists.values.first(where: { $0.systemType == sys.rawValue }) == nil {
                let p = Playlist(
                    name: sys.displayName,
                    description: sys.detail,
                    isSystem: true,
                    systemType: sys.rawValue
                )
                playlists[p.id] = p
            }
        }
        persist()
        migrateStreamHeartsToFavoriteStreams()
    }

    /// Hearted live streams used to land in Liked Songs — move them to Favorite Streams.
    private func migrateStreamHeartsToFavoriteStreams() {
        guard var liked = systemPlaylist(.favorites),
              var dest = systemPlaylist(.favoriteStreams) else { return }
        let streamIds = liked.trackIds.filter { tracks[$0]?.source == .stream }
        guard !streamIds.isEmpty else { return }
        for id in streamIds where !dest.trackIds.contains(id) {
            dest.trackIds.append(id)
        }
        liked.trackIds.removeAll { streamIds.contains($0) }
        liked.touch()
        dest.touch()
        playlists[liked.id] = liked
        playlists[dest.id] = dest
        persist()
    }

    func allPlaylists() -> [Playlist] {
        playlists.values.sorted {
            if $0.isSystem != $1.isSystem { return $0.isSystem && !$1.isSystem }
            return $0.updatedAt > $1.updatedAt
        }
    }

    func playlistMetas() -> [PlaylistMeta] {
        allPlaylists().map { p in
            let firstId = p.trackIds.first
            let firstArt = firstId.flatMap { tracks[$0]?.artworkUri }
                ?? firstId.flatMap { id -> String? in
                    id.hasPrefix("mp-") ? "mpmedia://\(id.dropFirst(3))" : nil
                }
            return PlaylistMeta(
                id: p.id,
                name: p.name,
                description: p.description,
                coverUri: p.coverUri,
                createdAt: p.createdAt,
                updatedAt: p.updatedAt,
                isSystem: p.isSystem,
                systemType: p.systemType,
                trackCount: p.trackIds.count,
                firstArtworkUri: firstArt
            )
        }
    }

    func playlist(id: String) -> Playlist? { playlists[id] }

    func systemPlaylist(_ type: SystemPlaylist) -> Playlist? {
        playlists.values.first { $0.systemType == type.rawValue }
    }

    func tracksForPlaylist(id: String) -> [Track] {
        guard let p = playlists[id] else { return [] }
        let list = p.trackIds.compactMap { tracks[$0] }.filter { !$0.isHidden }
        if p.systemType == SystemPlaylist.favorites.rawValue {
            return list.excludingLiveStreams()
        }
        return list
    }

    @discardableResult
    func createPlaylist(name: String, description: String = "") -> Playlist {
        let p = Playlist(name: name.trimmingCharacters(in: .whitespacesAndNewlines), description: description)
        playlists[p.id] = p
        persist()
        return p
    }

    func renamePlaylist(id: String, name: String) {
        guard var p = playlists[id], !p.isSystem else { return }
        p.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        p.touch()
        playlists[id] = p
        persist()
    }

    func deletePlaylist(id: String) {
        guard let p = playlists[id], !p.isSystem else { return }
        playlists.removeValue(forKey: id)
        persist()
    }

    func addToPlaylist(playlistId: String, trackId: String) {
        guard var p = playlists[playlistId] else { return }
        guard tracks[trackId] != nil else { return }
        if !p.trackIds.contains(trackId) {
            p.trackIds.append(trackId)
            p.touch()
            playlists[playlistId] = p
            persist()
        }
    }

    func removeFromPlaylist(playlistId: String, trackId: String) {
        guard var p = playlists[playlistId] else { return }
        p.trackIds.removeAll { $0 == trackId }
        p.touch()
        playlists[playlistId] = p
        persist()
    }

    func setPlaylistTrackIds(playlistId: String, trackIds: [String]) {
        guard var p = playlists[playlistId] else { return }
        if p.systemType == SystemPlaylist.favorites.rawValue {
            let keptStreams = p.trackIds.filter { id in
                tracks[id]?.source == .stream && !trackIds.contains(id)
            }
            p.trackIds = trackIds + keptStreams
        } else {
            p.trackIds = trackIds
        }
        p.touch()
        playlists[playlistId] = p
        persist()
    }

    func moveTrack(playlistId: String, trackId: String, toPosition: Int) {
        guard var p = playlists[playlistId] else { return }
        guard let from = p.trackIds.firstIndex(of: trackId) else { return }
        let item = p.trackIds.remove(at: from)
        let target = min(max(0, toPosition), p.trackIds.count)
        p.trackIds.insert(item, at: target)
        p.touch()
        playlists[playlistId] = p
        persist()
    }

    @discardableResult
    func toggleFavorite(trackId: String) -> Bool {
        ensureSystemPlaylists()
        let isStream = tracks[trackId]?.source == .stream
        let type: SystemPlaylist = isStream ? .favoriteStreams : .favorites
        guard var fav = systemPlaylist(type) else { return false }
        if let idx = fav.trackIds.firstIndex(of: trackId) {
            fav.trackIds.remove(at: idx)
            fav.touch()
            playlists[fav.id] = fav
            persist()
            return false
        } else {
            fav.trackIds.append(trackId)
            fav.touch()
            playlists[fav.id] = fav
            persist()
            return true
        }
    }

    func isFavorite(trackId: String) -> Bool {
        let isStream = tracks[trackId]?.source == .stream
        let type: SystemPlaylist = isStream ? .favoriteStreams : .favorites
        return systemPlaylist(type)?.trackIds.contains(trackId) ?? false
    }

    func recordPlay(trackId: String) {
        ensureSystemPlaylists()
        guard var recent = systemPlaylist(.recentlyPlayed) else { return }
        recent.trackIds.removeAll { $0 == trackId }
        recent.trackIds.insert(trackId, at: 0)
        if recent.trackIds.count > 50 {
            recent.trackIds = Array(recent.trackIds.prefix(50))
        }
        recent.touch()
        playlists[recent.id] = recent
        if var t = tracks[trackId] {
            t.lastPlayedAt = Int64(Date().timeIntervalSince1970 * 1000)
            t.playCount += 1
            tracks[trackId] = t
        }
        persist()
    }

    // MARK: - Smart playlists

    func mostPlayed(limit: Int = 100) -> [Track] {
        visibleTracks()
            .excludingLiveStreams()
            .filter { $0.playCount > 0 }
            .sorted {
                if $0.playCount != $1.playCount { return $0.playCount > $1.playCount }
                return $0.lastPlayedAt > $1.lastPlayedAt
            }
            .prefix(limit)
            .map { $0 }
    }

    func neverPlayed(limit: Int = 500) -> [Track] {
        librarySongs()
            .filter { $0.playCount == 0 }
            .sorted { $0.dateAdded > $1.dateAdded }
            .prefix(limit)
            .map { $0 }
    }

    func notRecentlyPlayed(limit: Int = 200) -> [Track] {
        let cutoff = Int64(Date().timeIntervalSince1970 * 1000) - SmartPlaylist.notRecentlyWindowMs
        return librarySongs()
            .filter { $0.playCount > 0 && $0.lastPlayedAt > 0 && $0.lastPlayedAt < cutoff }
            .sorted { $0.lastPlayedAt < $1.lastPlayedAt }
            .prefix(limit)
            .map { $0 }
    }

    func smartTracks(_ smart: SmartPlaylist, limit: Int = 100) -> [Track] {
        switch smart {
        case .mostPlayed: return mostPlayed(limit: limit)
        case .recentlyAdded: return recentlyAdded(limit: limit)
        case .neverPlayed: return neverPlayed(limit: max(limit, 500))
        case .notRecently: return notRecentlyPlayed(limit: max(limit, 200))
        }
    }

    func duplicateGroups() -> [DuplicateDetector.Group] {
        DuplicateDetector.findGroups(librarySongs())
    }

    func hideTracks(_ ids: [String], hidden: Bool) {
        for id in ids {
            guard var t = tracks[id] else { continue }
            t.isHidden = hidden
            tracks[id] = t
        }
        persist()
    }

    func mergeDuplicateGroup(_ group: DuplicateDetector.Group, keepId: String? = nil) {
        let keep = keepId ?? group.keepId
        let extras = group.tracks.filter { $0.id != keep }
        for extra in extras {
            for (pid, playlist) in playlists {
                guard playlist.trackIds.contains(extra.id) else { continue }
                var p = playlist
                if !p.trackIds.contains(keep) {
                    if let idx = p.trackIds.firstIndex(of: extra.id) {
                        p.trackIds[idx] = keep
                    } else {
                        p.trackIds.append(keep)
                    }
                } else {
                    p.trackIds.removeAll { $0 == extra.id }
                }
                p.touch()
                playlists[pid] = p
            }
        }
        hideTracks(extras.map(\.id), hidden: true)
    }

    func addTracksToPlaylist(playlistId: String, trackIds: [String]) {
        guard var p = playlists[playlistId] else { return }
        var changed = false
        for id in trackIds where tracks[id] != nil && !p.trackIds.contains(id) {
            p.trackIds.append(id)
            changed = true
        }
        if changed {
            p.touch()
            playlists[playlistId] = p
            persist()
        }
    }

    /// Distinct genres with counts, mirroring `albums()` / `artists()`.
    func genres() -> [AlbumGroup] {
        var map: [String: (count: Int, duration: Int64, art: String?)] = [:]
        for t in visibleTracks() where t.source != .stream {
            guard let g = t.genre, !g.isEmpty else { continue }
            var cur = map[g] ?? (0, 0, t.artworkUri)
            cur.count += 1
            cur.duration += t.duration
            if cur.art == nil { cur.art = t.artworkUri }
            map[g] = cur
        }
        return map.map { name, v in
            AlbumGroup(
                name: name,
                subtitle: "",
                trackCount: v.count,
                totalDuration: v.duration,
                artworkUri: v.art
            )
        }
        .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    func tracks(genre: String) -> [Track] {
        visibleTracks()
            .filter { $0.source != .stream && $0.genre == genre }
            .sorted {
                if $0.artist != $1.artist {
                    return $0.artist.localizedCaseInsensitiveCompare($1.artist) == .orderedAscending
                }
                if $0.album != $1.album {
                    return $0.album.localizedCaseInsensitiveCompare($1.album) == .orderedAscending
                }
                return $0.trackNumber < $1.trackNumber
            }
    }

    func continueListening(limit: Int = 20) -> [Track] {
        guard let recent = systemPlaylist(.recentlyPlayed) else { return [] }
        return Array(recent.trackIds.prefix(limit).compactMap { tracks[$0] })
    }

    func mediaDirectory() -> URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            .appendingPathComponent("Media", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func jellyfinDirectory() -> URL {
        let dir = mediaDirectory().appendingPathComponent("Jellyfin", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func youtubeDirectory() -> URL {
        let dir = mediaDirectory().appendingPathComponent("YouTube", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func lanDirectory() -> URL {
        let dir = mediaDirectory().appendingPathComponent("LAN", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func streamDirectory() -> URL {
        let dir = mediaDirectory().appendingPathComponent("Streams", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}
