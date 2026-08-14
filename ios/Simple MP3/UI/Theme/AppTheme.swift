//
//  AppTheme.swift
//  Simple MP3
//
//  Car-night premium palette — deep violet base, electric teal accent
//  (ported from Android Color.kt / Theme.kt).
//

import SwiftUI

enum AppColors {
    // Night
    static let nightBlack = Color(hex: 0x0C0A12)
    static let nightSurface = Color(hex: 0x14121C)
    static let nightCard = Color(hex: 0x1C1828)
    static let nightElevated = Color(hex: 0x2A2438)
    static let deepViolet = Color(hex: 0x2A1848)

    static let accentTeal = Color(hex: 0x00E5C0)
    static let accentTealDim = Color(hex: 0x00B89A)
    /// Darker teal for text/tints on light surfaces (electric teal fails contrast on white).
    static let dayAccent = Color(hex: 0x007A68)
    static let accentCoral = Color(hex: 0xFF6B6B)
    static let accentGold = Color(hex: 0xFFD166)
    static let accentViolet = Color(hex: 0xA78BFA)

    static let textPrimary = Color(hex: 0xF5F5F7)
    static let textSecondary = Color(hex: 0xB0B0BC)
    static let textMuted = Color(hex: 0x6E6E7A)

    static let gradientStart = Color(hex: 0x0D1B1A)
    static let gradientMid = Color(hex: 0x0C0A12)
    static let gradientEnd = deepViolet

    // Day
    static let dayBackground = Color(hex: 0xFAFAFC)
    static let daySurface = Color(hex: 0xFFFFFF)
    static let dayCard = Color(hex: 0xF0EEF6)
    static let dayElevated = Color(hex: 0xE4E0EF)
    static let dayTextPrimary = Color(hex: 0x1A1723)
    static let dayTextSecondary = Color(hex: 0x4E4B5C)
    static let dayTextMuted = Color(hex: 0x8B8894)
    static let dayGradientStart = Color(hex: 0xEAFBF7)
    static let dayGradientMid = Color(hex: 0xFAFAFC)
    static let dayGradientEnd = Color(hex: 0xF1E9FB)
}

struct AppPalette {
    let background: Color
    let surface: Color
    let card: Color
    let elevated: Color
    let textPrimary: Color
    let textSecondary: Color
    let textMuted: Color
    let accent: Color
    let isDark: Bool

    static let night = AppPalette(
        background: AppColors.nightBlack,
        surface: AppColors.nightSurface,
        card: AppColors.nightCard,
        elevated: AppColors.nightElevated,
        textPrimary: AppColors.textPrimary,
        textSecondary: AppColors.textSecondary,
        textMuted: AppColors.textMuted,
        accent: AppColors.accentTeal,
        isDark: true
    )

    static let day = AppPalette(
        background: AppColors.dayBackground,
        surface: AppColors.daySurface,
        card: AppColors.dayCard,
        elevated: AppColors.dayElevated,
        textPrimary: AppColors.dayTextPrimary,
        textSecondary: AppColors.dayTextSecondary,
        textMuted: AppColors.dayTextMuted,
        accent: AppColors.dayAccent,
        isDark: false
    )
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = AppPalette.night
}

extension EnvironmentValues {
    var appPalette: AppPalette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: opacity)
    }
}

struct AppBackground: View {
    @Environment(\.appPalette) private var palette

    var body: some View {
        LinearGradient(
            colors: palette.isDark
                ? [AppColors.gradientStart, AppColors.gradientMid, AppColors.gradientEnd]
                : [AppColors.dayGradientStart, AppColors.dayGradientMid, AppColors.dayGradientEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}

/// Dark cinematic wash — splash, CarPlay mock, and other always-night surfaces.
struct NightBackground: View {
    var body: some View {
        LinearGradient(
            colors: [
                AppColors.gradientStart,
                AppColors.gradientMid,
                AppColors.gradientEnd
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}

enum Formatters {
    static func duration(_ ms: Int64) -> String {
        let total = max(0, Int(ms / 1000))
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
    }

    static func trackCount(_ n: Int) -> String {
        n == 1 ? "1 song" : "\(n) songs"
    }

    static func greeting(date: Date = Date()) -> String {
        let hour = Calendar.current.component(.hour, from: date)
        switch hour {
        case 5..<12: return "Good morning"
        case 12..<17: return "Good afternoon"
        case 17..<22: return "Good evening"
        default: return "Good night"
        }
    }
}
