//
//  AppPreferences.swift
//  Simple MP3
//

import Foundation
import Observation

/// Playback buffer size. Bigger buffers rebuffer less on flaky car networks at the
/// cost of a longer initial load. `forwardSeconds` feeds
/// `AVPlayerItem.preferredForwardBufferDuration`. Mirrors Android's BufferProfile.
enum BufferProfile: String, CaseIterable, Sendable {
    case small
    case balanced
    case large

    var label: String {
        switch self {
        case .small: return "Small"
        case .balanced: return "Balanced"
        case .large: return "Large"
        }
    }

    /// Preferred forward buffer in seconds (0 == system default for balanced).
    var forwardSeconds: Double {
        switch self {
        case .small: return 15
        case .balanced: return 0
        case .large: return 60
        }
    }
}

@Observable
@MainActor
final class AppPreferences {
    private let defaults = UserDefaults.standard

    private enum Key {
        static let driveMode = "driveMode"
        static let showCarPlayWeather = "showCarPlayWeather"
        static let autoDriveModeOnCar = "autoDriveModeOnCar"
        static let autoResumeOnDrive = "autoResumeOnDrive"
        static let pauseOnCarDisconnect = "pauseOnCarDisconnect"
        static let largeFileOptimize = "largeFileOptimize"
        static let largeFileColdPack = "largeFileColdPack"
        static let jellyfinEnabled = "jellyfinEnabled"
        static let wifiOnlyDownloads = "wifiOnlyDownloads"
        static let resumeEnabled = "resumeEnabled"
        static let themeMode = "themeMode"
        static let bufferProfile = "bufferProfile"
        static let normalizeVolume = "normalizeVolume"
        static let normalizePreampDb = "normalizePreampDb"
        static let scrobbleProvider = "scrobbleProvider"
        static let scrobbleEnabled = "scrobbleEnabled"
        static let listenBrainzToken = "listenBrainzToken"
        static let lastfmSessionKey = "lastfmSessionKey"
        static let lastfmUsername = "lastfmUsername"
        static let lastfmApiKey = "lastfmApiKey"
        static let lastfmApiSecret = "lastfmApiSecret"
        static let scrobbleQueue = "scrobbleQueue"
        static let lastLibraryScanMs = "lastLibraryScanMs"
        static let resumeSnapshot = "resumeSnapshot"
        static let jellyfinServerUrl = "jellyfinServerUrl"
        static let jellyfinUser = "jellyfinUser"
        static let jellyfinToken = "jellyfinToken"
        static let jellyfinUserId = "jellyfinUserId"
        static let jellyfinDeviceId = "jellyfinDeviceId"
    }

