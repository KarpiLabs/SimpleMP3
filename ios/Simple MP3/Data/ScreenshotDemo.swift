//
//  ScreenshotDemo.swift
//  Simple MP3
//
//  Seeds a polished library + playback state when launched with
//  -ScreenshotDemo (and optional -ScreenshotScene <name>).
//  Used only for App Store / marketing screenshot capture.
//

import Foundation
import UIKit

enum ScreenshotScene: String, CaseIterable, Sendable {
    case home
    case playlists
    case nowplaying
    case drive
    case library
    case search
    case tools
    case jellyfin
    case carplay
}

enum ScreenshotDemo {
    static var isEnabled: Bool {
        ProcessInfo.processInfo.arguments.contains("-ScreenshotDemo")
    }

    static var scene: ScreenshotScene {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-ScreenshotScene"),
              args.indices.contains(i + 1),
              let scene = ScreenshotScene(rawValue: args[i + 1])
        else {
            return .home
        }
        return scene
    }

    @MainActor
    static func seed(into app: AppModel) async {
        let artDir = demoArtDirectory()
        try? FileManager.default.createDirectory(at: artDir, withIntermediateDirectories: true)

        let silentURL = makeSilentAudioIfNeeded()
        let tracks = makeTracks(artDir: artDir, audioURL: silentURL)

        await app.repository.upsertTracks(tracks)

        // Favorites / recently played / jellyfin offline system playlists
        if let fav = await app.repository.systemPlaylist(.favorites) {
            for id in tracks.prefix(6).map(\.id) {
                await app.repository.addToPlaylist(playlistId: fav.id, trackId: id)
            }
        }
        if let recent = await app.repository.systemPlaylist(.recentlyPlayed) {
            for id in tracks.prefix(5).map(\.id) {
                await app.repository.addToPlaylist(playlistId: recent.id, trackId: id)
            }
        }
        if let jf = await app.repository.systemPlaylist(.jellyfinOffline) {
            for t in tracks where t.source == .jellyfin {
                await app.repository.addToPlaylist(playlistId: jf.id, trackId: t.id)
            }
        }
        if let yt = await app.repository.systemPlaylist(.youtubeDownloads) {
            for t in tracks where t.source == .youtube {
                await app.repository.addToPlaylist(playlistId: yt.id, trackId: t.id)
            }
        }

        // User playlists
        let roadTrip = await app.repository.createPlaylist(
            name: "Road Trip Mix",
            description: "Highway-ready anthems"
        )
        for id in tracks.prefix(8).map(\.id) {
            await app.repository.addToPlaylist(playlistId: roadTrip.id, trackId: id)
        }
        let focus = await app.repository.createPlaylist(
            name: "Focus Hours",
            description: "Deep work energy"
        )
        for id in tracks.dropFirst(3).prefix(6).map(\.id) {
            await app.repository.addToPlaylist(playlistId: focus.id, trackId: id)
        }
        let lateNight = await app.repository.createPlaylist(
            name: "Late Night",
            description: "Neon after midnight"
        )
        for id in [tracks[0].id, tracks[2].id, tracks[4].id, tracks[6].id] {
            await app.repository.addToPlaylist(playlistId: lateNight.id, trackId: id)
        }

        // Preferences that look good in shots
        app.preferences.themeMode = .dark
        app.preferences.jellyfinEnabled = true
        app.preferences.driveMode = false
        app.preferences.resumeEnabled = true
        app.preferences.jellyfinServerUrl = "http://192.168.1.50:8096"
        app.preferences.jellyfinUser = "alex"
        app.preferences.jellyfinToken = "demo-token-screenshot"
        app.preferences.jellyfinUserId = "demo-user-id"
        app.preferences.saveResume(
            ResumeSnapshot(
                trackIds: tracks.map(\.id),
                index: 0,
                positionMs: 84_000
            )
        )

        app.jellyfin.applyDemoSession(tracks: tracks.filter { $0.source == .jellyfin })

        await app.repository.refresh()

        // Present now-playing UI without relying on AVPlayer network/file quirks
        let queue = Array(tracks.prefix(10))
        if let current = queue.first {
            app.player.presentDemoState(
                track: current,
                queue: queue,
                positionMs: 84_000,
                isPlaying: true
            )
        }

        applyScene(scene, app: app)
    }

