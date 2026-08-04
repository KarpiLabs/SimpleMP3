//
//  CarPlaySceneDelegate.swift
//  Simple MP3
//
//  Apple CarPlay audio template — browse tree mirrors Android Auto:
//  Continue · Liked · Playlists · Offline · Albums · Artists · Songs · Recent
//

import CarPlay
import Foundation
import MediaPlayer
import UIKit

final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interfaceController: CPInterfaceController?
    private var app: AppModel { AppModel.shared }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController
        Task { @MainActor in
            app.player.handleCarConnect()
            let root = await buildRootTemplate()
            interfaceController.setRootTemplate(root, animated: true) { _, _ in }
        }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController
    ) {
        Task { @MainActor in
            app.player.handleCarDisconnect()
        }
        self.interfaceController = nil
    }

    // MARK: - Root

    @MainActor
    private func buildRootTemplate() async -> CPListTemplate {
        var items: [CPListItem] = []

        items.append(folderItem(title: "Continue", detail: "Pick up where you left off", id: "continue"))
        items.append(folderItem(title: "Liked Songs", detail: "Favorites", id: "liked"))
        items.append(folderItem(title: "Playlists", detail: "\(app.visiblePlaylists.count)", id: "playlists"))
        if app.preferences.jellyfinEnabled {
            items.append(folderItem(title: "Jellyfin Offline", detail: "\(app.repository.jellyfinCount)", id: "jellyfin"))
        }
        items.append(folderItem(title: "YouTube Downloads", detail: "\(app.repository.youtubeCount)", id: "youtube"))
        items.append(folderItem(title: "Albums", detail: "\(app.repository.albums.count)", id: "albums"))
        items.append(folderItem(title: "Artists", detail: "\(app.repository.artists.count)", id: "artists"))
        items.append(folderItem(title: "Songs", detail: Formatters.trackCount(app.repository.trackCount), id: "songs"))
        items.append(folderItem(title: "Recently Played", detail: "History", id: "recent"))
        items.append(folderItem(title: "Now Playing", detail: app.player.state.current?.title ?? "—", id: "now"))

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

        let section = CPListSection(items: items)
        let template = CPListTemplate(title: "Simple MP3", sections: [section])
        return template
    }

    private func folderItem(title: String, detail: String, id: String) -> CPListItem {
        let item = CPListItem(text: title, detailText: detail)
        item.accessoryType = .disclosureIndicator
        item.userInfo = id
        return item
    }

    @MainActor
    private func openSection(_ id: String) async {
        switch id {
        case "continue":
            let tracks = await app.repository.getContinueTracks()
            pushTrackList(title: "Continue", tracks: tracks)
        case "liked":
            let tracks = await app.repository.getLikedTracks()
            pushTrackList(title: "Liked Songs", tracks: tracks)
        case "playlists":
            pushPlaylists()
        case "jellyfin":
            let tracks = await app.repository.tracks(source: .jellyfin)
            pushTrackList(title: "Jellyfin Offline", tracks: tracks)
        case "youtube":
            let tracks = await app.repository.tracks(source: .youtube)
            pushTrackList(title: "YouTube", tracks: tracks)
        case "albums":
            pushAlbums()
        case "artists":
            pushArtists()
        case "songs":
            pushTrackList(title: "Songs", tracks: app.repository.tracks)
        case "recent":
            let tracks = await app.repository.getRecentlyPlayed()
            pushTrackList(title: "Recently Played", tracks: tracks)
        case "now":
            pushNowPlaying()
        default:
            break
        }
    }

    @MainActor
    private func pushPlaylists() {
        let items: [CPListItem] = app.visiblePlaylists.map { pl in
            let item = CPListItem(text: pl.name, detailText: Formatters.trackCount(pl.trackCount))
            item.accessoryType = .disclosureIndicator
            item.userInfo = pl.id
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
        let template = CPListTemplate(title: "Playlists", sections: [CPListSection(items: items)])
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
        let template = CPListTemplate(title: "Albums", sections: [CPListSection(items: Array(items))])
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
        let template = CPListTemplate(title: "Artists", sections: [CPListSection(items: Array(items))])
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    @MainActor
    private func pushTrackList(title: String, tracks: [Track]) {
        var items: [CPListItem] = []

        if !tracks.isEmpty {
            let playAll = CPListItem(text: "Play All", detailText: Formatters.trackCount(tracks.count))
            playAll.handler = { [weak self] _, completion in
                Task { @MainActor in
                    self?.app.playAll(tracks)
                    completion()
                }
            }
            items.append(playAll)

            let shuffle = CPListItem(text: "Shuffle", detailText: "Random order")
            shuffle.handler = { [weak self] _, completion in
                Task { @MainActor in
                    self?.app.player.play(tracks: tracks.shuffled(), startIndex: 0)
                    completion()
                }
            }
            items.append(shuffle)
        }

        for (index, track) in tracks.prefix(200).enumerated() {
            let item = CPListItem(text: track.title, detailText: track.artist)
            item.handler = { [weak self] _, completion in
                Task { @MainActor in
                    self?.app.player.play(tracks: tracks, startIndex: index)
                    completion()
                }
            }
            items.append(item)
        }

        if items.isEmpty {
            items.append(CPListItem(text: "No tracks", detailText: "Add music on your iPhone"))
        }

        let template = CPListTemplate(title: title, sections: [CPListSection(items: items)])
        interfaceController?.pushTemplate(template, animated: true) { _, _ in }
    }

    @MainActor
    private func pushNowPlaying() {
        let state = app.player.state
        var items: [CPListItem] = []

        let title = CPListItem(
            text: state.current?.title ?? "Nothing playing",
            detailText: state.current?.artist
        )
        items.append(title)

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
}
