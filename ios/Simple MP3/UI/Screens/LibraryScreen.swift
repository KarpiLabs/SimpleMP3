//
//  LibraryScreen.swift
//  Simple MP3
//

import SwiftUI

struct LibraryScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var segment = 0
    @State private var filter = ""

    var body: some View {
        VStack(spacing: 0) {
            Picker("Library", selection: $segment) {
                Text("Songs").tag(0)
                Text("Albums").tag(1)
                Text("Artists").tag(2)
                Text("Folders").tag(3)
            }
            .pickerStyle(.segmented)
            .padding()

            if segment == 0 {
                TextField("Filter songs", text: $filter)
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(palette.card))
                    .padding(.horizontal)
                    .foregroundStyle(palette.textPrimary)
            }

            switch segment {
            case 0: songsList
            case 1: albumsList
            case 2: artistsList
            default: foldersList
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await app.repository.scanLibrary(force: true) }
                } label: {
                    if app.repository.isScanning {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .foregroundStyle(palette.accent)
            }
        }
    }

    private var filteredSongs: [Track] {
        let all = app.repository.tracks
        let q = filter.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return all }
        return all.filter {
            $0.title.localizedCaseInsensitiveContains(q)
                || $0.artist.localizedCaseInsensitiveContains(q)
        }
    }

    private var songsList: some View {
        List {
            ForEach(filteredSongs) { track in
                TrackRowView(
                    track: track,
                    isPlaying: app.player.state.current?.id == track.id,
                    onTap: { app.playTrack(track, queue: filteredSongs) },
                    onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                    onMore: { app.addToPlaylistTrack = track }
                )
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private var albumsList: some View {
        List(app.repository.albums) { album in
            NavigationLink {
                CollectionDetailScreen(
                    title: album.name,
                    subtitle: album.subtitle,
                    load: { await app.repository.tracks(album: album.name, artist: album.subtitle) }
                )
            } label: {
                HStack(spacing: 12) {
                    AlbumArtView(artworkUri: album.artworkUri, size: 56)
                    VStack(alignment: .leading) {
                        Text(album.name)
                            .foregroundStyle(palette.textPrimary)
                            .font(.headline)
                        Text("\(album.subtitle) · \(Formatters.trackCount(album.trackCount))")
                            .font(.caption)
                            .foregroundStyle(palette.textSecondary)
                    }
                }
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private var artistsList: some View {
        List(app.repository.artists) { artist in
            NavigationLink {
                CollectionDetailScreen(
                    title: artist.name,
                    subtitle: Formatters.trackCount(artist.trackCount),
                    load: { await app.repository.tracks(artist: artist.name) }
                )
            } label: {
                HStack(spacing: 12) {
                    AlbumArtView(artworkUri: artist.artworkUri, size: 56)
                    VStack(alignment: .leading) {
                        Text(artist.name)
                            .foregroundStyle(palette.textPrimary)
                            .font(.headline)
                        Text(Formatters.trackCount(artist.trackCount))
                            .font(.caption)
                            .foregroundStyle(palette.textSecondary)
                    }
                }
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private var foldersList: some View {
        List(app.repository.folderPaths, id: \.self) { path in
            NavigationLink {
                CollectionDetailScreen(
                    title: path,
                    subtitle: "Folder",
                    load: { await app.repository.tracks(folderPath: path) }
                )
            } label: {
                Label(path, systemImage: "folder.fill")
                    .foregroundStyle(palette.textPrimary)
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }
}

struct CollectionDetailScreen: View {
    let title: String
    let subtitle: String
    let load: () async -> [Track]

    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var tracks: [Track] = []

    var body: some View {
        List {
            ForEach(tracks) { track in
                TrackRowView(
                    track: track,
                    isPlaying: app.player.state.current?.id == track.id,
                    onTap: { app.playTrack(track, queue: tracks) },
                    onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                    onMore: { app.addToPlaylistTrack = track }
                )
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Play all") { app.playAll(tracks) }
                    .foregroundStyle(palette.accent)
                    .disabled(tracks.isEmpty)
            }
        }
        .task {
            tracks = await load()
        }
    }
}
