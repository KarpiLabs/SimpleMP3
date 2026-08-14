//
//  MediaLibraryScanner.swift
//  Simple MP3
//
//  Scans the device music library (MediaPlayer) + app Documents/Media.
//

import AVFoundation
import Foundation
import MediaPlayer
import UIKit

enum MediaLibraryScanner {
    static let audioExtensions: Set<String> = [
        "mp3", "m4a", "aac", "wav", "aiff", "aif", "flac", "caf", "ogg", "opus"
    ]

    /// Request Apple Music / media library authorization.
    ///
    /// Bounded with a timeout: if the system permission alert never resolves (e.g. no
    /// UI automation to dismiss it in a CI/test environment), bootstrap must still
    /// proceed rather than hang forever.
    static func requestAuthorization() async -> MPMediaLibraryAuthorizationStatus {
        await withTaskGroup(of: MPMediaLibraryAuthorizationStatus.self) { group in
            group.addTask {
                await withCheckedContinuation { cont in
                    MPMediaLibrary.requestAuthorization { status in
                        cont.resume(returning: status)
                    }
                }
            }
            group.addTask {
                try? await Task.sleep(for: .seconds(10))
                return MPMediaLibrary.authorizationStatus()
            }
            let result = await group.next() ?? .notDetermined
            group.cancelAll()
            return result
        }
    }

    static func scan(forceDocuments: Bool = true) async -> [Track] {
        var result: [Track] = []

        let status = await requestAuthorization()
        if status == .authorized {
            result.append(contentsOf: scanMediaPlayerLibrary())
        }

        if forceDocuments {
            result.append(contentsOf: await scanDocumentsMedia())
        }

        // Dedupe by id (MediaPlayer persistentID wins over same file).
        var seen = Set<String>()
        var unique: [Track] = []
        for t in result {
            if seen.insert(t.id).inserted {
                unique.append(t)
            }
        }
        return unique
    }

    private static func scanMediaPlayerLibrary() -> [Track] {
        let query = MPMediaQuery.songs()
        guard let items = query.items else { return [] }
        return items.compactMap { item -> Track? in
            guard let url = item.assetURL else { return nil }
            let pid = item.persistentID
            let id = "mp-\(pid)"
            let title = item.title?.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let title, !title.isEmpty else { return nil }
            let artist = item.artist ?? item.albumArtist ?? "Unknown Artist"
            let album = item.albumTitle ?? "Unknown Album"
            let durationMs = Int64((item.playbackDuration) * 1000)
            let artworkUri: String? = "mpmedia://\(pid)"
            let dateAdded = Int64(item.dateAdded.timeIntervalSince1970 * 1000)
            let folder = item.albumTitle.map { "Music/\($0)" } ?? "Music"
            return Track(
                id: id,
                title: title,
                artist: artist,
                album: album,
                albumId: "\(item.albumPersistentID)",
                artistId: "\(item.artistPersistentID)",
                uri: url.absoluteString,
                duration: durationMs,
                artworkUri: artworkUri,
                dateAdded: dateAdded,
                year: item.releaseDate.flatMap { Calendar.current.component(.year, from: $0) } ?? 0,
                trackNumber: item.albumTrackNumber,
                genre: item.genre,
                folderPath: folder,
                size: 0,
                source: .local
            )
        }
    }

    private static func collectAudioFiles(in media: URL) -> [URL] {
        let fm = FileManager.default
        var files: [URL] = []
        if let enumerator = fm.enumerator(
            at: media,
            includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) {
            for case let url as URL in enumerator {
                if audioExtensions.contains(url.pathExtension.lowercased()) {
                    files.append(url)
                }
            }
        }
        return files
    }

    private static func scanDocumentsMedia() async -> [Track] {
        let fm = FileManager.default
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
        let media = docs.appendingPathComponent("Media", isDirectory: true)
        try? fm.createDirectory(at: media, withIntermediateDirectories: true)

        let files = collectAudioFiles(in: media)

        var tracks: [Track] = []
        for url in files {
            if let track = await metadataTrack(from: url) {
                tracks.append(track)
            }
        }
        return tracks
    }