    @MainActor
    static func applyScene(_ scene: ScreenshotScene, app: AppModel) {
        app.preferences.driveMode = false
        app.showNowPlaying = false
        app.screenshotOverlay = nil

        switch scene {
        case .home:
            app.selectedTab = .home
        case .playlists:
            app.selectedTab = .playlists
        case .library:
            app.selectedTab = .library
        case .search:
            app.selectedTab = .search
            app.searchQuery = "Nova"
            Task { await app.updateSearch() }
        case .tools:
            app.selectedTab = .tools
        case .jellyfin:
            app.selectedTab = .tools
            app.screenshotOverlay = .jellyfin
        case .nowplaying:
            app.selectedTab = .home
            app.showNowPlaying = true
        case .drive:
            app.selectedTab = .home
            app.preferences.driveMode = true
        case .carplay:
            // CarPlay is a separate scene; show Drive Mode as the in-app car story.
            app.selectedTab = .home
            app.preferences.driveMode = true
            app.screenshotOverlay = .carplay
        }
    }

    // MARK: - Data

    private static func demoArtDirectory() -> URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("ScreenshotDemo/art", isDirectory: true)
    }

    private static func makeSilentAudioIfNeeded() -> URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent("ScreenshotDemo", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let out = dir.appendingPathComponent("silent.caf")
        if !FileManager.default.fileExists(atPath: out.path) {
            // Tiny valid CAF (empty/near-silent) is optional; demo playback sets UI state directly.
            FileManager.default.createFile(atPath: out.path, contents: Data(), attributes: nil)
        }
        return out
    }

    private static func makeTracks(artDir: URL, audioURL: URL) -> [Track] {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let specs: [(title: String, artist: String, album: String, duration: Int64, source: TrackSource, colors: [UIColor])] = [
            ("Midnight Drive", "Nova Lane", "Night Roads", 222_000, .local, [
                UIColor(red: 0.05, green: 0.25, blue: 0.22, alpha: 1),
                UIColor(red: 0.0, green: 0.9, blue: 0.75, alpha: 1)
            ]),
            ("Neon Skyline", "Echo Park", "City Lights", 245_000, .local, [
                UIColor(red: 0.18, green: 0.08, blue: 0.35, alpha: 1),
                UIColor(red: 0.65, green: 0.55, blue: 0.98, alpha: 1)
            ]),
            ("Coastline", "Atlas", "Salt Air", 198_000, .jellyfin, [
                UIColor(red: 0.05, green: 0.15, blue: 0.35, alpha: 1),
                UIColor(red: 0.2, green: 0.55, blue: 0.95, alpha: 1)
            ]),
            ("Static Bloom", "Kite", "Greenhouse", 214_000, .jellyfin, [
                UIColor(red: 0.25, green: 0.08, blue: 0.12, alpha: 1),
                UIColor(red: 1.0, green: 0.42, blue: 0.42, alpha: 1)
            ]),
            ("Glass Harbor", "Northbound", "Ports", 187_000, .jellyfin, [
                UIColor(red: 0.08, green: 0.18, blue: 0.2, alpha: 1),
                UIColor(red: 0.4, green: 0.85, blue: 0.9, alpha: 1)
            ]),
            ("Low Battery", "Vesper", "After Hours", 231_000, .local, [
                UIColor(red: 0.15, green: 0.1, blue: 0.05, alpha: 1),
                UIColor(red: 1.0, green: 0.82, blue: 0.4, alpha: 1)
            ]),
            ("Paper Planes", "Juniper", "Folded Sky", 176_000, .youtube, [
                UIColor(red: 0.2, green: 0.05, blue: 0.08, alpha: 1),
                UIColor(red: 0.95, green: 0.25, blue: 0.3, alpha: 1)
            ]),
            ("Signal Lost", "Relay", "Frequencies", 203_000, .local, [
                UIColor(red: 0.05, green: 0.05, blue: 0.12, alpha: 1),
                UIColor(red: 0.3, green: 0.75, blue: 0.65, alpha: 1)
            ]),
            ("Copper Dawn", "Maris", "Horizon EP", 255_000, .local, [
                UIColor(red: 0.25, green: 0.12, blue: 0.05, alpha: 1),
                UIColor(red: 0.95, green: 0.55, blue: 0.2, alpha: 1)
            ]),
            ("Quiet Engine", "Solace", "Idle Mode", 268_000, .local, [
                UIColor(red: 0.08, green: 0.12, blue: 0.18, alpha: 1),
                UIColor(red: 0.45, green: 0.55, blue: 0.7, alpha: 1)
            ]),
            ("Violet Hour", "Lumen", "Dusk Collection", 192_000, .jellyfin, [
                UIColor(red: 0.15, green: 0.05, blue: 0.28, alpha: 1),
                UIColor(red: 0.7, green: 0.4, blue: 0.95, alpha: 1)
            ]),
            ("Open Road", "Kite", "Greenhouse", 210_000, .local, [
                UIColor(red: 0.05, green: 0.2, blue: 0.12, alpha: 1),
                UIColor(red: 0.2, green: 0.85, blue: 0.55, alpha: 1)
            ])
        ]

        return specs.enumerated().map { index, s in
            let artURL = artDir.appendingPathComponent("art-\(index).png")
            if !FileManager.default.fileExists(atPath: artURL.path) {
                writeGradientArt(to: artURL, colors: s.colors, glyph: String(s.title.prefix(1)))
            }
            return Track(
                id: "demo-\(index)",
                title: s.title,
                artist: s.artist,
                album: s.album,
                albumId: "album-\(s.album.hashValue)",
                artistId: "artist-\(s.artist.hashValue)",
                uri: audioURL.absoluteString,
                duration: s.duration,
                artworkUri: artURL.absoluteString,
                dateAdded: now - Int64(index) * 86_400_000,
                year: 2025,
                trackNumber: index + 1,
                genre: "Electronic",
                folderPath: s.source == .jellyfin ? "Jellyfin/Offline" : "Music/Demo",
                size: 4_200_000,
                source: s.source,
                externalId: s.source == .jellyfin ? "jf-\(index)" : nil,
                isOffline: true,
                lastPlayedAt: now - Int64(index) * 3_600_000
            )
        }
    }

    private static func writeGradientArt(to url: URL, colors: [UIColor], glyph: String) {
        let size = CGSize(width: 512, height: 512)
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { ctx in
            let cg = ctx.cgContext
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            let cgColors = colors.map(\.cgColor) as CFArray
            if let gradient = CGGradient(colorsSpace: colorSpace, colors: cgColors, locations: [0, 1]) {
                cg.drawLinearGradient(
                    gradient,
                    start: CGPoint(x: 0, y: 0),
                    end: CGPoint(x: size.width, y: size.height),
                    options: []
                )
            }
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 160, weight: .bold),
                .foregroundColor: UIColor.white.withAlphaComponent(0.22)
            ]
            let str = glyph as NSString
            let textSize = str.size(withAttributes: attrs)
            str.draw(
                at: CGPoint(x: (size.width - textSize.width) / 2, y: (size.height - textSize.height) / 2),
                withAttributes: attrs
            )
            // Teal note accent
            let note = "♪" as NSString
            let noteAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 72, weight: .semibold),
                .foregroundColor: UIColor(red: 0, green: 0.9, blue: 0.75, alpha: 0.85)
            ]
            let noteSize = note.size(withAttributes: noteAttrs)
            note.draw(
                at: CGPoint(x: size.width - noteSize.width - 36, y: size.height - noteSize.height - 32),
                withAttributes: noteAttrs
            )
        }
        if let data = image.pngData() {
            try? data.write(to: url, options: .atomic)
        }
    }
}

enum ScreenshotOverlay: String, Identifiable, Sendable {
    case jellyfin
    case carplay

    var id: String { rawValue }
}
