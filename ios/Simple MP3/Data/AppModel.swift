//
//  AppModel.swift
//  Simple MP3
//
//  Shared app graph for SwiftUI + CarPlay.
//

import Foundation
import Observation
import SwiftUI
import UIKit

@Observable
@MainActor
final class AppModel {
    static let shared = AppModel()

    let preferences: AppPreferences
    let repository: MusicRepository
    let player: PlaybackManager
    let jellyfin: JellyfinService
    let quickConnect: QuickConnectServer

    var searchQuery: String = ""
    var searchResults: [Track] = []
    var showNowPlaying = false
    var showQueue = false
    var addToPlaylistTrack: Track?
    var selectedTab: AppTab = .home
    /// Forced full-screen destination for App Store screenshot capture.
    var screenshotOverlay: ScreenshotOverlay?

    /// Launch experience state (splash screen).
    private(set) var launchPhase: LaunchPhase = .starting
    private(set) var launchStatus: String = "Warming up…"
    private(set) var launchProgress: Double = 0.05

    private init() {
        let prefs = AppPreferences()
        let repo = MusicRepository(preferences: prefs)
        let playback = PlaybackManager()
        playback.attach(repository: repo, preferences: prefs)
        preferences = prefs
        repository = repo
        player = playback
        jellyfin = JellyfinService(preferences: prefs, repository: repo)
        quickConnect = QuickConnectServer(repository: repo)
    }

    func bootstrap() async {
        let minSplash = Task {
            // Keep splash visible long enough for the animation to read as intentional.
            try? await Task.sleep(for: .milliseconds(900))
        }

        setLaunch(.starting, status: "Warming up the decks…", progress: 0.08)
        try? await Task.sleep(for: .milliseconds(180))

        setLaunch(.loadingLibrary, status: "Opening your library…", progress: 0.22)
        await repository.bootstrap { [weak self] event in
            guard let self else { return }
            switch event {
            case .storeLoaded:
                self.setLaunch(.loadingLibrary, status: "Library cache ready", progress: 0.45)
            case .systemPlaylistsReady:
                self.setLaunch(.loadingLibrary, status: "Playlists online", progress: 0.55)
            case .scanStarted:
                self.setLaunch(.scanning, status: "Scanning media…", progress: 0.68)
            case .scanFinished(let count):
                let label = count == 0
                    ? "No tracks yet — ready to import"
                    : "Found \(count) track\(count == 1 ? "" : "s")"
                self.setLaunch(.scanning, status: label, progress: 0.88)
            case .refreshed:
                self.setLaunch(.finishing, status: "Cueing the UI…", progress: 0.95)
            }
        }

        if ScreenshotDemo.isEnabled {
            setLaunch(.finishing, status: "Loading demo library…", progress: 0.92)
            await ScreenshotDemo.seed(into: self)
        }

        setLaunch(.finishing, status: "Almost there…", progress: 0.98)
        await minSplash.value
        setLaunch(.ready, status: "Let’s play", progress: 1.0)
    }

    private func setLaunch(_ phase: LaunchPhase, status: String, progress: Double) {
        launchPhase = phase
        launchStatus = status
        launchProgress = min(1, max(0, progress))
    }

    func updateSearch() async {
        let q = searchQuery
        if q.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            searchResults = []
        } else {
            searchResults = await repository.search(q)
        }
    }

    func playTrack(_ track: Track, queue: [Track]? = nil) {
        player.play(track, queue: queue)
    }

    func playAll(_ tracks: [Track]) {
        guard !tracks.isEmpty else { return }
        player.play(tracks: tracks, startIndex: 0)
    }

    var palette: AppPalette {
        palette(for: UITraitCollection.current.userInterfaceStyle == .light ? .light : .dark)
    }

    func palette(for colorScheme: ColorScheme) -> AppPalette {
        switch preferences.themeMode {
        case .dark: return .night
        case .light: return .day
        case .system: return colorScheme == .light ? .day : .night
        }
    }

    var preferredColorScheme: ColorScheme? {
        switch preferences.themeMode {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }

    var visiblePlaylists: [PlaylistMeta] {
        if preferences.jellyfinEnabled {
            return repository.playlists
        }
        return repository.playlists.filter { $0.systemType != SystemPlaylist.jellyfinOffline.rawValue }
    }

    var continueListening: [Track] { repository.continueListening }
    var recentlyAdded: [Track] { repository.recentlyAdded }
}

enum LaunchPhase: Equatable, Sendable {
    case starting
    case loadingLibrary
    case scanning
    case finishing
    case ready
}

enum BootstrapEvent: Sendable {
    case storeLoaded
    case systemPlaylistsReady
    case scanStarted
    case scanFinished(trackCount: Int)
    case refreshed
}

enum AppTab: String, CaseIterable, Identifiable {
    case home, library, playlists, tools

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .library: return "Library"
        case .playlists: return "Playlists"
        case .tools: return "Tools"
        }
    }

    var systemImage: String {
        switch self {
        case .home: return "house.fill"
        case .library: return "music.note.list"
        case .playlists: return "list.bullet.rectangle"
        case .tools: return "wrench.and.screwdriver.fill"
        }
    }
}
