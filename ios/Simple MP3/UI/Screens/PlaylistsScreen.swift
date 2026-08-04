//
//  PlaylistsScreen.swift
//  Simple MP3
//

import SwiftUI

struct PlaylistsScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var showCreate = false
    @State private var newName = ""

    var body: some View {
        List {
            ForEach(app.visiblePlaylists) { pl in
                NavigationLink {
                    PlaylistDetailScreen(playlistId: pl.id)
                } label: {
                    HStack(spacing: 12) {
                        AlbumArtView(artworkUri: pl.displayCover, size: 56)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pl.name)
                                .font(.headline)
                                .foregroundStyle(palette.textPrimary)
                            Text(Formatters.trackCount(pl.trackCount) + (pl.isSystem ? " · System" : ""))
                                .font(.caption)
                                .foregroundStyle(palette.textSecondary)
                        }
                    }
                }
                .listRowBackground(Color.clear)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    if !pl.isSystem {
                        Button(role: .destructive) {
                            Task { await app.repository.deletePlaylist(id: pl.id) }
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    newName = ""
                    showCreate = true
                } label: {
                    Image(systemName: "plus")
                        .foregroundStyle(AppColors.accentTeal)
                }
            }
        }
        .alert("New playlist", isPresented: $showCreate) {
            TextField("Name", text: $newName)
            Button("Create") {
                let name = newName.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return }
                Task { _ = await app.repository.createPlaylist(name: name) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

struct PlaylistDetailScreen: View {
    let playlistId: String
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var tracks: [Track] = []
    @State private var title = "Playlist"

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
            .onDelete { indexSet in
                Task {
                    for i in indexSet {
                        await app.repository.removeFromPlaylist(playlistId: playlistId, trackId: tracks[i].id)
                    }
                    await reload()
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Play all") { app.playAll(tracks) }
                    .foregroundStyle(AppColors.accentTeal)
                    .disabled(tracks.isEmpty)
            }
        }
        .task { await reload() }
    }

    private func reload() async {
        if let p = await app.repository.playlist(id: playlistId) {
            title = p.name
        }
        tracks = await app.repository.tracksForPlaylist(id: playlistId)
    }
}

struct AddToPlaylistSheet: View {
    let track: Track
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appPalette) private var palette

    var body: some View {
        NavigationStack {
            List(app.visiblePlaylists) { pl in
                Button {
                    Task {
                        await app.repository.addToPlaylist(playlistId: pl.id, trackId: track.id)
                        dismiss()
                    }
                } label: {
                    HStack {
                        Text(pl.name)
                            .foregroundStyle(palette.textPrimary)
                        Spacer()
                        Text(Formatters.trackCount(pl.trackCount))
                            .foregroundStyle(palette.textMuted)
                            .font(.caption)
                    }
                }
                .listRowBackground(palette.card)
            }
            .scrollContentBackground(.hidden)
            .background(NightBackground())
            .navigationTitle("Add to playlist")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
