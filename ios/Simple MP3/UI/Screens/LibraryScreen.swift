//
//  LibraryScreen.swift
//  Simple MP3
//

import SwiftUI

struct LibraryScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var segment = 0
    @State private var selected = Set<String>()
    @State private var editMode: EditMode = .inactive

    var body: some View {
        @Bindable var app = app
        VStack(spacing: 0) {
            if isSearching {
                searchResultsList
            } else {
                Picker("Library", selection: $segment) {
                    Text("Songs").tag(0)
                    Text("Albums").tag(1)
                    Text("Artists").tag(2)
                    Text("Folders").tag(3)
                }
                .pickerStyle(.segmented)
                .padding()

                switch segment {
                case 0: songsList
                case 1: albumsList
                case 2: artistsList
                default: foldersList
                }
            }
        }
        .searchable(text: $app.searchQuery, prompt: "Songs, artists, albums, playlists")
        .onChange(of: app.searchQuery) { _, _ in
            Task { await app.updateSearch() }
        }
        .environment(\.editMode, $editMode)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if segment == 0 || isSearching {
                    EditButton()
                        .foregroundStyle(palette.accent)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                if editMode.isEditing, !selected.isEmpty {
                    selectionMenu
                } else {
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
    }

    private var isSearching: Bool {
        !app.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var selectionMenu: some View {
        Menu {
            Button("Add to queue") {
                app.player.addToQueue(selectedTracks)
                selected.removeAll()
                editMode = .inactive
            }
            Button("Play next") {
                app.player.playNext(selectedTracks)
                selected.removeAll()
                editMode = .inactive
            }
            Button("Add to playlist") {
                app.addToPlaylistTracks = selectedTracks
                if let first = selectedTracks.first {
                    app.addToPlaylistTrack = first
                }
                selected.removeAll()
                editMode = .inactive
            }
            Button("Hide", role: .destructive) {
                Task { await app.repository.hideTracks(Array(selected)) }
                selected.removeAll()
                editMode = .inactive
            }
        } label: {
            Label("\(selected.count)", systemImage: "checkmark.circle")
                .foregroundStyle(palette.accent)
        }
    }

    private var selectedTracks: [Track] {
        let source = isSearching ? app.searchResults.tracks : app.repository.tracks
        return source.filter { selected.contains($0.id) }
    }

    private var searchResultsList: some View {
        List(selection: $selected) {
            if app.searchResults.isEmpty {
                Text("No matches for “\(app.searchQuery)”")
                    .foregroundStyle(palette.textSecondary)
                    .listRowBackground(Color.clear)
            }
            if !app.searchResults.playlists.isEmpty {
                Section("Playlists") {
                    ForEach(app.searchResults.playlists) { pl in
                        NavigationLink {
                            PlaylistDetailScreen(playlistId: pl.id)
                        } label: {
                            Label(pl.name, systemImage: "music.note.list")
                        }
                        .listRowBackground(Color.clear)
                    }
                }
            }
            if !app.searchResults.albums.isEmpty {
                Section("Albums") {
                    ForEach(app.searchResults.albums) { album in
                        NavigationLink {
                            CollectionDetailScreen(
                                title: album.name,
                                subtitle: album.subtitle,
                                load: { await app.repository.tracks(album: album.name, artist: album.subtitle) }
                            )
                        } label: {
                            Text(album.name)
                        }
                        .listRowBackground(Color.clear)
                    }
                }
            }
            if !app.searchResults.artists.isEmpty {
                Section("Artists") {
                    ForEach(app.searchResults.artists) { artist in
                        NavigationLink {
                            CollectionDetailScreen(
                                title: artist.name,
                                subtitle: Formatters.trackCount(artist.trackCount),
                                load: { await app.repository.tracks(artist: artist.name) }
                            )
                        } label: {
                            Text(artist.name)
                        }
                        .listRowBackground(Color.clear)
                    }
                }
            }
            if !app.searchResults.tracks.isEmpty {
                Section("Songs") {
                    ForEach(app.searchResults.tracks) { track in
                        TrackRowView(
                            track: track,
                            isPlaying: app.player.state.current?.id == track.id,
                            onTap: { app.playTrack(track, queue: app.searchResults.tracks) },
                            onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                            onMore: { app.addToPlaylistTrack = track },
                            onHide: { app.hideTrack(track) }
                        )
                        .tag(track.id)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private var songsList: some View {
        List(selection: $selected) {
            ForEach(app.repository.tracks) { track in
                TrackRowView(
                    track: track,
                    isPlaying: app.player.state.current?.id == track.id,
                    onTap: { app.playTrack(track, queue: app.repository.tracks) },
                    onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                    onMore: { app.addToPlaylistTrack = track },
                    onHide: { app.hideTrack(track) }
                )
                .tag(track.id)
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
    @State private var selected = Set<String>()
    @State private var editMode: EditMode = .inactive

    var body: some View {
        List(selection: $selected) {
            ForEach(tracks) { track in
                TrackRowView(
                    track: track,
                    isPlaying: app.player.state.current?.id == track.id,
                    onTap: { app.playTrack(track, queue: tracks) },
                    onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                    onMore: { app.addToPlaylistTrack = track },
                    onHide: { app.hideTrack(track) }
                )
                .tag(track.id)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .environment(\.editMode, $editMode)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                EditButton().foregroundStyle(palette.accent)
            }
            ToolbarItem(placement: .topBarTrailing) {
                if editMode.isEditing, !selected.isEmpty {
                    Menu {
                        Button("Add to queue") {
                            app.player.addToQueue(tracks.filter { selected.contains($0.id) })
                            selected.removeAll()
                            editMode = .inactive
                        }
                        Button("Play next") {
                            app.player.playNext(tracks.filter { selected.contains($0.id) })
                            selected.removeAll()
                            editMode = .inactive
                        }
                        Button("Add to playlist") {
                            let picked = tracks.filter { selected.contains($0.id) }
                            app.addToPlaylistTracks = picked
                            app.addToPlaylistTrack = picked.first
                            selected.removeAll()
                            editMode = .inactive
                        }
                        Button("Hide", role: .destructive) {
                            Task { await app.repository.hideTracks(Array(selected)) }
                            selected.removeAll()
                            editMode = .inactive
                        }
                    } label: {
                        Label("\(selected.count)", systemImage: "checkmark.circle")
                    }
                    .foregroundStyle(palette.accent)
                }
            }
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
