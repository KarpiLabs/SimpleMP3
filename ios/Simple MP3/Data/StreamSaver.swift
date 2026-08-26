//
//  StreamSaver.swift
//  Simple MP3
//
//  Saves a network stream (progressive URL or HLS .m3u8) to a local .m4a using
//  AVAssetExportSession — App Store–safe, no re-encode of an owned file. Mirrors
//  the Android Streams "Save offline" flow.
//

import AVFoundation
import Foundation

enum StreamSaveError: LocalizedError {
    case invalidURL
    case cannotCreateExport
    case exportFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "That doesn't look like a valid stream URL."
        case .cannotCreateExport: return "This stream can't be saved (unsupported format)."
        case .exportFailed(let msg): return "Save failed: \(msg)"
        }
    }
}

enum StreamSaver {
    /// Export [urlString] to an `.m4a` at [destination]. Returns the written file URL.
    static func save(urlString: String, to destination: URL) async throws -> URL {
        guard let url = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme == "http" || url.scheme == "https" else {
            throw StreamSaveError.invalidURL
        }

        let asset = AVURLAsset(url: url)
        guard let export = AVAssetExportSession(
            asset: asset,
            presetName: AVAssetExportPresetAppleM4A
        ) else {
            throw StreamSaveError.cannotCreateExport
        }

        try? FileManager.default.removeItem(at: destination)
        export.outputURL = destination
        export.outputFileType = .m4a

        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            export.exportAsynchronously {
                continuation.resume()
            }
        }

        switch export.status {
        case .completed:
            return destination
        case .cancelled:
            throw StreamSaveError.exportFailed("cancelled")
        default:
            throw StreamSaveError.exportFailed(export.error?.localizedDescription ?? "unknown error")
        }
    }

    /// Duration in milliseconds of a saved local file (0 if unknown).
    static func durationMs(of fileURL: URL) async -> Int64 {
        let asset = AVURLAsset(url: fileURL)
        let seconds = (try? await asset.load(.duration).seconds) ?? 0
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int64(seconds * 1000)
    }
}
