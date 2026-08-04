//
//  SharedComponents.swift
//  Simple MP3
//

import SwiftUI

struct AlbumArtView: View {
    let artworkUri: String?
    var size: CGFloat = 56
    var cornerRadius: CGFloat = 10

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(AppColors.nightElevated)
            if let artworkUri, let url = URL(string: artworkUri), url.isFileURL,
               let ui = UIImage(contentsOfFile: url.path) {
                Image(uiImage: ui)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "music.note")
                    .font(.system(size: size * 0.35))
                    .foregroundStyle(AppColors.accentTeal.opacity(0.7))
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

struct TrackRowView: View {
    let track: Track
    var isPlaying: Bool = false
    var onTap: () -> Void
    var onFavorite: (() -> Void)?
    var onMore: (() -> Void)?

    @Environment(\.appPalette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            AlbumArtView(artworkUri: track.artworkUri, size: 52)
            VStack(alignment: .leading, spacing: 3) {
                Text(track.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isPlaying ? AppColors.accentTeal : palette.textPrimary)
                    .lineLimit(1)
                Text("\(track.artist) · \(Formatters.duration(track.duration))")
                    .font(.system(size: 13))
                    .foregroundStyle(palette.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if let onFavorite {
                Button(action: onFavorite) {
                    Image(systemName: "heart")
                        .foregroundStyle(AppColors.accentCoral.opacity(0.9))
                }
                .buttonStyle(.plain)
            }
            if let onMore {
                Button(action: onMore) {
                    Image(systemName: "ellipsis")
                        .foregroundStyle(palette.textMuted)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

struct SectionHeader: View {
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?

    @Environment(\.appPalette) private var palette

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(palette.textPrimary)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(AppColors.accentTeal)
            }
        }
        .padding(.horizontal, 4)
    }
}

struct PlaylistCardView: View {
    let playlist: PlaylistMeta
    var onTap: () -> Void

    @Environment(\.appPalette) private var palette

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [AppColors.deepViolet, AppColors.nightCard],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    AlbumArtView(artworkUri: playlist.displayCover, size: 120, cornerRadius: 12)
                        .opacity(playlist.displayCover == nil ? 0 : 1)
                    if playlist.displayCover == nil {
                        Image(systemName: systemIcon)
                            .font(.system(size: 36))
                            .foregroundStyle(AppColors.accentTeal)
                    }
                }
                .frame(width: 140, height: 140)

                Text(playlist.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(palette.textPrimary)
                    .lineLimit(1)
                    .frame(width: 140, alignment: .leading)
                Text(Formatters.trackCount(playlist.trackCount))
                    .font(.system(size: 12))
                    .foregroundStyle(palette.textMuted)
            }
        }
        .buttonStyle(.plain)
    }

    private var systemIcon: String {
        switch playlist.systemType {
        case SystemPlaylist.favorites.rawValue: return "heart.fill"
        case SystemPlaylist.recentlyPlayed.rawValue: return "clock.fill"
        case SystemPlaylist.jellyfinOffline.rawValue: return "server.rack"
        case SystemPlaylist.youtubeDownloads.rawValue: return "play.rectangle.fill"
        case SystemPlaylist.lanImports.rawValue: return "wifi"
        default: return "music.note.list"
        }
    }
}

struct MiniPlayerBar: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        if let track = app.player.state.current {
            HStack(spacing: 12) {
                AlbumArtView(artworkUri: track.artworkUri, size: 44, cornerRadius: 8)
                VStack(alignment: .leading, spacing: 2) {
                    Text(track.title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(palette.textPrimary)
                        .lineLimit(1)
                    Text(track.artist)
                        .font(.system(size: 12))
                        .foregroundStyle(palette.textSecondary)
                        .lineLimit(1)
                }
                Spacer()
                Button {
                    app.player.skipPrevious()
                } label: {
                    Image(systemName: "backward.fill")
                        .foregroundStyle(palette.textPrimary)
                }
                Button {
                    app.player.togglePlayPause()
                } label: {
                    Image(systemName: app.player.state.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(AppColors.accentTeal)
                        .frame(width: 36, height: 36)
                }
                Button {
                    app.player.skipNext()
                } label: {
                    Image(systemName: "forward.fill")
                        .foregroundStyle(palette.textPrimary)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.card.opacity(0.95))
                    .shadow(color: .black.opacity(0.35), radius: 12, y: 4)
            )
            .padding(.horizontal, 12)
            .padding(.bottom, 4)
            .onTapGesture { app.showNowPlaying = true }
        }
    }
}

struct NowPlayingSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = app.player.state
        NavigationStack {
            VStack(spacing: 24) {
                Capsule()
                    .fill(palette.textMuted.opacity(0.4))
                    .frame(width: 40, height: 5)
                    .padding(.top, 8)

                AlbumArtView(artworkUri: state.current?.artworkUri, size: 280, cornerRadius: 20)
                    .shadow(color: AppColors.accentTeal.opacity(0.2), radius: 30)

                VStack(spacing: 6) {
                    Text(state.current?.title ?? "Nothing playing")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(palette.textPrimary)
                        .multilineTextAlignment(.center)
                    Text(state.current?.artist ?? "")
                        .font(.system(size: 16))
                        .foregroundStyle(palette.textSecondary)
                }
                .padding(.horizontal)

                VStack(spacing: 8) {
                    Slider(
                        value: Binding(
                            get: { state.progress },
                            set: { app.player.seek(fraction: $0) }
                        ),
                        in: 0...1
                    )
                    .tint(AppColors.accentTeal)
                    HStack {
                        Text(Formatters.duration(state.positionMs))
                        Spacer()
                        Text(Formatters.duration(state.durationMs))
                    }
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(palette.textMuted)
                }
                .padding(.horizontal, 24)

                HStack(spacing: 36) {
                    Button { app.player.toggleShuffle() } label: {
                        Image(systemName: "shuffle")
                            .foregroundStyle(state.shuffle ? AppColors.accentTeal : palette.textMuted)
                    }
                    Button { app.player.skipPrevious() } label: {
                        Image(systemName: "backward.fill").font(.title)
                            .foregroundStyle(palette.textPrimary)
                    }
                    Button { app.player.togglePlayPause() } label: {
                        Image(systemName: state.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 68))
                            .foregroundStyle(AppColors.accentTeal)
                    }
                    Button { app.player.skipNext() } label: {
                        Image(systemName: "forward.fill").font(.title)
                            .foregroundStyle(palette.textPrimary)
                    }
                    Button { app.player.cycleRepeat() } label: {
                        Image(systemName: state.repeatMode.systemImage)
                            .foregroundStyle(state.repeatMode == .off ? palette.textMuted : AppColors.accentTeal)
                    }
                }

                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(NightBackground())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Queue") { app.showQueue = true }
                        .foregroundStyle(AppColors.accentTeal)
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }
                        .foregroundStyle(palette.textSecondary)
                }
            }
        }
    }
}

struct QueueSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(app.player.state.queue.enumerated()), id: \.element.id) { index, track in
                    TrackRowView(
                        track: track,
                        isPlaying: index == app.player.state.index,
                        onTap: {
                            app.player.play(tracks: app.player.state.queue, startIndex: index)
                        }
                    )
                    .listRowBackground(palette.card)
                }
            }
            .scrollContentBackground(.hidden)
            .background(NightBackground())
            .navigationTitle("Queue")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
