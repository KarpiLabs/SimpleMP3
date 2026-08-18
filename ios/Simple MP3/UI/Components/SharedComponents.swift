//
//  SharedComponents.swift
//  Simple MP3
//

import SwiftUI

struct AlbumArtView: View {
    var artworkUri: String?
    var trackId: String? = nil
    var size: CGFloat = 56
    var cornerRadius: CGFloat = 10

    @Environment(\.appPalette) private var palette

    init(artworkUri: String?, trackId: String? = nil, size: CGFloat = 56, cornerRadius: CGFloat = 10) {
        self.artworkUri = artworkUri
        self.trackId = trackId
        self.size = size
        self.cornerRadius = cornerRadius
    }

    init(track: Track, size: CGFloat = 56, cornerRadius: CGFloat = 10) {
        self.artworkUri = track.artworkUri
        self.trackId = track.id
        self.size = size
        self.cornerRadius = cornerRadius
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(palette.elevated)
            if let ui = MediaArtwork.image(artworkUri: artworkUri, trackId: trackId, side: size) {
                Image(uiImage: ui)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "music.note")
                    .font(.system(size: size * 0.35))
                    .foregroundStyle(palette.accent.opacity(0.7))
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
    /// Long-press → "Hide from Library". Nil suppresses the context menu entirely.
    var onHide: (() -> Void)? = nil

    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            AlbumArtView(track: track, size: 52)
            VStack(alignment: .leading, spacing: 3) {
                Text(track.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isPlaying ? palette.accent : palette.textPrimary)
                    .lineLimit(1)
                Text("\(track.artist) · \(Formatters.duration(track.duration))")
                    .font(.system(size: 13))
                    .foregroundStyle(palette.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if let onFavorite {
                Button(action: onFavorite) {
                    Image(systemName: app.repository.favoriteIds.contains(track.id) ? "heart.fill" : "heart")
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
        .contextMenu {
            if onHide != nil {
                if let onMore {
                    Button {
                        onMore()
                    } label: {
                        Label("Add to Playlist", systemImage: "text.badge.plus")
                    }
                }
                if let onFavorite {
                    Button {
                        onFavorite()
                    } label: {
                        Label(
                            app.repository.favoriteIds.contains(track.id) ? "Unlike" : "Like",
                            systemImage: app.repository.favoriteIds.contains(track.id) ? "heart.slash" : "heart"
                        )
                    }
                }
                Button(role: .destructive) {
                    onHide?()
                } label: {
                    Label("Hide from Library", systemImage: "eye.slash")
                }
            }
        }
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
                    .foregroundStyle(palette.accent)
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
                                colors: palette.isDark
                                    ? [AppColors.deepViolet, AppColors.nightCard]
                                    : [AppColors.dayElevated, AppColors.dayCard],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    AlbumArtView(artworkUri: playlist.displayCover, size: 120, cornerRadius: 12)
                        .opacity(playlist.displayCover == nil ? 0 : 1)
                    if playlist.displayCover == nil {
                        Image(systemName: systemIcon)
                            .font(.system(size: 36))
                            .foregroundStyle(palette.accent)
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
                AlbumArtView(track: track, size: 44, cornerRadius: 8)
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
                        .foregroundStyle(palette.accent)
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

                AlbumArtView(
                    artworkUri: state.current?.artworkUri,
                    trackId: state.current?.id,
                    size: 280,
                    cornerRadius: 20
                )
                    .shadow(color: palette.accent.opacity(0.2), radius: 30)
                    .gesture(
                        DragGesture(minimumDistance: 40)
                            .onEnded { value in
                                if value.translation.width < -50 {
                                    app.player.skipNext()
                                } else if value.translation.width > 50 {
                                    app.player.skipPrevious()
                                }
                            }
                    )

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
                    .tint(palette.accent)
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
                            .foregroundStyle(state.shuffle ? palette.accent : palette.textMuted)
                    }
                    Button { app.player.skipPrevious() } label: {
                        Image(systemName: "backward.fill").font(.title)
                            .foregroundStyle(palette.textPrimary)
                    }
                    Button { app.player.togglePlayPause() } label: {
                        Image(systemName: state.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 68))
                            .foregroundStyle(palette.accent)
                    }
                    Button { app.player.skipNext() } label: {
                        Image(systemName: "forward.fill").font(.title)
                            .foregroundStyle(palette.textPrimary)
                    }
                    Button { app.player.cycleRepeat() } label: {
                        Image(systemName: state.repeatMode.systemImage)
                            .foregroundStyle(state.repeatMode == .off ? palette.textMuted : palette.accent)
                    }
                }

                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(AppBackground())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Queue") { app.showQueue = true }
                        .foregroundStyle(palette.accent)
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
            .background(AppBackground())
            .navigationTitle("Queue")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