    static func metadataTrack(from url: URL, source: TrackSource = .local, externalId: String? = nil) async -> Track? {
        let asset = AVURLAsset(url: url)
        let title: String
        let artist: String
        let album: String
        let durationMs: Int64
        var artworkUri: String?
        var trackNumber = 0
        let year = 0
        var genre: String?

        do {
            let duration = try await asset.load(.duration)
            durationMs = Int64(CMTimeGetSeconds(duration) * 1000)
        } catch {
            durationMs = 0
        }

        do {
            let meta = try await asset.load(.commonMetadata)
            func value(for key: AVMetadataKey) async -> String? {
                guard let item = meta.first(where: { $0.commonKey == key }) else { return nil }
                return try? await item.load(.stringValue)
            }
            title = await value(for: .commonKeyTitle)
                ?? url.deletingPathExtension().lastPathComponent
            artist = await value(for: .commonKeyArtist) ?? "Unknown Artist"
            album = await value(for: .commonKeyAlbumName) ?? "Unknown Album"
            if let numItem = meta.first(where: { $0.commonKey == .commonKeyType }),
               let num = try? await numItem.load(.numberValue) {
                trackNumber = num.intValue
            }
            genre = await value(for: .commonKeyType)

            if let artItem = meta.first(where: { $0.commonKey == .commonKeyArtwork }),
               let artData = try? await artItem.load(.dataValue) {
                let artDir = url.deletingLastPathComponent().appendingPathComponent(".art", isDirectory: true)
                try? FileManager.default.createDirectory(at: artDir, withIntermediateDirectories: true)
                let artURL = artDir.appendingPathComponent(url.deletingPathExtension().lastPathComponent + ".jpg")
                try? artData.write(to: artURL)
                artworkUri = artURL.absoluteString
            }
        } catch {
            title = url.deletingPathExtension().lastPathComponent
            artist = "Unknown Artist"
            album = "Unknown Album"
        }

        let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
        let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
        let modDate = (attrs?[.modificationDate] as? Date) ?? Date()
        let relative = relativeFolder(for: url)
        let id = stableFileId(url: url, externalId: externalId, source: source)

        return Track(
            id: id,
            title: title,
            artist: artist,
            album: album,
            uri: url.absoluteString,
            duration: max(0, durationMs),
            artworkUri: artworkUri,
            dateAdded: Int64(modDate.timeIntervalSince1970 * 1000),
            year: year,
            trackNumber: trackNumber,
            genre: genre,
            folderPath: relative,
            size: size,
            source: source,
            externalId: externalId,
            isOffline: true
        )
    }

    private static func relativeFolder(for url: URL) -> String {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let media = docs.appendingPathComponent("Media")
        let path = url.deletingLastPathComponent().path
        if path.hasPrefix(media.path) {
            let rel = String(path.dropFirst(media.path.count)).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            return rel.isEmpty ? "Media" : rel
        }
        return "Media"
    }

    private static func stableFileId(url: URL, externalId: String?, source: TrackSource) -> String {
        if let externalId, !externalId.isEmpty {
            return "\(source.rawValue)-\(externalId)"
        }
        return "file-\(url.path.hashValue)"
    }
}

enum MediaArtwork {
    private static let cache = NSCache<NSString, UIImage>()

    static func image(artworkUri: String?, trackId: String? = nil, side: CGFloat) -> UIImage? {
        let size = max(side, 64)
        if let uri = artworkUri, !uri.isEmpty {
            if uri.hasPrefix("mpmedia://") {
                let pid = String(uri.dropFirst("mpmedia://".count))
                if let img = mediaItemImage(persistentID: pid, side: size) { return img }
            } else if let url = URL(string: uri), url.isFileURL,
                      let img = UIImage(contentsOfFile: url.path) {
                return img
            }
        }
        if let trackId, trackId.hasPrefix("mp-") {
            return mediaItemImage(persistentID: String(trackId.dropFirst(3)), side: size)
        }
        return nil
    }

    private static func mediaItemImage(persistentID: String, side: CGFloat) -> UIImage? {
        let key = "\(persistentID)-\(Int(side))" as NSString
        if let cached = cache.object(forKey: key) { return cached }
        guard let pid = UInt64(persistentID) else { return nil }
        let query = MPMediaQuery.songs()
        query.addFilterPredicate(
            MPMediaPropertyPredicate(
                value: NSNumber(value: pid),
                forProperty: MPMediaItemPropertyPersistentID
            )
        )
        guard let item = query.items?.first,
              let artwork = item.artwork,
              let img = artwork.image(at: CGSize(width: side * 2, height: side * 2))
        else { return nil }
        cache.setObject(img, forKey: key)
        return img
    }
}
