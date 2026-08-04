//
//  AppPreferences.swift
//  Simple MP3
//

import Foundation
import Observation

@Observable
@MainActor
final class AppPreferences {
    private let defaults = UserDefaults.standard

    private enum Key {
        static let driveMode = "driveMode"
        static let autoDriveModeOnCar = "autoDriveModeOnCar"
        static let autoResumeOnDrive = "autoResumeOnDrive"
        static let pauseOnCarDisconnect = "pauseOnCarDisconnect"
        static let largeFileOptimize = "largeFileOptimize"
        static let largeFileColdPack = "largeFileColdPack"
        static let jellyfinEnabled = "jellyfinEnabled"
        static let wifiOnlyDownloads = "wifiOnlyDownloads"
        static let resumeEnabled = "resumeEnabled"
        static let themeMode = "themeMode"
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
}
