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
                    StreamsScreen()
                } label: {
                    toolRow(
                        icon: "dot.radiowaves.left.and.right",
                        title: "Streams",
                        subtitle: "Play or save an .m3u8 / HLS audio stream"
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

struct QuickConnectScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var imports: [Track] = []

    var body: some View {
        List {
            Section {
                Text("Start a local web portal. On another device on the same Wi‑Fi, open the URL and enter the access code. Files appear in LAN Imports and play offline / on CarPlay.")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
                Text("Leaving this screen stops the portal.")
                    .font(.caption)
                    .foregroundStyle(palette.textMuted)
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

            Section("LAN Imports") {
                ForEach(imports) { track in
                    TrackRowView(
                        track: track,
                        isPlaying: app.player.state.current?.id == track.id,
                        onTap: { app.playTrack(track, queue: imports) },
                        onMore: { app.addToPlaylistTrack = track },
                        onHide: { app.hideTrack(track) }
                    )
                    .listRowBackground(Color.clear)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            Task {
                                await app.repository.deleteTrack(id: track.id)
                                await reload()
                            }
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Quick Connect")
        .onAppear { app.quickConnect.refreshAddresses() }
        .onDisappear {
            // Navigating away stops the portal — see the note in the header section.
            app.quickConnect.stop()
        }
        .task { await reload() }
        .onChange(of: app.quickConnect.lastUploadName) { _, _ in
            Task { await reload() }
        }
    }

    private func reload() async {
        imports = await app.repository.tracks(source: .lan)
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

            Section {
                Picker("Playback buffer", selection: $prefs.bufferProfile) {
                    ForEach(BufferProfile.allCases, id: \.self) { profile in
                        Text(profile.label).tag(profile)
                    }
                }
            } header: {
                Text("Playback")
            } footer: {
                Text("Larger buffers rebuffer less on flaky networks (e.g. streams over cellular) at the cost of a longer initial load. Applies to the next track.")
            }

            Section("CarPlay & Drive") {
                Toggle("Drive Mode", isOn: $prefs.driveMode)
                Toggle("Show weather in CarPlay", isOn: $prefs.showCarPlayWeather)
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
                NavigationLink {
                    HiddenSongsScreen()
                } label: {
                    LabeledContent("Hidden songs", value: "\(app.repository.hiddenTracks.count)")
                }
            }

            Section("About") {
                LabeledContent("App", value: "Jerry's Simple MP3")
                LabeledContent("Version", value: appVersion)
                Link("Privacy Policy", destination: URL(string: "https://karpilabs.io/privacy")!)
                Link("Terms of Use", destination: URL(string: "https://karpilabs.io/terms")!)
                Text("©2026 KarpiLabs LLC. All rights reserved.")
                    .font(.caption)
                    .foregroundStyle(palette.textMuted)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Settings")
    }

    private var appVersion: String {
        let bundle = Bundle.main
        let shortVersion = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(shortVersion) (\(build))"
    }
}

struct StreamsScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    @State private var url = ""
    @State private var title = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var saved: [Track] = []

    var body: some View {
        List {
            Section {
                Text("Paste an .m3u8 (HLS) or direct audio URL. Play it live, or save a copy offline (.m4a) for CarPlay.")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
                TextField("https://…/playlist.m3u8", text: $url)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                TextField("Title (optional)", text: $title)

                Button {
                    app.player.playStream(url: url, title: title)
                } label: {
                    Label("Play live", systemImage: "play.fill")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
                .disabled(url.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)

                Button {
                    Task { await save() }
                } label: {
                    if isSaving {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Label("Save offline", systemImage: "arrow.down.circle")
                            .frame(maxWidth: .infinity)
                    }
                }
                .disabled(url.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(AppColors.accentCoral)
                }
            } header: {
                Text("Stream").foregroundStyle(palette.textMuted)
            }

            Section {
                if saved.isEmpty {
                    Text("No saved streams yet.")
                        .font(.caption)
                        .foregroundStyle(palette.textSecondary)
                } else {
                    ForEach(saved) { track in
                        TrackRowView(
                            track: track,
                            isPlaying: app.player.state.current?.id == track.id,
                            onTap: { app.playTrack(track, queue: saved) },
                            onMore: { app.addToPlaylistTrack = track },
                            onHide: { app.hideTrack(track) }
                        )
                        .listRowBackground(Color.clear)
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                Task {
                                    await app.repository.deleteTrack(id: track.id)
                                    await reload()
                                }
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
            } header: {
                Text("Saved Streams").foregroundStyle(palette.textMuted)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Streams")
        .task { await reload() }
    }

    private func reload() async {
        saved = await app.repository.tracks(source: .stream)
    }

    private func save() async {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        let key = "stream:\(abs(trimmed.hashValue))"
        let dir = await app.repository.mediaDirectory(for: .stream)
        let dest = dir.appendingPathComponent("\(abs(trimmed.hashValue)).m4a")
        let name = title.trimmingCharacters(in: .whitespaces).isEmpty
            ? defaultTitle(from: trimmed)
            : title.trimmingCharacters(in: .whitespaces)

        do {
            let file = try await StreamSaver.save(urlString: trimmed, to: dest)
            let durMs = await StreamSaver.durationMs(of: file)
            let track = Track(
                id: key,
                title: name,
                artist: "Stream",
                album: "Saved Streams",
                uri: file.absoluteString,
                duration: durMs,
                source: .stream,
                externalId: key,
                isOffline: true
            )
            await app.repository.upsertTrack(track)
            if let playlist = await app.repository.systemPlaylist(.savedStreams) {
                await app.repository.addToPlaylist(playlistId: playlist.id, trackId: track.id)
            }
            url = ""
            title = ""
            await reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func defaultTitle(from urlString: String) -> String {
        let last = urlString
            .components(separatedBy: "?").first?
            .components(separatedBy: "/").last?
            .components(separatedBy: ".").first ?? ""
        return last.isEmpty ? "Saved stream" : last
    }
}

struct HiddenSongsScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette

    var body: some View {
        List {
            if app.repository.hiddenTracks.isEmpty {
                ContentUnavailableView(
                    "No hidden songs",
                    systemImage: "eye.slash",
                    description: Text("Long-press a song and choose “Hide from Library” to keep junk like ringtones out of your library. They'll show up here so you can bring them back.")
                )
                .foregroundStyle(palette.textSecondary)
            } else {
                ForEach(app.repository.hiddenTracks) { track in
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(track.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(palette.textPrimary)
                                .lineLimit(1)
                            Text(track.artist)
                                .font(.system(size: 13))
                                .foregroundStyle(palette.textSecondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Button("Unhide") {
                            app.unhideTrack(track)
                        }
                        .foregroundStyle(AppColors.accentTeal)
                    }
                    .listRowBackground(Color.clear)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle("Hidden songs")
    }
}
