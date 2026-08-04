//
//  JellyfinClient.swift
//  Simple MP3
//

import Foundation

enum JellyfinError: LocalizedError {
    case invalidURL
    case loginFailed(String)
    case notAuthenticated
    case http(Int, String)
    case decode
    case downloadFailed

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid server URL"
        case .loginFailed(let m): return m
        case .notAuthenticated: return "Not signed in to Jellyfin"
        case .http(let c, let m): return "HTTP \(c): \(m)"
        case .decode: return "Could not parse server response"
        case .downloadFailed: return "Download failed"
        }
    }
}

actor JellyfinClient {
    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 600
        session = URLSession(configuration: config)
    }

    func normalizeServerUrl(_ raw: String) -> String {
        var url = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while url.hasSuffix("/") { url.removeLast() }
        if !url.lowercased().hasPrefix("http://") && !url.lowercased().hasPrefix("https://") {
            url = "http://\(url)"
        }
        return url
    }

    private func authHeader(deviceId: String, token: String? = nil) -> String {
        var s = #"MediaBrowser Client="SimpleMP3", Device="iOS", DeviceId="\#(deviceId)", Version="1.0.0""#
        if let token, !token.isEmpty {
            s += #", Token="\#(token)""#
        }
        return s
    }

    func authenticate(
        serverUrl: String,
        username: String,
        password: String,
        deviceId: String
    ) async throws -> JellyfinSession {
        let base = normalizeServerUrl(serverUrl)
        guard let url = URL(string: "\(base)/Users/AuthenticateByName") else {
            throw JellyfinError.invalidURL
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(authHeader(deviceId: deviceId), forHTTPHeaderField: "X-Emby-Authorization")
        let body = AuthenticateByNameRequest(Username: username.trimmingCharacters(in: .whitespaces), Pw: password)
        req.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw JellyfinError.loginFailed("No response") }
        if !(200..<300).contains(http.statusCode) {
            let msg = String(data: data, encoding: .utf8)?.prefix(200) ?? ""
            throw JellyfinError.loginFailed("Login failed (\(http.statusCode)): \(msg)")
        }
        let parsed = try JSONDecoder().decode(AuthenticationResult.self, from: data)
        guard let token = parsed.AccessToken, let user = parsed.User else {
            throw JellyfinError.loginFailed("Invalid login response")
        }
        return JellyfinSession(
            serverUrl: base,
            accessToken: token,
            userId: user.Id,
            userName: user.Name?.isEmpty == false ? user.Name! : username,
            serverId: parsed.ServerId,
            deviceId: deviceId
        )
    }

    func getAudioItems(
        session jf: JellyfinSession,
        startIndex: Int = 0,
        limit: Int = 200,
        parentId: String? = nil,
        searchTerm: String? = nil
    ) async throws -> QueryResult {
        var params = [
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "Fields=BasicSyncInfo,PrimaryImageAspectRatio,Path,MediaSources",
            "SortBy=Album,IndexNumber,SortName",
            "SortOrder=Ascending",
            "StartIndex=\(startIndex)",
            "Limit=\(limit)",
            "EnableImageTypes=Primary"
        ]
        if let parentId, !parentId.isEmpty {
            params.append("ParentId=\(parentId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? parentId)")
        }
        if let searchTerm, !searchTerm.isEmpty {
            params.append("SearchTerm=\(searchTerm.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? searchTerm)")
        }
        let urlStr = "\(jf.serverUrl)/Users/\(jf.userId)/Items?\(params.joined(separator: "&"))"
        return try await getJSON(session: jf, urlString: urlStr)
    }

    func getAlbums(session jf: JellyfinSession, startIndex: Int = 0, limit: Int = 100) async throws -> QueryResult {
        let urlStr = """
        \(jf.serverUrl)/Users/\(jf.userId)/Items?\
        IncludeItemTypes=MusicAlbum&Recursive=true\
        &SortBy=SortName&SortOrder=Ascending\
        &StartIndex=\(startIndex)&Limit=\(limit)\
        &EnableImageTypes=Primary\
        &Fields=ChildCount,PrimaryImageAspectRatio
        """
        return try await getJSON(session: jf, urlString: urlStr)
    }

    func getAlbumTracks(session jf: JellyfinSession, albumId: String) async throws -> [JellyfinItem] {
        let enc = albumId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? albumId
        let urlStr = """
        \(jf.serverUrl)/Users/\(jf.userId)/Items?\
        ParentId=\(enc)&IncludeItemTypes=Audio&Recursive=true\
        &SortBy=IndexNumber,SortName&SortOrder=Ascending\
        &Fields=BasicSyncInfo,PrimaryImageAspectRatio&Limit=500
        """
        let result: QueryResult = try await getJSON(session: jf, urlString: urlStr)
        return result.Items ?? []
    }

    func getPlaylists(session jf: JellyfinSession, startIndex: Int = 0, limit: Int = 100) async throws -> QueryResult {
        let urlStr = """
        \(jf.serverUrl)/Users/\(jf.userId)/Items?\
        IncludeItemTypes=Playlist&Recursive=true\
        &SortBy=SortName&SortOrder=Ascending\
        &StartIndex=\(startIndex)&Limit=\(limit)\
        &EnableImageTypes=Primary&Fields=ChildCount,PrimaryImageAspectRatio
        """
        return try await getJSON(session: jf, urlString: urlStr)
    }

    func streamURL(session jf: JellyfinSession, itemId: String) -> URL? {
        let enc = itemId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? itemId
        return URL(string: "\(jf.serverUrl)/Audio/\(enc)/stream?static=true&api_key=\(jf.accessToken)")
    }

    func downloadURL(session jf: JellyfinSession, itemId: String) -> URL? {
        streamURL(session: jf, itemId: itemId)
    }

    func imageURL(session jf: JellyfinSession, itemId: String, tag: String?, maxWidth: Int = 300) -> URL? {
        guard let tag, !tag.isEmpty else { return nil }
        let enc = itemId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? itemId
        return URL(string: "\(jf.serverUrl)/Items/\(enc)/Images/Primary?tag=\(tag)&maxWidth=\(maxWidth)&api_key=\(jf.accessToken)")
    }

    func download(
        session jf: JellyfinSession,
        item: JellyfinItem,
        to directory: URL
    ) async throws -> URL {
        guard let remote = downloadURL(session: jf, itemId: item.Id) else {
            throw JellyfinError.invalidURL
        }
        var req = URLRequest(url: remote)
        req.setValue(authHeader(deviceId: jf.deviceId, token: jf.accessToken), forHTTPHeaderField: "X-Emby-Authorization")
        let (temp, response) = try await session.download(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw JellyfinError.downloadFailed
        }
        let ext = item.Container?.lowercased() ?? "mp3"
        let safe = item.title
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
        let dest = directory.appendingPathComponent("\(item.Id)_\(safe).\(ext)")
        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }
        try FileManager.default.moveItem(at: temp, to: dest)
        return dest
    }

    private func getJSON<T: Decodable>(session jf: JellyfinSession, urlString: String) async throws -> T {
        guard let url = URL(string: urlString) else { throw JellyfinError.invalidURL }
        var req = URLRequest(url: url)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(authHeader(deviceId: jf.deviceId, token: jf.accessToken), forHTTPHeaderField: "X-Emby-Authorization")
        let (data, response) = try await session.data(for: req)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            let msg = String(data: data, encoding: .utf8)?.prefix(120) ?? ""
            throw JellyfinError.http(http.statusCode, String(msg))
        }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw JellyfinError.decode
        }
    }
}
