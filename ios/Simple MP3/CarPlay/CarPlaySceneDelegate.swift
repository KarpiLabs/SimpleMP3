//
//  CarPlaySceneDelegate.swift
//  Simple MP3
//
//  Apple CarPlay templates — tab bar root (Home / Recently Played / Browse /
//  Your Library): a greeting header, a horizontal "continue listening" image
//  row, and playlist shelves.
//
//  Requires entitlement: com.apple.developer.carplay-audio
//

import CarPlay
import Foundation
import MediaPlayer
import UIKit

@objc(CarPlaySceneDelegate)
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interfaceController: CPInterfaceController?
    private var carWindow: CPWindow?
    private var libraryObserver: NSObjectProtocol?
    private var playbackObserver: NSObjectProtocol?

    private var app: AppModel { AppModel.shared }

    // MARK: - Connect / disconnect

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        connect(interfaceController: interfaceController, window: nil)
    }

    /// Preferred path on modern iOS — window hosts Now Playing chrome when needed.
    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController,
        to window: CPWindow
    ) {
        connect(interfaceController: interfaceController, window: window)
    }

    @objc(templateApplicationScene:didDisconnectInterfaceController:)
    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        disconnect()
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController,
        from window: CPWindow
    ) {
        disconnect()
    }

    private func connect(interfaceController: CPInterfaceController, window: CPWindow?) {
        self.interfaceController = interfaceController
        self.carWindow = window

        configureNowPlayingTemplate()
        startObserving()

        Task { @MainActor in
            // Ensure library is warm if CarPlay launches before (or instead of) the phone UI.
            if !app.repository.isLoaded {
                await app.bootstrap()
            }
            app.player.handleCarConnect()
            let root = await buildRootTemplate()
            interfaceController.setRootTemplate(root, animated: true) { _, error in
                if let error {
                    print("CarPlay setRootTemplate error: \(error)")
                }
            }
        }
    }

    private func disconnect() {
        stopObserving()
        Task { @MainActor in
            app.player.handleCarDisconnect()
        }
        interfaceController = nil
        carWindow = nil
    }

    // MARK: - Now Playing

    private func configureNowPlayingTemplate() {
        let np = CPNowPlayingTemplate.shared
        // Default system buttons (play/pause, next, previous) use MPRemoteCommandCenter
        // already wired in PlaybackManager.
        np.isUpNextButtonEnabled = false
        np.isAlbumArtistButtonEnabled = true
    }

    // MARK: - Library refresh

    private func startObserving() {
        stopObserving()
        libraryObserver = NotificationCenter.default.addObserver(
            forName: .libraryDidChange,
            object: nil,
            queue: .main
        ) { _ in
            Task { @MainActor [weak self] in self?.reloadRootIfNeeded() }
        }
        playbackObserver = NotificationCenter.default.addObserver(
            forName: .playbackDidChange,
            object: nil,
            queue: .main
        ) { _ in
            Task { @MainActor [weak self] in self?.reloadRootIfNeeded() }
        }
    }

    private func stopObserving() {
        if let libraryObserver {
            NotificationCenter.default.removeObserver(libraryObserver)
            self.libraryObserver = nil
        }
        if let playbackObserver {
            NotificationCenter.default.removeObserver(playbackObserver)
            self.playbackObserver = nil
        }
    }

    @MainActor
    private func reloadRootIfNeeded() {
        guard let interfaceController else { return }
        // Only refresh when at root so we don't interrupt deep navigation mid-browse.
        guard interfaceController.templates.count <= 1 else { return }
        Task {
            let root = await buildRootTemplate()
            interfaceController.setRootTemplate(root, animated: false) { _, _ in }
        }
    }

    // MARK: - Root (tab bar: Home · Recently Played · Browse · Your Library)

    @MainActor
    private func buildRootTemplate() async -> CPTabBarTemplate {
        let home = await buildHomeTemplate()
        home.tabTitle = "Home"
        home.tabImage = UIImage(systemName: "house.fill")

        let recent = await buildRecentlyPlayedTemplate()
        recent.tabTitle = "Recently Played"
        recent.tabImage = UIImage(systemName: "clock.fill")

        let browse = buildBrowseTemplate()
        browse.tabTitle = "Browse"
        browse.tabImage = UIImage(systemName: "square.grid.2x2.fill")

        let library = buildLibraryTemplate()
        library.tabTitle = "Your Library"
        library.tabImage = UIImage(systemName: "books.vertical.fill")

        return CPTabBarTemplate(templates: [home, recent, browse, library])
    }

    /// Greeting header + horizontal "continue listening" shelf + playlist shelf.
    @MainActor
    private func buildHomeTemplate() async -> CPListTemplate {
        var sections: [CPListSection] = []

        var greetingHeader = Formatters.greeting()
        if app.preferences.showCarPlayWeather, let weather = await WeatherService.shared.getWeatherSummary() {
            greetingHeader += " · \(weather)"
        }

        let continueTracks = await app.repository.getContinueTracks()
        if !continueTracks.isEmpty {
            let shelf = Array(continueTracks.prefix(8))
            let elements = shelf.map { track in
                CPListImageRowItemRowElement(
                    image: artworkImage(for: track, side: 240),
                    title: track.title,
                    subtitle: track.artist
                )
            }
            let row = CPListImageRowItem(text: "Jump back in", elements: elements, allowsMultipleLines: false)
            row.listImageRowHandler = { [weak self] _, index, completion in
                guard let self else { completion(); return }
                Task { @MainActor in
                    self.app.player.play(tracks: shelf, startIndex: index)
                    self.presentSystemNowPlaying()
                    completion()
                }
            }
            sections.append(CPListSection(items: [row], header: greetingHeader, sectionIndexTitle: nil))
        }

        let playlists = Array(app.visiblePlaylists.prefix(10))
        if !playlists.isEmpty {
            sections.append(CPListSection(
                items: playlists.map(playlistItem),
                header: nil,
                sectionIndexTitle: nil
            ))
        }

        if sections.isEmpty {
            sections = [CPListSection(items: [CPListItem(text: "No music yet", detailText: "Add music on your iPhone")])]
        }

        return CPListTemplate(title: "Home", sections: sections)
    }

    @MainActor
    private func buildRecentlyPlayedTemplate() async -> CPListTemplate {
        let tracks = await app.repository.getRecentlyPlayed()
        return trackListTemplate(title: "Recently Played", tracks: tracks)
    }

    @MainActor
    private func buildBrowseTemplate() -> CPListTemplate {
        var items: [CPListItem] = []
        if app.preferences.jellyfinEnabled {
            items.append(folderItem(title: "Jellyfin Offline", detail: "\(app.repository.jellyfinCount)", id: "jellyfin"))
        }
        items.append(folderItem(title: "Albums", detail: "\(app.repository.albums.count)", id: "albums"))
        items.append(folderItem(title: "Artists", detail: "\(app.repository.artists.count)", id: "artists"))
        items.append(folderItem(title: "Songs", detail: Formatters.trackCount(app.repository.trackCount), id: "songs"))
        attachSectionHandlers(items)
        return CPListTemplate(title: "Browse", sections: [CPListSection(items: items)])
    }

    @MainActor
    private func buildLibraryTemplate() -> CPListTemplate {
        var items: [CPListItem] = []
        items.append(folderItem(title: "Liked Songs", detail: "Favorites", id: "liked"))
        items.append(folderItem(title: "Playlists", detail: "\(app.visiblePlaylists.count)", id: "playlists"))
        let nowDetail = app.player.state.current?.title ?? "Nothing playing"
        items.append(folderItem(title: "Now Playing", detail: nowDetail, id: "now"))
        attachSectionHandlers(items)
        return CPListTemplate(title: "Your Library", sections: [CPListSection(items: items)])
    }

    private func folderItem(title: String, detail: String, id: String) -> CPListItem {
        let item = CPListItem(text: title, detailText: detail)
        item.accessoryType = .disclosureIndicator
        item.userInfo = id
        return item
    }

    private func attachSectionHandlers(_ items: [CPListItem]) {
        for item in items {
            item.handler = { [weak self] _, completion in
                guard let self, let id = item.userInfo as? String else {
                    completion()
                    return
                }
                Task { @MainActor in
                    await self.openSection(id)
                    completion()
                }
            }
        }
    }

    @MainActor
    private func openSection(_ id: String) async {
        switch id {
        case "liked":
            let tracks = await app.repository.getLikedTracks()
            pushTrackList(title: "Liked Songs", tracks: tracks)
        case "playlists":
            pushPlaylists()
        case "jellyfin":
            let tracks = await app.repository.tracks(source: .jellyfin)
            pushTrackList(title: "Jellyfin Offline", tracks: tracks)
        case "albums":
            pushAlbums()
        case "artists":
            pushArtists()
        case "songs":
            pushTrackList(title: "Songs", tracks: app.repository.tracks)
        case "now":
            pushNowPlaying()
        default:
            break
        }
    }

    // MARK: - Artwork

    private func artworkImage(for track: Track, side: CGFloat) -> UIImage {
        MediaArtwork.image(artworkUri: track.artworkUri, trackId: track.id, side: side) ?? placeholderArtwork(side: side)
    }

    private func artworkImage(for playlist: PlaylistMeta, side: CGFloat) -> UIImage {
        MediaArtwork.image(artworkUri: playlist.displayCover, side: side) ?? placeholderArtwork(side: side)
    }

    private func placeholderArtwork(side: CGFloat) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        return renderer.image { _ in
            UIColor(white: 0.18, alpha: 1).setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: side, height: side))
            let config = UIImage.SymbolConfiguration(pointSize: side * 0.4, weight: .semibold)
            if let note = UIImage(systemName: "music.note", withConfiguration: config)?
                .withTintColor(UIColor(white: 0.55, alpha: 1), renderingMode: .alwaysOriginal) {
                let origin = CGPoint(x: (side - note.size.width) / 2, y: (side - note.size.height) / 2)
                note.draw(at: origin)
            }
        }
    }

    // MARK: - Sections

    @MainActor
    private func playlistItem(_ pl: PlaylistMeta) -> CPListItem {
        let item = CPListItem(text: pl.name, detailText: Formatters.trackCount(pl.trackCount), image: artworkImage(for: pl, side: 120))
        item.accessoryType = .disclosureIndicator
        item.handler = { [weak self] _, completion in
            guard let self else { completion(); return }
            Task { @MainActor in
                let tracks = await self.app.repository.tracksForPlaylist(id: pl.id)
                self.pushTrackList(title: pl.name, tracks: tracks)
                completion()
            }
        }
        return item
    }

    @MainActor
    private func pushPlaylists() {
        let items = app.visiblePlaylists.map(playlistItem)
        let safe = items.isEmpty
            ? [CPListItem(text: "No playlists", detailText: "Create one on your iPhone")]
            : items
        let template = CPListTemplate(title: "Playlists", sections: [CPListSection(items: safe)])
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    @MainActor
    private func pushAlbums() {
        let items: [CPListItem] = app.repository.albums.prefix(100).map { album in
            let item = CPListItem(text: album.name, detailText: "\(album.subtitle) · \(album.trackCount)")
            item.accessoryType = .disclosureIndicator
            item.handler = { [weak self] _, completion in
                guard let self else { completion(); return }
                Task { @MainActor in
                    let tracks = await self.app.repository.tracks(album: album.name, artist: album.subtitle)
                    self.pushTrackList(title: album.name, tracks: tracks)
                    completion()
                }
            }
            return item
        }
        let safe = items.isEmpty
            ? [CPListItem(text: "No albums", detailText: "Add music on your iPhone")]
            : Array(items)
        let template = CPListTemplate(title: "Albums", sections: [CPListSection(items: safe)])
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    @MainActor
    private func pushArtists() {
        let items: [CPListItem] = app.repository.artists.prefix(100).map { artist in
            let item = CPListItem(text: artist.name, detailText: Formatters.trackCount(artist.trackCount))
            item.accessoryType = .disclosureIndicator
            item.handler = { [weak self] _, completion in
                guard let self else { completion(); return }
                Task { @MainActor in
                    let tracks = await self.app.repository.tracks(artist: artist.name)
                    self.pushTrackList(title: artist.name, tracks: tracks)
                    completion()
                }
            }
            return item
        }
        let safe = items.isEmpty
            ? [CPListItem(text: "No artists", detailText: "Add music on your iPhone")]
            : Array(items)
        let template = CPListTemplate(title: "Artists", sections: [CPListSection(items: safe)])
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    @MainActor
    private func pushTrackList(title: String, tracks: [Track]) {
        let template = trackListTemplate(title: title, tracks: tracks)
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    @MainActor
    private func trackListTemplate(title: String, tracks: [Track]) -> CPListTemplate {
        var items: [CPListItem] = []

        if !tracks.isEmpty {
            let playAll = CPListItem(text: "Play All", detailText: Formatters.trackCount(tracks.count))
            playAll.handler = { [weak self] _, completion in
                Task { @MainActor in
                    self?.app.playAll(tracks)
                    self?.presentSystemNowPlaying()
                    completion()
                }
            }
            items.append(playAll)

            let shuffle = CPListItem(text: "Shuffle", detailText: "Random order")
            shuffle.handler = { [weak self] _, completion in
                Task { @MainActor in
                    self?.app.player.play(tracks: tracks.shuffled(), startIndex: 0)
                    self?.presentSystemNowPlaying()
                    completion()
                }
            }
            items.append(shuffle)
        }

        for (index, track) in tracks.prefix(200).enumerated() {
            let item = CPListItem(text: track.title, detailText: track.artist)
            let playing = app.player.state.current?.id == track.id
            if playing {
                item.isPlaying = true
            }
            item.handler = { [weak self] _, completion in
                Task { @MainActor in
                    self?.app.player.play(tracks: tracks, startIndex: index)
                    self?.presentSystemNowPlaying()
                    completion()
                }
            }
            items.append(item)
        }

        if items.isEmpty {
            items.append(CPListItem(text: "No tracks", detailText: "Add music on your iPhone"))
        }

        return CPListTemplate(title: title, sections: [CPListSection(items: items)])
    }

    @MainActor
    private func pushNowPlaying() {
        let state = app.player.state
        var items: [CPListItem] = []

        items.append(CPListItem(
            text: state.current?.title ?? "Nothing playing",
            detailText: state.current?.artist
        ))

        if state.current != nil {
            let open = CPListItem(text: "Open Now Playing", detailText: "Full screen controls")
            open.handler = { [weak self] _, completion in
                self?.presentSystemNowPlaying()
                completion()
            }
            items.append(open)
        }

        let playPause = CPListItem(
            text: state.isPlaying ? "Pause" : "Play",
            detailText: "Toggle playback"
        )
        playPause.handler = { [weak self] _, completion in
            Task { @MainActor in
                self?.app.player.togglePlayPause()
                completion()
            }
        }
        items.append(playPause)

        let next = CPListItem(text: "Next", detailText: nil)
        next.handler = { [weak self] _, completion in
            Task { @MainActor in self?.app.player.skipNext(); completion() }
        }
        items.append(next)

        let prev = CPListItem(text: "Previous", detailText: nil)
        prev.handler = { [weak self] _, completion in
            Task { @MainActor in self?.app.player.skipPrevious(); completion() }
        }
        items.append(prev)

        let shuffle = CPListItem(
            text: state.shuffle ? "Shuffle On" : "Shuffle Off",
            detailText: "Tap to toggle"
        )
        shuffle.handler = { [weak self] _, completion in
            Task { @MainActor in self?.app.player.toggleShuffle(); completion() }
        }
        items.append(shuffle)

        let template = CPListTemplate(title: "Now Playing", sections: [CPListSection(items: items)])
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    /// Pushes the system Now Playing template (CarPlay media chrome).
    private func presentSystemNowPlaying() {
        guard let interfaceController else { return }
        let np = CPNowPlayingTemplate.shared
        // Avoid stacking multiple Now Playing templates.
        if interfaceController.topTemplate === np { return }
        if interfaceController.templates.contains(where: { $0 === np }) {
            interfaceController.popToRootTemplate(animated: false) { _, _ in
                interfaceController.pushTemplate(np, animated: true) { _, _ in }
            }
            return
        }
        interfaceController.pushTemplate(np, animated: true) { _, error in
            if let error {
                print("CarPlay Now Playing push error: \(error)")
            }
        }
    }
}