    var driveMode: Bool {
        didSet { defaults.set(driveMode, forKey: Key.driveMode) }
    }
    var showCarPlayWeather: Bool {
        didSet { defaults.set(showCarPlayWeather, forKey: Key.showCarPlayWeather) }
    }
    var autoDriveModeOnCar: Bool {
        didSet { defaults.set(autoDriveModeOnCar, forKey: Key.autoDriveModeOnCar) }
    }
    var autoResumeOnDrive: Bool {
        didSet { defaults.set(autoResumeOnDrive, forKey: Key.autoResumeOnDrive) }
    }
    var pauseOnCarDisconnect: Bool {
        didSet { defaults.set(pauseOnCarDisconnect, forKey: Key.pauseOnCarDisconnect) }
    }
    var largeFileOptimize: Bool {
        didSet { defaults.set(largeFileOptimize, forKey: Key.largeFileOptimize) }
    }
    var largeFileColdPack: Bool {
        didSet { defaults.set(largeFileColdPack, forKey: Key.largeFileColdPack) }
    }
    var jellyfinEnabled: Bool {
        didSet { defaults.set(jellyfinEnabled, forKey: Key.jellyfinEnabled) }
    }
    var wifiOnlyDownloads: Bool {
        didSet { defaults.set(wifiOnlyDownloads, forKey: Key.wifiOnlyDownloads) }
    }
    var resumeEnabled: Bool {
        didSet { defaults.set(resumeEnabled, forKey: Key.resumeEnabled) }
    }
    var themeMode: ThemeMode {
        didSet { defaults.set(themeMode.rawValue, forKey: Key.themeMode) }
    }
    var bufferProfile: BufferProfile {
        didSet { defaults.set(bufferProfile.rawValue, forKey: Key.bufferProfile) }
    }
    /// Even out loudness across tracks using ReplayGain tags.
    var normalizeVolume: Bool {
        didSet { defaults.set(normalizeVolume, forKey: Key.normalizeVolume) }
    }
    /// Extra gain (dB) on top of ReplayGain; clamped to [-12, 12].
    var normalizePreampDb: Int {
        didSet { defaults.set(normalizePreampDb, forKey: Key.normalizePreampDb) }
    }
    /// none | listenbrainz | lastfm
    var scrobbleProvider: String {
        didSet { defaults.set(scrobbleProvider, forKey: Key.scrobbleProvider) }
    }
    var scrobbleEnabled: Bool {
        didSet { defaults.set(scrobbleEnabled, forKey: Key.scrobbleEnabled) }
    }
    // Credentials live in the Keychain, not plaintext UserDefaults.
    var listenBrainzToken: String {
        didSet { Keychain.set(listenBrainzToken, for: Key.listenBrainzToken) }
    }
    var lastfmSessionKey: String {
        didSet { Keychain.set(lastfmSessionKey, for: Key.lastfmSessionKey) }
    }
    var lastfmUsername: String {
        didSet { defaults.set(lastfmUsername, forKey: Key.lastfmUsername) }
    }
    var lastfmApiKey: String {
        didSet { defaults.set(lastfmApiKey, forKey: Key.lastfmApiKey) }
    }
    var lastfmApiSecret: String {
        didSet { Keychain.set(lastfmApiSecret, for: Key.lastfmApiSecret) }
    }

    var jellyfinServerUrl: String {
        didSet { defaults.set(jellyfinServerUrl, forKey: Key.jellyfinServerUrl) }
    }
    var jellyfinUser: String {
        didSet { defaults.set(jellyfinUser, forKey: Key.jellyfinUser) }
    }
    var jellyfinToken: String {
        didSet { defaults.set(jellyfinToken, forKey: Key.jellyfinToken) }
    }
    var jellyfinUserId: String {
        didSet { defaults.set(jellyfinUserId, forKey: Key.jellyfinUserId) }
    }
    var jellyfinDeviceId: String {
        didSet { defaults.set(jellyfinDeviceId, forKey: Key.jellyfinDeviceId) }
    }

    var resumeSnapshot: ResumeSnapshot?

