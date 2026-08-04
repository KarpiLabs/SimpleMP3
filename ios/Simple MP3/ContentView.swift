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
    }
}

#Preview {
    ContentView()
        .environment(AppModel.shared)
        .environment(\.appPalette, .night)
}
