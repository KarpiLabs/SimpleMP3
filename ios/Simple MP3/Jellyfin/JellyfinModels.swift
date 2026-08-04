//
//  JellyfinModels.swift
//  Simple MP3
//

import Foundation

struct AuthenticateByNameRequest: Encodable {
    let Username: String
    let Pw: String
}

struct AuthenticationResult: Decodable {
    let AccessToken: String?
    let User: JellyfinUser?
    let ServerId: String?
}

struct JellyfinUser: Decodable {
    let Id: String
    let Name: String?
}

struct QueryResult: Decodable {
    let Items: [JellyfinItem]?
    let TotalRecordCount: Int?
    let StartIndex: Int?
}

struct JellyfinItem: Decodable, Identifiable, Hashable {
    let Id: String
    let Name: String?
    let itemType: String?
    let Album: String?
    let AlbumId: String?
    let AlbumArtist: String?
    let Artists: [String]?
    let RunTimeTicks: Int64?
    let IndexNumber: Int?
    let ProductionYear: Int?
    let ImageTags: [String: String]?
    let AlbumPrimaryImageTag: String?
    let ParentId: String?
    let ChildCount: Int?
    let Size: Int64?
    let Container: String?

    enum CodingKeys: String, CodingKey {
        case Id, Name, Album, AlbumId, AlbumArtist, Artists
        case RunTimeTicks, IndexNumber, ProductionYear, ImageTags
        case AlbumPrimaryImageTag, ParentId, ChildCount, Size, Container
        case itemType = "Type"
    }

    var id: String { Id }

    var durationMs: Int64 { (RunTimeTicks ?? 0) / 10_000 }

    var artistName: String {
        if let a = Artists?.first, !a.isEmpty { return a }
        if let a = AlbumArtist, !a.isEmpty { return a }
        return "Unknown Artist"
    }

    var albumName: String {
        if let a = Album, !a.isEmpty { return a }
        return "Unknown Album"
    }

    var title: String {
        if let n = Name, !n.isEmpty { return n }
        return "Unknown Title"
    }

    var hasPrimaryImage: Bool {
        ImageTags?["Primary"] != nil || !(AlbumPrimaryImageTag ?? "").isEmpty
    }
}

struct JellyfinSession: Equatable, Sendable {
    var serverUrl: String
    var accessToken: String
    var userId: String
    var userName: String
    var serverId: String?
    var deviceId: String
}

struct SyncProgress: Equatable, Sendable {
    var phase: String = "Idle"
    var current: Int = 0
    var total: Int = 0
    var currentTitle: String = ""
    var isActive: Bool = false
    var error: String?
    var lastResult: String?

    var fraction: Double {
        guard total > 0 else { return 0 }
        return min(1, Double(current) / Double(total))
    }
}
