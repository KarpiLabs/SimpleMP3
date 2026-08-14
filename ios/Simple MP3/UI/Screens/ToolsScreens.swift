//
//  ToolsScreens.swift
//  Simple MP3
//

import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct ToolsScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        List {
            Section {
                NavigationLink {
                    JellyfinScreen()
                } label: {
                    toolRow(
                        icon: "server.rack",
                        title: "Jellyfin",
                        subtitle: app.jellyfin.isLoggedIn
                            ? "Signed in · \(app.repository.jellyfinCount) offline"
                            : (app.preferences.jellyfinEnabled ? "Not signed in" : "Connect your server")
                    )
                }
                NavigationLink {
                    YoutubeScreen()
                } label: {
                    toolRow(
                        icon: "play.rectangle.fill",
                        title: "YouTube → Audio",
                        subtitle: "\(app.repository.youtubeCount) downloads"
                    )
                }
                NavigationLink {
                    QuickConnectScreen()
                } label: {
                    toolRow(
                        icon: "wifi",
                        title: "Quick Connect",
                        subtitle: app.quickConnect.isRunning ? "Portal running" : "LAN file upload"
                    )
                }
            } header: {
                Text("Import & sync")
                    .foregroundStyle(palette.textMuted)
            }

            Section {
                NavigationLink {
                    SettingsScreen()
                } label: {
                    toolRow(icon: "gearshape.fill", title: "Settings", subtitle: "Drive mode, theme, CarPlay")
                }
            } header: {
                Text("App")
                    .foregroundStyle(palette.textMuted)
            }
        }
        .scrollContentBackground(.hidden)
        .listStyle(.insetGrouped)
    }

    private func toolRow(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundStyle(AppColors.accentTeal)
                .frame(width: 36, height: 36)
                .background(Circle().fill(AppColors.deepViolet.opacity(0.6)))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(palette.textPrimary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct JellyfinScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var server = ""
    @State private var user = ""
    @State private var password = ""
    @State private var search = ""

    var body: some View {
        List {
            if !app.jellyfin.isLoggedIn {
                Section("Server") {
                    TextField("http://jellyfin.local:8096", text: $server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Username", text: $user)
                        .textInputAutocapitalization(.never)
                    SecureField("Password", text: $password)
                    Button {
                        Task {
                            await app.jellyfin.login(serverUrl: server, username: user, password: password)
                        }
                    } label: {
                        if app.jellyfin.isLoading {
                            ProgressView()
                        } else {
                            Text("Sign in")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(server.isEmpty || user.isEmpty || app.jellyfin.isLoading)
                }
            } else {
                Section {
                    LabeledContent("User", value: app.jellyfin.session?.userName ?? "")
                    LabeledContent("Server", value: app.jellyfin.session?.serverUrl ?? "")
                    Button("Refresh library") {
                        Task { await app.jellyfin.loadLibrary() }
                    }
                    Button("Download all listed") {
                        Task { await app.jellyfin.downloadAllVisible() }
                    }
                    Button("Sign out", role: .destructive) {
                        app.jellyfin.logout()
                    }
                }

                if app.jellyfin.syncProgress.isActive {
                    Section("Progress") {
                        ProgressView(value: app.jellyfin.syncProgress.fraction) {
                            Text(app.jellyfin.syncProgress.currentTitle)
                        }
                        Text("\(app.jellyfin.syncProgress.current)/\(app.jellyfin.syncProgress.total)")
                            .font(.caption)
                            .foregroundStyle(palette.textMuted)
                    }
                }

                Section("Server tracks") {
                    TextField("Search", text: $search)
                        .onSubmit {
                            Task {
                                if search.isEmpty {
                                    await app.jellyfin.loadLibrary()
                                } else {
                                    await app.jellyfin.search(search)
                                }
                            }
                        }
                    ForEach(app.jellyfin.remoteItems) { item in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(item.title)
                                    .foregroundStyle(palette.textPrimary)
                                Text("\(item.artistName) · \(Formatters.duration(item.durationMs))")
                                    .font(.caption)
                                    .foregroundStyle(palette.textSecondary)
                            }
                            Spacer()
                            Button {
                                Task { await app.jellyfin.playRemote(item, player: app.player) }
                            } label: {
                                Image(systemName: "play.fill")
                                    .foregroundStyle(palette.accent)
                            }
                            Button {
                                Task { await app.jellyfin.downloadItem(item) }
                            } label: {
                                Image(systemName: "arrow.down.circle")
                                    .foregroundStyle(AppColors.accentViolet)
                            }
                        }
                    }
                }
            }

            if let msg = app.jellyfin.statusMessage {
                Section {
                    Text(msg).font(.caption).foregroundStyle(palette.textSecondary)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Jellyfin")
        .onAppear {
            if server.isEmpty { server = app.preferences.jellyfinServerUrl }
            if user.isEmpty { user = app.preferences.jellyfinUser }
            app.preferences.jellyfinEnabled = true
        }
        .task {
            if app.jellyfin.isLoggedIn {
                await app.jellyfin.loadLibrary()
            }
        }
    }
}

struct YoutubeScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var link = ""
    @State private var showImporter = false
    @State private var pendingTitle: String?
    @State private var pendingVideoId: String?
    @State private var downloads: [Track] = []

    var body: some View {
        List {
            Section {
                Text("App Store builds cannot extract YouTube streams in-process. Paste a link for metadata, then import the audio file (Files / Share) into YouTube Downloads.")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
                TextField("YouTube URL or video id", text: $link)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Look up title") {
                    Task {
                        pendingVideoId = YouTubeService.videoId(from: link)
                        pendingTitle = await app.youtube.fetchTitle(for: link)
                        await app.youtube.prepareImportHint(for: link)
                    }
                }
                Button("Import audio file…") {
                    showImporter = true
                }
                .foregroundStyle(palette.accent)
            }

            if let msg = app.youtube.statusMessage {
                Section {
                    Text(msg).font(.caption).foregroundStyle(palette.textSecondary)
                }
            }

            Section("Downloads") {
                ForEach(downloads) { track in
                    TrackRowView(
                        track: track,
                        isPlaying: app.player.state.current?.id == track.id,
                        onTap: { app.playTrack(track, queue: downloads) },
                        onMore: { app.addToPlaylistTrack = track }
                    )
                    .listRowBackground(Color.clear)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("YouTube")
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.audio, .mpeg4Audio, .aiff, .wav],
            allowsMultipleSelection: true
        ) { result in
            if case .success(let urls) = result {
                Task {
                    for url in urls {
                        await app.youtube.importAudioFile(
                            from: url,
                            videoId: pendingVideoId,
                            titleOverride: pendingTitle
                        )
                    }
                    await reload()
                }
            }
        }
        .task { await reload() }
    }

    private func reload() async {
        downloads = await app.repository.tracks(source: .youtube)
    }
}

struct QuickConnectScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        List {
            Section {
                Text("Start a local web portal. On another device on the same Wi‑Fi, open the URL and enter the access code. Files appear in LAN Imports and play offline / on CarPlay.")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
                if app.quickConnect.isLockedOut {
                    Text("Portal locked after too many wrong codes. Stop and start again for a new code.")
                        .foregroundStyle(AppColors.accentCoral)
                    Button("Stop portal", role: .destructive) {
                        app.quickConnect.stop()
                    }
                } else if app.quickConnect.isRunning {
                    VStack(spacing: 6) {
                        Text("ACCESS CODE")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(palette.textMuted)
                            .tracking(1.5)
                        Text(app.quickConnect.accessCode)
                            .font(.system(size: 36, weight: .bold, design: .monospaced))
                            .foregroundStyle(palette.textPrimary)
                            .textSelection(.enabled)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    Button("Copy code") {
                        UIPasteboard.general.string = app.quickConnect.accessCode
                    }
                    .foregroundStyle(palette.accent)
                    ForEach(app.quickConnect.portalURLs, id: \.self) { url in
                        Text(url)
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(palette.accent)
                            .textSelection(.enabled)
                    }
                    Button("Stop portal", role: .destructive) {
                        app.quickConnect.stop()
                    }
                } else {
                    Button("Start portal") {
                        app.quickConnect.start()
                    }
                    .foregroundStyle(palette.accent)
                }
            }

            if let msg = app.quickConnect.statusMessage {
                Section {
                    Text(msg).foregroundStyle(palette.textSecondary)
                    if let last = app.quickConnect.lastUploadName {
                        Text("Last upload: \(last)")
                            .foregroundStyle(palette.textPrimary)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Quick Connect")
        .onAppear { app.quickConnect.refreshAddresses() }
        .onDisappear {
            // Leave running if user wants continuous portal — don't auto-stop.
        }
    }
}

struct SettingsScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        @Bindable var prefs = app.preferences
        List {
            Section("Appearance") {
                Picker("Theme", selection: $prefs.themeMode) {
                    ForEach(ThemeMode.allCases, id: \.self) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
            }

            Section("CarPlay & Drive") {
                Toggle("Drive Mode", isOn: $prefs.driveMode)
                Toggle("Auto Drive Mode on CarPlay", isOn: $prefs.autoDriveModeOnCar)
                Toggle("Auto-resume when car connects", isOn: $prefs.autoResumeOnDrive)
                Toggle("Pause when car disconnects", isOn: $prefs.pauseOnCarDisconnect)
                Toggle("Remember playback position", isOn: $prefs.resumeEnabled)
            }

            Section("Downloads") {
                Toggle("Enable Jellyfin integration", isOn: $prefs.jellyfinEnabled)
                Toggle("Wi‑Fi only downloads", isOn: $prefs.wifiOnlyDownloads)
            }

            Section("Library") {
                Button("Rescan media library") {
                    Task { await app.repository.scanLibrary(force: true) }
                }
                LabeledContent("Tracks", value: "\(app.repository.trackCount)")
            }

            Section("About") {
                LabeledContent("App", value: "Simple MP3 for iOS")
                LabeledContent("CarPlay", value: "Audio app")
                Text("Port of the Android Simple MP3 player by KarpiLabs. Local library, playlists, Jellyfin offline, Quick Connect, and Apple CarPlay.")
                    .font(.caption)
                    .foregroundStyle(palette.textMuted)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Settings")
    }
}
