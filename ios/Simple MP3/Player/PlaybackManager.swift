//
//  PlaybackManager.swift
//  Simple MP3
//
//  AVPlayer + Now Playing / lock screen / CarPlay remote commands.
//

import AVFoundation
import Combine
import MediaPlayer
import Observation
import UIKit

struct PlayerUiState: Equatable {
    var current: Track?
    var queue: [Track] = []
    var index: Int = 0
    var isPlaying: Bool = false
    var positionMs: Int64 = 0
    var durationMs: Int64 = 0
    var shuffle: Bool = false
    var repeatMode: RepeatMode = .off
    var isBuffering: Bool = false
    var isLive: Bool = false
    /// Encoded / indicated bitrate of the selected stream variant.
    var bitrateBps: Int64 = 0
    /// Observed network throughput from the access log.
    var throughputBps: Int64 = 0

    var hasTrack: Bool { current != nil }
    var progress: Double {
        guard durationMs > 0 else { return 0 }
        return min(1, Double(positionMs) / Double(durationMs))
    }

    var streamRateLabel: String? {
        Formatters.dataRateLabel(isLive: isLive, bitrateBps: bitrateBps, throughputBps: throughputBps)
    }
}

enum RepeatMode: Int, CaseIterable, Sendable {
    case off
    case all
    case one

    mutating func cycle() {
        self = RepeatMode(rawValue: (rawValue + 1) % 3) ?? .off
    }

    var systemImage: String {
        switch self {
        case .off: return "repeat"
        case .all: return "repeat"
        case .one: return "repeat.1"
        }
    }
}

@Observable
@MainActor
final class PlaybackManager {
    private(set) var state = PlayerUiState()

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var order: [Int] = []
    private var shuffleOrder: [Int] = []
    private var repository: MusicRepository?
    private var preferences: AppPreferences?
    private var lastRecordedTrackId: String?
    private var progressSaveTask: Task<Void, Never>?

    init() {
        configureSession()
        setupRemoteCommands()
        setupEndObserver()
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { time in
            Task { @MainActor [weak self] in
                self?.handleTime(time)
            }
        }
    }

