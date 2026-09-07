//
//  ScrobbleService.swift
//  Simple MP3
//
//  Sends listens to ListenBrainz or Last.fm (parity with Android's ScrobbleManager).
//

import CryptoKit
import Foundation
import Observation

enum ScrobbleProvider {
    static let none = "none"
    static let listenBrainz = "listenbrainz"
    static let lastfm = "lastfm"
}

struct PendingScrobble: Codable, Sendable {
    var artist: String
    var track: String
    var album: String
    var durationSec: Int
    /// Unix seconds when the track started playing.
    var timestamp: Int64
}

@Observable
@MainActor
final class ScrobbleService {
    private let preferences: AppPreferences

    // Per-track state, reset on each track start.
    private var current: PendingScrobble?
    private var currentTrackId: String?
    private var scrobbledCurrent = false

    init(preferences: AppPreferences) {
        self.preferences = preferences
    }

    private var isReady: Bool {
        guard preferences.scrobbleEnabled else { return false }
        switch preferences.scrobbleProvider {
        case ScrobbleProvider.listenBrainz:
            return !preferences.listenBrainzToken.isEmpty
        case ScrobbleProvider.lastfm:
            return !preferences.lastfmSessionKey.isEmpty
                && !preferences.lastfmApiKey.isEmpty
                && !preferences.lastfmApiSecret.isEmpty
        default:
            return false
        }
    }

    func onTrackStarted(_ track: Track) {
        guard !track.title.isEmpty, !track.artist.isEmpty else {
            current = nil
            currentTrackId = nil
            return
        }
        let pending = PendingScrobble(
            artist: track.artist,
            track: track.title,
            album: track.album,
            durationSec: Int(track.duration / 1000),
            timestamp: Int64(Date().timeIntervalSince1970)
        )
        current = pending
        currentTrackId = track.id
        scrobbledCurrent = false
        guard isReady else { return }
        Task {
            await sendNowPlaying(pending)
            await flushQueue()
        }
    }

    func onProgress(positionMs: Int64) {
        guard let pending = current, !scrobbledCurrent else { return }
        let durationMs = Int64(pending.durationSec) * 1000
        guard durationMs >= 30_000 else { return }
        let threshold = min(durationMs / 2, 240_000)
        guard positionMs >= threshold else { return }
        scrobbledCurrent = true
        guard isReady else { return }
        Task {
            enqueue(pending)
            await flushQueue()
        }
    }

    /// Validate + persist a Last.fm session from username/password.
    func loginLastfm(apiKey: String, apiSecret: String, username: String, password: String) async -> Bool {
        preferences.lastfmApiKey = apiKey
        preferences.lastfmApiSecret = apiSecret
        guard let sk = await LastFmClient.mobileSession(
            apiKey: apiKey,
            apiSecret: apiSecret,
            username: username,
            password: password
        ) else { return false }
        preferences.lastfmUsername = username
        preferences.lastfmSessionKey = sk
        return true
    }

    // MARK: - Submission

    private func sendNowPlaying(_ pending: PendingScrobble) async {
        switch preferences.scrobbleProvider {
        case ScrobbleProvider.listenBrainz:
            _ = await ListenBrainzClient.submit(
                token: preferences.listenBrainzToken,
                scrobbles: [pending],
                listenType: "playing_now"
            )
        case ScrobbleProvider.lastfm:
            _ = await LastFmClient.updateNowPlaying(
                apiKey: preferences.lastfmApiKey,
                apiSecret: preferences.lastfmApiSecret,
                sessionKey: preferences.lastfmSessionKey,
                scrobble: pending
            )
        default:
            break
        }
    }

    private func enqueue(_ pending: PendingScrobble) {
        var queue = decodeQueue()
        queue.append(pending)
        if queue.count > 200 { queue = Array(queue.suffix(200)) }
        encodeQueue(queue)
    }

    private func flushQueue() async {
        let queue = decodeQueue()
        guard !queue.isEmpty else { return }
        let ok: Bool
        switch preferences.scrobbleProvider {
        case ScrobbleProvider.listenBrainz:
            ok = await ListenBrainzClient.submit(
                token: preferences.listenBrainzToken,
                scrobbles: queue,
                listenType: "single"
            )
        case ScrobbleProvider.lastfm:
            ok = await LastFmClient.scrobble(
                apiKey: preferences.lastfmApiKey,
                apiSecret: preferences.lastfmApiSecret,
                sessionKey: preferences.lastfmSessionKey,
                scrobbles: queue
            )
        default:
            ok = false
        }
        if ok { preferences.scrobbleQueueJSON = "" }
    }