    init() {
        let d = UserDefaults.standard
        driveMode = d.bool(forKey: Key.driveMode)
        showCarPlayWeather = d.object(forKey: Key.showCarPlayWeather) as? Bool ?? true
        autoDriveModeOnCar = d.object(forKey: Key.autoDriveModeOnCar) as? Bool ?? true
        autoResumeOnDrive = d.object(forKey: Key.autoResumeOnDrive) as? Bool ?? true
        pauseOnCarDisconnect = d.object(forKey: Key.pauseOnCarDisconnect) as? Bool ?? true
        largeFileOptimize = d.object(forKey: Key.largeFileOptimize) as? Bool ?? true
        largeFileColdPack = d.object(forKey: Key.largeFileColdPack) as? Bool ?? true
        jellyfinEnabled = d.bool(forKey: Key.jellyfinEnabled)
        wifiOnlyDownloads = d.object(forKey: Key.wifiOnlyDownloads) as? Bool ?? true
        resumeEnabled = d.object(forKey: Key.resumeEnabled) as? Bool ?? true
        if let raw = d.string(forKey: Key.themeMode), let mode = ThemeMode(rawValue: raw) {
            themeMode = mode
        } else {
            themeMode = .system
        }
        if let raw = d.string(forKey: Key.bufferProfile), let profile = BufferProfile(rawValue: raw) {
            bufferProfile = profile
        } else {
            bufferProfile = .balanced
        }
        normalizeVolume = d.bool(forKey: Key.normalizeVolume)
        normalizePreampDb = min(12, max(-12, d.integer(forKey: Key.normalizePreampDb)))
        scrobbleProvider = d.string(forKey: Key.scrobbleProvider) ?? "none"
        scrobbleEnabled = d.bool(forKey: Key.scrobbleEnabled)
        listenBrainzToken = Self.loadSecret(Key.listenBrainzToken, defaults: d)
        lastfmSessionKey = Self.loadSecret(Key.lastfmSessionKey, defaults: d)
        lastfmUsername = d.string(forKey: Key.lastfmUsername) ?? ""
        lastfmApiKey = d.string(forKey: Key.lastfmApiKey) ?? ""
        lastfmApiSecret = Self.loadSecret(Key.lastfmApiSecret, defaults: d)
        jellyfinServerUrl = d.string(forKey: Key.jellyfinServerUrl) ?? ""
        jellyfinUser = d.string(forKey: Key.jellyfinUser) ?? ""
        jellyfinToken = d.string(forKey: Key.jellyfinToken) ?? ""
        jellyfinUserId = d.string(forKey: Key.jellyfinUserId) ?? ""
        if let did = d.string(forKey: Key.jellyfinDeviceId), !did.isEmpty {
            jellyfinDeviceId = did
        } else {
            let newId = UUID().uuidString
            jellyfinDeviceId = newId
            d.set(newId, forKey: Key.jellyfinDeviceId)
        }
        if let data = d.data(forKey: Key.resumeSnapshot),
           let snap = try? JSONDecoder().decode(ResumeSnapshot.self, from: data) {
            resumeSnapshot = snap
        }
    }

    /// Read a credential from the Keychain, one-time migrating any legacy plaintext
    /// value that was previously stored in UserDefaults.
    private static func loadSecret(_ key: String, defaults d: UserDefaults) -> String {
        if let value = Keychain.get(key) { return value }
        if let legacy = d.string(forKey: key), !legacy.isEmpty {
            Keychain.set(legacy, for: key)
            d.removeObject(forKey: key)
            return legacy
        }
        return ""
    }

    var isJellyfinLoggedIn: Bool {
        !jellyfinToken.isEmpty && !jellyfinUserId.isEmpty && !jellyfinServerUrl.isEmpty
    }

    func saveResume(_ snap: ResumeSnapshot?) {
        resumeSnapshot = snap
        if let snap, let data = try? JSONEncoder().encode(snap) {
            defaults.set(data, forKey: Key.resumeSnapshot)
        } else {
            defaults.removeObject(forKey: Key.resumeSnapshot)
        }
    }

    func setLastLibraryScanMs(_ ms: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        defaults.set(ms, forKey: Key.lastLibraryScanMs)
    }

    func shouldSkipScan(windowMs: Int64 = 5 * 60 * 1000) -> Bool {
        let last = defaults.object(forKey: Key.lastLibraryScanMs) as? Int64 ?? 0
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return last > 0 && (now - last) < windowMs
    }

    func clearJellyfinSession() {
        jellyfinToken = ""
        jellyfinUserId = ""
    }

    // MARK: - Scrobbling

    var scrobbleQueueJSON: String {
        get { defaults.string(forKey: Key.scrobbleQueue) ?? "" }
        set {
            if newValue.isEmpty {
                defaults.removeObject(forKey: Key.scrobbleQueue)
            } else {
                defaults.set(newValue, forKey: Key.scrobbleQueue)
            }
        }
    }

    func clearLastfmSession() {
        lastfmSessionKey = ""
        lastfmUsername = ""
    }
}