    func attach(repository: MusicRepository, preferences: AppPreferences) {
        self.repository = repository
        self.preferences = preferences
    }

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            print("Audio session error: \(error)")
        }
    }

    // MARK: - Public controls

    func play(tracks: [Track], startIndex: Int = 0, positionMs: Int64 = 0) {
        guard !tracks.isEmpty else { return }
        let idx = min(max(0, startIndex), tracks.count - 1)
        state.queue = tracks
        state.index = idx
        state.shuffle = false
        order = Array(tracks.indices)
        shuffleOrder = order.shuffled()
        loadAndPlay(at: idx, positionMs: positionMs, autoPlay: true)
    }

    func play(_ track: Track, queue: [Track]? = nil) {
        let q = queue ?? [track]
        let idx = q.firstIndex(where: { $0.id == track.id }) ?? 0
        play(tracks: q, startIndex: idx)
    }

    /// Play a live network stream URL (progressive or HLS `.m3u8`) directly — AVPlayer
    /// handles HLS natively. Not saved to the library.
    func playStream(url: String, title: String) {
        let cleaned = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }
        let track = Track(
            id: "stream:\(cleaned.hashValue)",
            title: title.isEmpty ? "Stream" : title,
            artist: "Live stream",
            album: "Streams",
            uri: cleaned,
            source: .stream,
            isOffline: false
        )
        play(tracks: [track])
    }

    func togglePlayPause() {
        if state.isPlaying {
            pause()
        } else {
            resume()
        }
    }

    func pause() {
        player.pause()
        state.isPlaying = false
        updateNowPlayingPlayback()
        persistResume()
    }

    func resume() {
        if state.current == nil, let snap = preferences?.resumeSnapshot, snap.hasSession {
            Task { await resumeLastSession(autoPlay: true) }
            return
        }
        player.play()
        state.isPlaying = true
        updateNowPlayingPlayback()
    }

    func skipNext() {
        guard !state.queue.isEmpty else { return }
        let next = nextIndex()
        if let next {
            loadAndPlay(at: next, positionMs: 0, autoPlay: true)
        } else {
            pause()
            seek(toMs: 0)
        }
    }

    func skipPrevious() {
        if state.positionMs > 3000 {
            seek(toMs: 0)
            return
        }
        guard !state.queue.isEmpty else { return }
        if let prev = previousIndex() {
            loadAndPlay(at: prev, positionMs: 0, autoPlay: true)
        } else {
            seek(toMs: 0)
        }
    }

    func seek(toMs: Int64) {
        let seconds = Double(toMs) / 1000.0
        let time = CMTime(seconds: seconds, preferredTimescale: 600)
        player.seek(to: time)
        state.positionMs = toMs
        updateNowPlayingPlayback()
    }

    func seek(fraction: Double) {
        let ms = Int64(Double(state.durationMs) * fraction.clamped(to: 0...1))
        seek(toMs: ms)
    }

    func toggleShuffle() {
        state.shuffle.toggle()
        if state.shuffle {
            shuffleOrder = order.shuffled()
            // Keep current track first in shuffle sequence sense — rebuild around index
            if let cur = shuffleOrder.firstIndex(of: state.index) {
                shuffleOrder.remove(at: cur)
                shuffleOrder.insert(state.index, at: 0)
            }
        }
    }

    func cycleRepeat() {
        state.repeatMode.cycle()
    }

    func resumeLastSession(autoPlay: Bool) async {
        guard let preferences, let repository else { return }
        guard let snap = preferences.resumeSnapshot, snap.hasSession else { return }
        let tracks = await repository.tracks(ids: snap.trackIds)
        guard !tracks.isEmpty else { return }
        let idx = min(max(0, snap.index), tracks.count - 1)
        play(tracks: tracks, startIndex: idx, positionMs: snap.positionMs)
        if !autoPlay {
            pause()
        }
    }

    /// Sets Now Playing UI without loading audio — used by App Store screenshot demo mode.
    func presentDemoState(
        track: Track,
        queue: [Track],
        positionMs: Int64,
        isPlaying: Bool
    ) {
        let q = queue.isEmpty ? [track] : queue
        let idx = q.firstIndex(where: { $0.id == track.id }) ?? 0
        state.queue = q
        state.index = idx
        state.current = track
        state.durationMs = track.duration
        state.positionMs = min(max(0, positionMs), max(0, track.duration - 1))
        state.isPlaying = isPlaying
        state.isBuffering = false
        state.shuffle = true
        order = Array(q.indices)
        shuffleOrder = order
        player.pause()
        player.replaceCurrentItem(with: nil)
        publishNowPlaying(for: track)
        NotificationCenter.default.post(name: .playbackDidChange, object: nil)
    }

    // MARK: - Load

    private func loadAndPlay(at index: Int, positionMs: Int64, autoPlay: Bool) {
        guard state.queue.indices.contains(index) else { return }
        let track = state.queue[index]
        state.index = index
        state.current = track
        state.durationMs = track.duration
        state.positionMs = positionMs
        state.isBuffering = true
        state.isLive = track.isRemoteStream
        if !track.isRemoteStream {
            state.bitrateBps = 0
            state.throughputBps = 0
        }

        guard let url = resolveURL(for: track) else {
            state.isBuffering = false
            skipNext()
            return
        }

        let item = AVPlayerItem(url: url)
        // User-configurable buffer (parity with Android's DefaultLoadControl); 0 == system default.
        let forward = preferences?.bufferProfile.forwardSeconds ?? 0
        if forward > 0 {
            item.preferredForwardBufferDuration = forward
        }
        if track.isRemoteStream {
            // Prefer audio-only / lowest-resolution HLS variants so we don't pull video.
            item.preferredMaximumResolution = CGSize(width: 1, height: 1)
            item.preferredPeakBitRate = 512_000
        }
        player.automaticallyWaitsToMinimizeStalling = true
        player.replaceCurrentItem(with: item)
        if positionMs > 0 {
            let t = CMTime(seconds: Double(positionMs) / 1000.0, preferredTimescale: 600)
            player.seek(to: t)
        }
        if autoPlay {
            player.play()
            state.isPlaying = true
        }
        publishNowPlaying(for: track)
        Task {
            await repository?.recordPlay(trackId: track.id)
        }
        persistResume()
        NotificationCenter.default.post(name: .playbackDidChange, object: nil)
    }

    private func resolveURL(for track: Track) -> URL? {
        if let url = track.fileURL {
            if url.isFileURL {
                if FileManager.default.fileExists(atPath: url.path) {
                    return url
                }
                return nil
            }
            return url
        }
        return nil
    }

    private func nextIndex() -> Int? {
        guard !state.queue.isEmpty else { return nil }
        if state.repeatMode == .one {
            return state.index
        }
        let sequence = state.shuffle ? shuffleOrder : order
        guard let pos = sequence.firstIndex(of: state.index) else {
            return state.index + 1 < state.queue.count ? state.index + 1 : (state.repeatMode == .all ? 0 : nil)
        }
        let nextPos = pos + 1
        if nextPos < sequence.count {
            return sequence[nextPos]
        }
        return state.repeatMode == .all ? sequence.first : nil
    }

    private func previousIndex() -> Int? {
        guard !state.queue.isEmpty else { return nil }
        let sequence = state.shuffle ? shuffleOrder : order
        guard let pos = sequence.firstIndex(of: state.index) else {
            return state.index > 0 ? state.index - 1 : nil
        }
        if pos > 0 { return sequence[pos - 1] }
        return state.repeatMode == .all ? sequence.last : nil
    }

    private func handleTime(_ time: CMTime) {
        let ms = Int64(CMTimeGetSeconds(time) * 1000)
        if ms.isFinite {
            state.positionMs = max(0, ms)
        }
        if let item = player.currentItem {
            let dur = CMTimeGetSeconds(item.duration)
            if dur.isFinite && dur > 0 {
                state.durationMs = Int64(dur * 1000)
            }
        }
        state.isPlaying = player.rate > 0
        // Reflect real buffering rather than a synchronous guess at load time.
        state.isBuffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        updateStreamStats()
        updateNowPlayingPlayback()
    }

    /// Cap live streams to audio: disable any video tracks once the item is ready,
    /// and read indicated/observed bitrate from the access log.
    private func updateStreamStats() {
        let item = player.currentItem
        if state.current?.isRemoteStream == true, let item {
            for track in item.tracks where track.assetTrack?.mediaType == .video {
                track.isEnabled = false
            }
        }
        let indefinite = item?.duration.isIndefinite == true
        let live = state.current?.isRemoteStream == true || indefinite
        state.isLive = live
        guard live else {
            state.bitrateBps = 0
            state.throughputBps = 0
            return
        }
        guard let event = item?.accessLog()?.events.last else { return }
        let indicated = event.indicatedBitrate
        let observed = event.observedBitrate
        state.bitrateBps = indicated.isFinite && indicated > 0 ? Int64(indicated) : 0
        state.throughputBps = observed.isFinite && observed > 0 ? Int64(observed) : 0
    }

    private func setupEndObserver() {
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: nil,
            queue: .main
        ) { note in
            let endedItem = note.object as? AVPlayerItem
            Task { @MainActor [weak self] in
                guard let self else { return }
                if endedItem === self.player.currentItem {
                    self.skipNext()
                }
            }
        }
    }

    // MARK: - Now Playing / Remote

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.isEnabled = true
        center.pauseCommand.isEnabled = true
        center.togglePlayPauseCommand.isEnabled = true
        center.nextTrackCommand.isEnabled = true
        center.previousTrackCommand.isEnabled = true
        center.changePlaybackPositionCommand.isEnabled = true

        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayPause() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.skipNext() }
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.skipPrevious() }
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            Task { @MainActor in
                self?.seek(toMs: Int64(event.positionTime * 1000))
            }
            return .success
        }
    }

    private func publishNowPlaying(for track: Track) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPMediaItemPropertyAlbumTitle: track.album,
            MPMediaItemPropertyPlaybackDuration: Double(track.duration) / 1000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: Double(state.positionMs) / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate: state.isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0
        ]
        if let artURL = track.artworkUri.flatMap(URL.init(string:)),
           artURL.isFileURL,
           let data = try? Data(contentsOf: artURL),
           let image = UIImage(data: data) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        } else if track.id.hasPrefix("mp-"),
                  let pid = UInt64(track.id.dropFirst(3)) {
            let query = MPMediaQuery.songs()
            if let item = query.items?.first(where: { $0.persistentID == pid }),
               let artwork = item.artwork?.image(at: CGSize(width: 600, height: 600)) {
                info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: artwork.size) { _ in artwork }
            }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func updateNowPlayingPlayback() {
        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else { return }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = Double(state.positionMs) / 1000.0
        info[MPNowPlayingInfoPropertyPlaybackRate] = state.isPlaying ? 1.0 : 0.0
        if state.durationMs > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = Double(state.durationMs) / 1000.0
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func persistResume() {
        guard let preferences, preferences.resumeEnabled else { return }
        guard !state.queue.isEmpty else { return }
        let snap = ResumeSnapshot(
            trackIds: state.queue.map(\.id),
            index: state.index,
            positionMs: state.positionMs
        )
        preferences.saveResume(snap)
    }

    /// Called when CarPlay connects.
    func handleCarConnect() {
        guard let preferences else { return }
        if preferences.autoDriveModeOnCar {
            preferences.driveMode = true
        }
        if preferences.autoResumeOnDrive && !state.isPlaying {
            if state.current != nil {
                resume()
            } else {
                Task { await resumeLastSession(autoPlay: true) }
            }
        }
        NotificationCenter.default.post(name: .carPlayDidConnect, object: nil)
    }

    /// Called when CarPlay disconnects.
    func handleCarDisconnect() {
        guard let preferences else { return }
        if preferences.pauseOnCarDisconnect && state.isPlaying {
            pause()
        }
        if preferences.autoDriveModeOnCar {
            preferences.driveMode = false
        }
        NotificationCenter.default.post(name: .carPlayDidDisconnect, object: nil)
    }
}

extension Notification.Name {
    static let playbackDidChange = Notification.Name("SimpleMP3.playbackDidChange")
    static let carPlayDidConnect = Notification.Name("SimpleMP3.carPlayDidConnect")
    static let carPlayDidDisconnect = Notification.Name("SimpleMP3.carPlayDidDisconnect")
    static let libraryDidChange = Notification.Name("SimpleMP3.libraryDidChange")
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

private extension Int64 {
    var isFinite: Bool { self >= 0 && self < Int64.max / 4 }
}
