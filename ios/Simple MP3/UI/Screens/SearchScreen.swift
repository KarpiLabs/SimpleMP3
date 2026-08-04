//
//  SearchScreen.swift
//  Simple MP3
//

import SwiftUI

struct SearchScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        @Bindable var app = app
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(palette.textMuted)
                TextField("Songs, artists, albums", text: $app.searchQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .foregroundStyle(palette.textPrimary)
                    .onChange(of: app.searchQuery) { _, _ in
                        Task { await app.updateSearch() }
                    }
                if !app.searchQuery.isEmpty {
                    Button {
                        app.searchQuery = ""
                        app.searchResults = []
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(palette.textMuted)
                    }
                }
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 14).fill(palette.card))
            .padding()

            if app.searchResults.isEmpty {
                ContentUnavailableView(
                    app.searchQuery.isEmpty ? "Search your library" : "No results",
                    systemImage: "magnifyingglass",
                    description: Text(app.searchQuery.isEmpty
                        ? "Find local, Jellyfin offline, and imported tracks."
                        : "Try another title, artist, or album.")
                )
                .foregroundStyle(palette.textSecondary)
            } else {
                List(app.searchResults) { track in
                    TrackRowView(
                        track: track,
                        isPlaying: app.player.state.current?.id == track.id,
                        onTap: { app.playTrack(track, queue: app.searchResults) },
                        onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                        onMore: { app.addToPlaylistTrack = track }
                    )
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }
}
