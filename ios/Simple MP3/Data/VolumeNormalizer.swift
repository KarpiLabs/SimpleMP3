//
//  VolumeNormalizer.swift
//  Simple MP3
//
//  ReplayGain-based loudness normalization (parity with Android's VolumeNormalizer).
//

import AVFoundation
import Foundation

/// ReplayGain stores a per-track gain (dB) in file tags so players can play every
/// track at a consistent perceived loudness. AVPlayer volume maxes at 1.0, so we only
/// ever attenuate — the usual case since RG track gains are almost always negative.
enum VolumeNormalizer {
    static let trackGainKey = "replaygain_track_gain"

    /// Parse a ReplayGain value like "-6.48 dB" or "3.21" into a Double, or nil.
    static func parseGainDb(_ raw: String?) -> Double? {
        guard let raw, !raw.isEmpty else { return nil }
        var cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.lowercased().hasSuffix("db") {
            cleaned = String(cleaned.dropLast(2)).trimmingCharacters(in: .whitespaces)
        }
        return Double(cleaned)
    }

    /// Linear AVPlayer volume for a track. Returns 1.0 when normalization is off or the
    /// track has no known gain, so un-tagged tracks play at the user's own level.
    static func linearVolume(gainDb: Double?, preampDb: Int, enabled: Bool) -> Float {
        guard enabled, let gainDb else { return 1.0 }
        let totalDb = gainDb + Double(preampDb)
        let linear = pow(10.0, totalDb / 20.0)
        return Float(min(1.0, max(0.0, linear)))
    }

    /// Best-effort ReplayGain track-gain read from an asset's metadata. Handles ID3 TXXX
    /// (`extraAttributes[.info]` == REPLAYGAIN_TRACK_GAIN) and freeform/iTunes atoms.
    static func trackGain(from asset: AVAsset) async -> Double? {
        guard let items = try? await asset.load(.metadata) else { return nil }
        for item in items {
            if let gain = await replayGainValue(from: item) { return gain }
        }
        return nil
    }

    private static func replayGainValue(from item: AVMetadataItem) async -> Double? {
        var matched = false
        if let key = item.key as? String, key.lowercased().contains(trackGainKey) {
            matched = true
        }
        if !matched, let id = item.identifier?.rawValue.lowercased(), id.contains(trackGainKey) {
            matched = true
        }
        if !matched,
           let extra = try? await item.load(.extraAttributes),
           let info = extra[.info] as? String,
           info.lowercased().contains(trackGainKey) {
            matched = true
        }
        guard matched else { return nil }
        let value = try? await item.load(.stringValue)
        return parseGainDb(value)
    }
}