    private func decodeQueue() -> [PendingScrobble] {
        let json = preferences.scrobbleQueueJSON
        guard !json.isEmpty, let data = json.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([PendingScrobble].self, from: data)) ?? []
    }

    private func encodeQueue(_ queue: [PendingScrobble]) {
        if let data = try? JSONEncoder().encode(queue), let s = String(data: data, encoding: .utf8) {
            preferences.scrobbleQueueJSON = s
        }
    }
}

// MARK: - ListenBrainz

enum ListenBrainzClient {
    private static let endpoint = URL(string: "https://api.listenbrainz.org/1/submit-listens")!

    static func submit(token: String, scrobbles: [PendingScrobble], listenType: String) async -> Bool {
        guard !scrobbles.isEmpty, !token.isEmpty else { return true }
        let payload: [[String: Any]] = scrobbles.map { s in
            var meta: [String: Any] = [
                "artist_name": s.artist,
                "track_name": s.track,
                "additional_info": [
                    "duration_ms": s.durationSec * 1000,
                    "submission_client": "Simple MP3",
                ],
            ]
            if !s.album.isEmpty { meta["release_name"] = s.album }
            var listen: [String: Any] = ["track_metadata": meta]
            if listenType != "playing_now" { listen["listened_at"] = s.timestamp }
            return listen
        }
        let body: [String: Any] = ["listen_type": listenType, "payload": payload]
        guard let data = try? JSONSerialization.data(withJSONObject: body) else { return false }

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("Token \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
        } catch {
            return false
        }
    }
}

// MARK: - Last.fm

enum LastFmClient {
    private static let endpoint = URL(string: "https://ws.audioscrobbler.com/2.0/")!

    static func mobileSession(apiKey: String, apiSecret: String, username: String, password: String) async -> String? {
        let params = [
            "method": "auth.getMobileSession",
            "api_key": apiKey,
            "username": username,
            "password": password,
        ]
        guard let data = await post(params, apiSecret: apiSecret) else { return nil }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let session = obj["session"] as? [String: Any],
              let key = session["key"] as? String, !key.isEmpty else { return nil }
        return key
    }

    static func updateNowPlaying(apiKey: String, apiSecret: String, sessionKey: String, scrobble s: PendingScrobble) async -> Bool {
        var params = [
            "method": "track.updateNowPlaying",
            "api_key": apiKey,
            "sk": sessionKey,
            "artist": s.artist,
            "track": s.track,
        ]
        if !s.album.isEmpty { params["album"] = s.album }
        if s.durationSec > 0 { params["duration"] = String(s.durationSec) }
        return await post(params, apiSecret: apiSecret) != nil
    }

    static func scrobble(apiKey: String, apiSecret: String, sessionKey: String, scrobbles: [PendingScrobble]) async -> Bool {
        guard !scrobbles.isEmpty else { return true }
        var params = [
            "method": "track.scrobble",
            "api_key": apiKey,
            "sk": sessionKey,
        ]
        for (i, s) in scrobbles.enumerated() {
            params["artist[\(i)]"] = s.artist
            params["track[\(i)]"] = s.track
            params["timestamp[\(i)]"] = String(s.timestamp)
            if !s.album.isEmpty { params["album[\(i)]"] = s.album }
            if s.durationSec > 0 { params["duration[\(i)]"] = String(s.durationSec) }
        }
        return await post(params, apiSecret: apiSecret) != nil
    }

    /// Signs params (md5 of sorted key+value + secret), posts as form, returns body on 2xx.
    private static func post(_ params: [String: String], apiSecret: String) async -> Data? {
        let cleaned = params.filter { !$0.value.isEmpty }
        let sigBase = cleaned.sorted { $0.key < $1.key }.map { $0.key + $0.value }.joined() + apiSecret
        let sig = md5(sigBase)
        var signed = cleaned
        signed["api_sig"] = sig
        signed["format"] = "json"

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formEncode(signed).data(using: .utf8)
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let ok = (response as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
            return ok ? data : nil
        } catch {
            return nil
        }
    }

    private static func formEncode(_ params: [String: String]) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return params.map { key, value in
            let k = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
            let v = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
            return "\(k)=\(v)"
        }.joined(separator: "&")
    }

    private static func md5(_ input: String) -> String {
        let digest = Insecure.MD5.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
