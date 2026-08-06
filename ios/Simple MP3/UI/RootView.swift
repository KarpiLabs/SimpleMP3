//
//  RootView.swift
//  Simple MP3
//
//  Owns the launch → splash → main shell transition.
//

import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var app
    @State private var showSplash = true
    @State private var splashReady = false

    var body: some View {
        ZStack {
            if app.launchPhase == .ready || !showSplash {
                ContentView()
                    .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }

            if showSplash {
                SplashView(
                    status: app.launchStatus,
                    progress: app.launchProgress,
                    isReady: splashReady
                )
                .transition(.opacity)
                .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.45), value: showSplash)
        .task {
            await app.bootstrap()
            // Hold a beat so the progress bar lands at 100% and exit feels intentional.
            // Screenshot capture skips the hold so scenes settle faster.
            splashReady = true
            let holdMs: UInt64 = ScreenshotDemo.isEnabled ? 80 : 420
            try? await Task.sleep(for: .milliseconds(holdMs))
            showSplash = false
        }
    }
}

#Preview {
    RootView()
        .environment(AppModel.shared)
        .environment(\.appPalette, .night)
        .preferredColorScheme(.dark)
}
