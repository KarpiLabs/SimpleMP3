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
}
