//
//  HomeScreen.swift
//  Simple MP3
//

import SwiftUI

struct HomeScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        if app.preferences.driveMode {
            DriveModeHome()
        } else {
            regularHome
        }
    }

    private var regularHome: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                if !app.continueListening.isEmpty {
                    sectionContinue
                }
                if !app.visiblePlaylists.isEmpty {
                    sectionPlaylists
                }
                if !app.recentlyAdded.isEmpty {
                    sectionRecent
                }
                // spacer for mini player
                if app.repository.trackCount == 0 && !app.repository.isScanning {
                    emptyState
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 100)
        }
        .refreshable {
            await app.repository.scanLibrary(force: true)
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            Image("SplashMascot")
                .resizable()
                .scaledToFill()
                .frame(width: 44, height: 44)
                .clipShape(Circle())
                .overlay(Circle().stroke(palette.card, lineWidth: 2))
            VStack(alignment: .leading, spacing: 4) {
                Text(Formatters.greeting())
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(palette.textSecondary)
                Text(librarySubtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(palette.textMuted)
            }
            Spacer()
            HStack(spacing: 8) {
                Button {
                    app.preferences.driveMode.toggle()
                } label: {
                    Image(systemName: "car.fill")
                        .foregroundStyle(app.preferences.driveMode ? palette.accent : palette.textSecondary)
                        .padding(10)
                        .background(Circle().fill(palette.card))
                }
                NavigationLink {
                    SettingsScreen()
                } label: {
                    Image(systemName: "gearshape.fill")
                        .foregroundStyle(palette.textSecondary)
                        .padding(10)
                        .background(Circle().fill(palette.card))
                }
            }
        }
        .padding(.top, 8)
    }

    private var librarySubtitle: String {
        var parts = [Formatters.trackCount(app.repository.trackCount)]
        if app.preferences.jellyfinEnabled && app.repository.jellyfinCount > 0 {
            parts.append("\(app.repository.jellyfinCount) offline")
        }
        if app.repository.isScanning {
            parts.append("Scanning…")
        }
        return parts.joined(separator: " · ")
    }

    private var sectionContinue: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Continue")
            ForEach(app.continueListening.prefix(8)) { track in
                TrackRowView(
                    track: track,
                    isPlaying: app.player.state.current?.id == track.id,
                    onTap: { app.playTrack(track, queue: app.continueListening) },
                    onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                    onMore: { app.addToPlaylistTrack = track },
                    onHide: { app.hideTrack(track) }
                )
            }
        }
    }

    private var sectionPlaylists: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Playlists", actionTitle: "See all") {
                app.selectedTab = .playlists
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(app.visiblePlaylists.prefix(12)) { pl in
                        PlaylistCardView(playlist: pl) {
                            app.selectedTab = .playlists
                        }
                    }
                }
            }
        }
    }

    private var sectionRecent: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Recently added", actionTitle: "Play all") {
                app.playAll(app.recentlyAdded)
            }
            ForEach(app.recentlyAdded.prefix(12)) { track in
                TrackRowView(
                    track: track,
                    isPlaying: app.player.state.current?.id == track.id,
                    onTap: { app.playTrack(track, queue: app.recentlyAdded) },
                    onFavorite: { Task { await app.repository.toggleFavorite(trackId: track.id) } },
                    onMore: { app.addToPlaylistTrack = track },
                    onHide: { app.hideTrack(track) }
                )
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "music.note.house")
                .font(.system(size: 48))
                .foregroundStyle(AppColors.accentTeal)
            Text("No music yet")
                .font(.title3.bold())
                .foregroundStyle(palette.textPrimary)
            Text("Grant Media Library access or import files via Tools → Quick Connect.")
                .font(.subheadline)
                .foregroundStyle(palette.textSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await app.repository.scanLibrary(force: true) }
            } label: {
                Text("Scan library")
                    .font(.headline)
                    .foregroundStyle(AppColors.nightBlack)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(AppColors.accentTeal))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
}

struct DriveModeHome: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        VStack(spacing: 28) {
            HStack {
                Text("Drive Mode")
                    .font(.title2.bold())
                    .foregroundStyle(AppColors.accentTeal)
                Spacer()
                Button("Exit") {
                    app.preferences.driveMode = false
                }
                .foregroundStyle(palette.textSecondary)
            }
            .padding(.horizontal)

            Spacer()

            AlbumArtView(artworkUri: app.player.state.current?.artworkUri, size: 220, cornerRadius: 18)

            Text(app.player.state.current?.title ?? "Nothing playing")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(palette.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Text(app.player.state.current?.artist ?? "Tap play to resume")
                .font(.title3)
                .foregroundStyle(palette.textSecondary)

            HStack(spacing: 48) {
                Button { app.player.skipPrevious() } label: {
                    Image(systemName: "backward.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(palette.textPrimary)
                }
                Button { app.player.togglePlayPause() } label: {
                    Image(systemName: app.player.state.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 84))
                        .foregroundStyle(AppColors.accentTeal)
                }
                Button { app.player.skipNext() } label: {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(palette.textPrimary)
                }
            }

            if app.player.state.current == nil {
                Button {
                    Task { await app.player.resumeLastSession(autoPlay: true) }
                } label: {
                    Text("Resume last session")
                        .font(.headline)
                        .foregroundStyle(AppColors.nightBlack)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(Capsule().fill(AppColors.accentTeal))
                }
            }

            Spacer()
        }
        .padding()
    }
}
