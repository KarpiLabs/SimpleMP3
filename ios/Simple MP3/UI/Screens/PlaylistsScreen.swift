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
    @State private var showRename = false
    @State private var renameText = ""
    @State private var renameTarget: PlaylistMeta?

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
                .swipeActions(edge: .leading, allowsFullSwipe: false) {
                    if !pl.isSystem {
                        Button {
                            renameTarget = pl
                            renameText = pl.name
                            showRename = true
                        } label: {
                            Label("Rename", systemImage: "pencil")
                        }
                        .tint(palette.accent)
                    }
                }
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
                        .foregroundStyle(palette.accent)
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
        .alert("Rename playlist", isPresented: $showRename) {
            TextField("Name", text: $renameText)
            Button("Save") {
                let name = renameText.trimmingCharacters(in: .whitespaces)
                guard let id = renameTarget?.id, !name.isEmpty else { return }
                Task { await app.repository.renamePlaylist(id: id, name: name) }
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
    @State private var canAddSongs = false
    @State private var canRename = false
    @State private var canReorder = false
    @State private var showAddSongs = false
    @State private var showRename = false
    @State private var renameText = ""

    var body: some View {
        List {
            if tracks.isEmpty {
                emptyState
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
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
            .onMove(perform: canReorder ? { source, dest in
                tracks.move(fromOffsets: source, toOffset: dest)
                Task {
                    await app.repository.setPlaylistTrackIds(
                        playlistId: playlistId,
                        trackIds: tracks.map(\.id)
                    )
                }
            } : nil)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 14) {
                    if canAddSongs {
                        Button {
                            showAddSongs = true
                        } label: {
                            Image(systemName: "plus")
                        }
                        .foregroundStyle(palette.accent)
                        .accessibilityLabel("Add songs")
                    }
                    Button {
                        app.player.play(tracks: tracks.shuffled(), startIndex: 0)
                    } label: {
                        Image(systemName: "shuffle")
                    }
                    .foregroundStyle(palette.accent)
                    .disabled(tracks.isEmpty)
                    .accessibilityLabel("Shuffle")
                    Button("Play all") { app.playAll(tracks) }
                        .foregroundStyle(palette.accent)
                        .disabled(tracks.isEmpty)
                    if canRename || canReorder {
                        Menu {
                            if canRename {
                                Button("Rename", systemImage: "pencil") {
                                    renameText = title
                                    showRename = true
                                }
                            }
                            if canReorder {
                                EditButton()
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .foregroundStyle(palette.accent)
                        }
                    }
                }
            }
        }
        .alert("Rename playlist", isPresented: $showRename) {
            TextField("Name", text: $renameText)
            Button("Save") {
                let name = renameText.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return }
                Task {
                    await app.repository.renamePlaylist(id: playlistId, name: name)
                    await reload()
                }
            }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showAddSongs) {
            AddSongsToPlaylistSheet(
                playlistId: playlistId,
                existingIds: Set(tracks.map(\.id))
            ) {
                await reload()
            }
            .environment(app)
            .environment(\.appPalette, app.palette)
        }
        .task { await reload() }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "music.note.list")
                .font(.system(size: 40))
                .foregroundStyle(palette.accent)
            Text("No songs yet")
                .font(.title3.bold())
                .foregroundStyle(palette.textPrimary)
            Text(canAddSongs
                 ? "Add tracks from your library."
                 : "This playlist fills automatically.")
                .font(.subheadline)
                .foregroundStyle(palette.textSecondary)
                .multilineTextAlignment(.center)
            if canAddSongs {
                Button {
                    showAddSongs = true
                } label: {
                    Text("Add songs")
                        .font(.headline)
                        .foregroundStyle(AppColors.nightBlack)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(palette.accent))
                }
                .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 36)
    }

    private func reload() async {
        if let p = await app.repository.playlist(id: playlistId) {
            title = p.name
            canAddSongs = Self.allowsAddingSongs(p)
            canRename = !p.isSystem
            canReorder = !p.isSystem || p.systemType == SystemPlaylist.favorites.rawValue
        }
        tracks = await app.repository.tracksForPlaylist(id: playlistId)
    }

    static func allowsAddingSongs(_ playlist: Playlist) -> Bool {
        if !playlist.isSystem { return true }
        return playlist.systemType == SystemPlaylist.favorites.rawValue
    }
}

struct AddSongsToPlaylistSheet: View {
    let playlistId: String
    let existingIds: Set<String>
    var onAdded: () async -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appPalette) private var palette
    @State private var query = ""
    @State private var selected: Set<String> = []
    @State private var adding = false

    private var candidates: [Track] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return app.repository.tracks.filter { track in
            guard !existingIds.contains(track.id) else { return false }
            guard !q.isEmpty else { return true }
            return track.title.localizedCaseInsensitiveContains(q)
                || track.artist.localizedCaseInsensitiveContains(q)
                || track.album.localizedCaseInsensitiveContains(q)
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(palette.textMuted)
                    TextField("Filter songs", text: $query)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .foregroundStyle(palette.textPrimary)
                    if !query.isEmpty {
                        Button {
                            query = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(palette.textMuted)
                        }
                    }
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 14).fill(palette.card))
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 10)

                if candidates.isEmpty {
                    ContentUnavailableView(
                        query.isEmpty ? "All songs are already in this playlist" : "No matching songs",
                        systemImage: "music.note",
                        description: Text(query.isEmpty
                            ? "Import or scan more music, then come back."
                            : "Try another title, artist, or album.")
                    )
                    .foregroundStyle(palette.textSecondary)
                } else {
                    List(candidates) { track in
                        Button {
                            if selected.contains(track.id) {
                                selected.remove(track.id)
                            } else {
                                selected.insert(track.id)
                            }
                        } label: {
                            HStack(spacing: 12) {
                                AlbumArtView(artworkUri: track.artworkUri, size: 48)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(track.title)
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(palette.textPrimary)
                                        .lineLimit(1)
                                    Text(track.artist)
                                        .font(.system(size: 13))
                                        .foregroundStyle(palette.textSecondary)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 0)
                                Image(systemName: selected.contains(track.id) ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 22))
                                    .foregroundStyle(selected.contains(track.id) ? palette.accent : palette.textMuted)
                            }
                        }
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Add songs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(palette.textSecondary)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(selected.isEmpty ? "Add" : "Add \(selected.count)") {
                        Task { await addSelected() }
                    }
                    .disabled(selected.isEmpty || adding)
                    .fontWeight(.semibold)
                    .foregroundStyle(palette.accent)
                }
            }
        }
    }

    private func addSelected() async {
        adding = true
        defer { adding = false }
        for id in selected {
            await app.repository.addToPlaylist(playlistId: playlistId, trackId: id)
        }
        await onAdded()
        dismiss()
    }
}

struct AddToPlaylistSheet: View {
    let track: Track
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appPalette) private var palette

    var body: some View {
        NavigationStack {
            List(app.visiblePlaylists.filter(\.acceptsManualAdds)) { pl in
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
            .background(AppBackground())
            .navigationTitle("Add to playlist")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
