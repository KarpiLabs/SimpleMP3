//
//  ContentView.swift
//  Simple MP3
//

import SwiftUI

struct ContentView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        @Bindable var app = app
        ZStack {
            NightBackground()

            TabView(selection: $app.selectedTab) {
                NavigationStack {
                    HomeScreen()
                        .navigationBarHidden(true)
                }
                .tabItem { Label(AppTab.home.title, systemImage: AppTab.home.systemImage) }
                .tag(AppTab.home)

                NavigationStack {
                    SearchScreen()
                        .navigationTitle("Search")
                }
                .tabItem { Label(AppTab.search.title, systemImage: AppTab.search.systemImage) }
                .tag(AppTab.search)

                NavigationStack {
                    LibraryScreen()
                        .navigationTitle("Library")
                }
                .tabItem { Label(AppTab.library.title, systemImage: AppTab.library.systemImage) }
                .tag(AppTab.library)

                NavigationStack {
                    PlaylistsScreen()
                        .navigationTitle("Playlists")
                }
                .tabItem { Label(AppTab.playlists.title, systemImage: AppTab.playlists.systemImage) }
                .tag(AppTab.playlists)

                NavigationStack {
                    ToolsScreen()
                        .navigationTitle("Tools")
                }
                .tabItem { Label(AppTab.tools.title, systemImage: AppTab.tools.systemImage) }
                .tag(AppTab.tools)
            }
            .tint(AppColors.accentTeal)
            .toolbarBackground(palette.background.opacity(0.9), for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)

            if !app.preferences.driveMode {
                VStack {
                    Spacer()
                    MiniPlayerBar()
                        .padding(.bottom, 49)
                }
            }
        }
        .sheet(isPresented: $app.showNowPlaying) {
            NowPlayingSheet()
                .presentationDetents([.large])
                .environment(app)
                .environment(\.appPalette, app.palette)
        }
        .sheet(isPresented: $app.showQueue) {
            QueueSheet()
                .environment(app)
                .environment(\.appPalette, app.palette)
        }
        .sheet(item: $app.addToPlaylistTrack) { track in
            AddToPlaylistSheet(track: track)
                .environment(app)
                .environment(\.appPalette, app.palette)
        }
        // Full-screen destinations for deterministic App Store screenshot capture.
        .fullScreenCover(item: $app.screenshotOverlay) { overlay in
            NavigationStack {
                switch overlay {
                case .jellyfin:
                    JellyfinScreen()
                case .carplay:
                    CarPlayScreenshotMock()
                }
            }
            .environment(app)
            .environment(\.appPalette, app.palette)
            .preferredColorScheme(.dark)
        }
    }
}

/// In-app CarPlay-style panel for marketing screenshots (CarPlay UI itself is system-owned).
struct CarPlayScreenshotMock: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack {
                Text("CarPlay")
                    .font(.title2.bold())
                    .foregroundStyle(AppColors.accentTeal)
                Spacer()
                Text("Simple MP3")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textSecondary)
            }

            Text("Browse")
                .font(.title3.bold())
                .foregroundStyle(palette.textPrimary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                carTile(icon: "music.note.list", title: "Playlists", subtitle: "Your collections")
                carTile(icon: "server.rack", title: "Jellyfin Offline", subtitle: "Downloaded for the car")
                carTile(icon: "opticaldisc", title: "Albums", subtitle: "Browse by album")
                carTile(icon: "person.2.fill", title: "Artists", subtitle: "Browse by artist")
                carTile(icon: "music.note", title: "Songs", subtitle: "All tracks")
                carTile(icon: "clock.fill", title: "Recently Played", subtitle: "Jump back in")
            }

            if let track = app.player.state.current {
                HStack(spacing: 14) {
                    AlbumArtView(artworkUri: track.artworkUri, size: 56, cornerRadius: 10)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.headline)
                            .foregroundStyle(palette.textPrimary)
                        Text("\(track.artist) · Playing")
                            .font(.caption)
                            .foregroundStyle(palette.textSecondary)
                    }
                    Spacer()
                    Image(systemName: "pause.fill")
                        .font(.title2)
                        .foregroundStyle(AppColors.accentTeal)
                }
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(palette.card)
                )
            }

            Spacer()
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NightBackground())
        .navigationTitle("CarPlay")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func carTile(icon: String, title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundStyle(AppColors.accentTeal)
            Spacer(minLength: 0)
            Text(title)
                .font(.headline)
                .foregroundStyle(palette.textPrimary)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(palette.textSecondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 120, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.card)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.white.opacity(0.06), lineWidth: 1)
                )
        )
    }
}

#Preview {
    ContentView()
        .environment(AppModel.shared)
        .environment(\.appPalette, .night)
}
