//
//  Simple_MP3App.swift
//  Simple MP3
//
//  Local music player with playlists, Jellyfin offline, and Apple CarPlay.
//

import SwiftUI

@main
struct Simple_MP3App: App {
    @State private var app = AppModel.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .environment(\.appPalette, app.palette)
                // Dark chrome during cold start so system launch → splash match.
                .preferredColorScheme(app.launchPhase == .ready ? app.preferredColorScheme : .dark)
        }
    }
}
