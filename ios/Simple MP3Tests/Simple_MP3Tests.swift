//
//  Simple_MP3Tests.swift
//  Simple MP3Tests
//
//  Created by Zach Karpinski on 8/3/26.
//

import Foundation
import Testing
@testable import Simple_MP3

struct Simple_MP3Tests {

    @Test func streamArtworkParsesOgImage() {
        let html = "<html><head><meta property=\"og:image\" content=\"https://station.example/cover.jpg\"></head></html>"
        let url = StreamSaver.imageURLFromHTML(html, base: URL(string: "https://station.example/")!)
        #expect(url?.absoluteString == "https://station.example/cover.jpg")
    }

    @Test func streamArtworkParsesRelativeAppleTouchIcon() {
        let html = "<link rel=\"apple-touch-icon\" href=\"/icon.png\">"
        let url = StreamSaver.imageURLFromHTML(html, base: URL(string: "https://radio.example/listen")!)
        #expect(url?.absoluteString == "https://radio.example/icon.png")
    }

    @Test func streamArtworkParsesHlsTvgLogo() {
        let playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="https://img.example/logo.png",Station
        http://radio.example/stream
        """
        let url = StreamSaver.imageURLFromHLS(playlist, base: URL(string: "https://radio.example/playlist.m3u8")!)
        #expect(url?.absoluteString == "https://img.example/logo.png")
    }

    @Test func streamKeyIsStable() {
        let a = StreamSaver.streamKey(for: "https://radio.example/live.m3u8")
        let b = StreamSaver.streamKey(for: "https://radio.example/live.m3u8")
        #expect(a == b)
        #expect(a.hasPrefix("stream:"))
    }

    @Test func dataRateFormatsKbpsAndMbps() {
        #expect(Formatters.dataRate(128_000) == "128 kbps")
        #expect(Formatters.dataRate(1_500_000) == "1.5 Mbps")
    }

    @Test func liveDataRateLabelPrefersEncodedBitrate() {
        #expect(
            Formatters.dataRateLabel(isLive: true, bitrateBps: 128_000, throughputBps: 400_000)
                == "Live · 128 kbps"
        )
        #expect(Formatters.dataRateLabel(isLive: false, bitrateBps: 128_000, throughputBps: 0) == nil)
    }
  
    @MainActor
    @Test func testShowCarPlayWeatherDefault() async throws {
        let prefs = AppPreferences()
        #expect(prefs.showCarPlayWeather == true)
    }

    @Test func songQueuesExcludeLiveStreams() {
        let song = Track(title: "Highway", uri: "file://song.mp3", source: .local)
        let jellyfin = Track(title: "Offline", uri: "file://jf.mp3", source: .jellyfin)
        let stream = Track(title: "Radio", uri: "https://radio.example/live", source: .stream)
        let result = [song, jellyfin, stream].excludingLiveStreams()
        #expect(result.map(\.id) == [song.id, jellyfin.id])
        #expect(result.allSatisfy { $0.source != .stream })
    }

    @Test func playbackQueuePlaysALiveStreamAlone() {
        let song = Track(title: "Highway", uri: "file://song.mp3", source: .local)
        let stream = Track(title: "Radio", uri: "https://radio.example/live", source: .stream)
        let result = [song, stream].playbackQueue(start: stream)
        #expect(result.tracks.map(\.id) == [stream.id])
        #expect(result.index == 0)
    }

    @Test func playbackQueueDropsStreamsWhenStartingOnASong() {
        let song = Track(title: "Highway", uri: "file://song.mp3", source: .local)
        let stream = Track(title: "Radio", uri: "https://radio.example/live", source: .stream)
        let later = Track(title: "Night", uri: "file://night.mp3", source: .local)
        let result = [song, stream, later].playbackQueue(start: later)
        #expect(result.tracks.map(\.id) == [song.id, later.id])
        #expect(result.index == 1)
    }

    @Test func jellyfinErrorFormattingDoesNotLeakDetails() {
        let loginError = JellyfinError.loginFailed("Login failed (401)")
        #expect(loginError.errorDescription == "Login failed (401)")

        let httpError = JellyfinError.http(500, "")
        #expect(httpError.errorDescription == "HTTP 500")
    }

    @Test func persistArtworkPreventsPathTraversalOutsideSandbox() throws {
        let testData = Data("test-image".utf8)
        let homeDir = URL(fileURLWithPath: NSHomeDirectory())
        let validDestination = homeDir.appendingPathComponent("tmp_artwork.img")

        // Valid destination inside NSHomeDirectory should succeed
        let savedURL = try StreamSaver.persistArtwork(testData, to: validDestination)
        #expect(savedURL == validDestination)
        #expect(FileManager.default.fileExists(atPath: validDestination.path))
        try? FileManager.default.removeItem(at: validDestination)

        // Invalid destination outside NSHomeDirectory should throw StreamSaveError.invalidDestinationPath
        let invalidDestination = URL(fileURLWithPath: "/tmp/malicious_artwork.img")
        #expect(throws: StreamSaveError.self) {
            try StreamSaver.persistArtwork(testData, to: invalidDestination)
        }
    }
}
