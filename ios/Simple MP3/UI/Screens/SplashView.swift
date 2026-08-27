//
//  SplashView.swift
//  Simple MP3
//
//  AAA launch experience — seamless handoff from LaunchScreen.storyboard,
//  animated brand lockup, status line, and soft exit into the main shell.
//

import SwiftUI

struct SplashView: View {
    let status: String
    let progress: Double
    var isReady: Bool = false

    @State private var appear = false
    @State private var pulse = false
    @State private var ringSpin = false
    @State private var glow = false
    @State private var notesOffset: [CGFloat] = [-8, 6, -4]
    @State private var exitScale: CGFloat = 1
    @State private var exitOpacity: Double = 1

    var body: some View {
        ZStack {
            background

            // Floating audio-note accents
            floatingNotes
                .opacity(appear ? 0.55 : 0)

            VStack(spacing: 0) {
                Spacer()

                mascotBlock
                    .padding(.bottom, 28)

                titleBlock
                    .padding(.bottom, 36)

                progressBlock
                    .padding(.horizontal, 48)

                Spacer()

                footer
                    .padding(.bottom, 36)
            }
            .scaleEffect(exitScale)
            .opacity(exitOpacity)
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.spring(response: 0.75, dampingFraction: 0.78)) {
                appear = true
            }
            withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) {
                pulse = true
                glow = true
            }
            withAnimation(.linear(duration: 8).repeatForever(autoreverses: false)) {
                ringSpin = true
            }
            withAnimation(.easeInOut(duration: 2.2).repeatForever(autoreverses: true)) {
                notesOffset = [10, -12, 8]
            }
        }
        .onChange(of: isReady) { _, ready in
            guard ready else { return }
            withAnimation(.easeInOut(duration: 0.45)) {
                exitScale = 1.06
                exitOpacity = 0
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Simple MP3, \(status)")
    }

    // MARK: - Layers

    private var background: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(hex: 0x0D1B1A),
                    Color(hex: 0x0C0A12),
                    Color(hex: 0x2A1848)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            // Soft radial glow behind mascot
            RadialGradient(
                colors: [
                    AppColors.accentTeal.opacity(glow ? 0.22 : 0.10),
                    AppColors.deepViolet.opacity(0.35),
                    .clear
                ],
                center: .center,
                startRadius: 20,
                endRadius: 280
            )
            .offset(y: -40)
            .blur(radius: 8)

            // Subtle vignette
            RadialGradient(
                colors: [.clear, Color.black.opacity(0.45)],
                center: .center,
                startRadius: 160,
                endRadius: 520
            )
        }
    }

    private var floatingNotes: some View {
        ZStack {
            Image(systemName: "music.note")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(AppColors.accentTeal.opacity(0.45))
                .offset(x: -120, y: -90 + notesOffset[0])
            Image(systemName: "music.note.list")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(AppColors.accentViolet.opacity(0.4))
                .offset(x: 118, y: -70 + notesOffset[1])
            Image(systemName: "headphones")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(AppColors.accentTeal.opacity(0.35))
                .offset(x: 100, y: 120 + notesOffset[2])
            Image(systemName: "waveform")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(AppColors.accentGold.opacity(0.35))
                .offset(x: -110, y: 130 + notesOffset[0])
        }
    }

    private var mascotBlock: some View {
        ZStack {
            // Orbit ring
            Circle()
                .stroke(
                    AngularGradient(
                        colors: [
                            AppColors.accentTeal.opacity(0.0),
                            AppColors.accentTeal.opacity(0.85),
                            AppColors.accentViolet.opacity(0.6),
                            AppColors.accentTeal.opacity(0.0)
                        ],
                        center: .center
                    ),
                    lineWidth: 2.5
                )
                .frame(width: 196, height: 196)
                .rotationEffect(.degrees(ringSpin ? 360 : 0))

            // Outer soft pulse
            Circle()
                .fill(AppColors.accentTeal.opacity(pulse ? 0.14 : 0.06))
                .frame(width: pulse ? 210 : 188, height: pulse ? 210 : 188)
                .blur(radius: 2)

            // Icon plate
            Image("SplashMascot")
                .resizable()
                .scaledToFill()
                .frame(width: 148, height: 148)
                .clipShape(RoundedRectangle(cornerRadius: 36, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 36, style: .continuous)
                        .stroke(
                            LinearGradient(
                                colors: [
                                    AppColors.accentTeal.opacity(0.85),
                                    AppColors.accentViolet.opacity(0.45),
                                    AppColors.accentTeal.opacity(0.2)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.5
                        )
                )
                .shadow(color: AppColors.accentTeal.opacity(0.35), radius: pulse ? 28 : 14, y: 8)
                .shadow(color: .black.opacity(0.5), radius: 20, y: 12)
                .scaleEffect(appear ? 1 : 0.72)
                .opacity(appear ? 1 : 0)
        }
    }

    private var titleBlock: some View {
        VStack(spacing: 10) {
            Text("Jerry's Simple MP3")
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [AppColors.textPrimary, AppColors.textPrimary.opacity(0.9)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .shadow(color: .black.opacity(0.35), radius: 8, y: 2)

            Text("Local music")
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(AppColors.accentTeal)
                .tracking(0.4)
        }
        .multilineTextAlignment(.center)
        .offset(y: appear ? 0 : 16)
        .opacity(appear ? 1 : 0)
        .animation(.spring(response: 0.8, dampingFraction: 0.82).delay(0.08), value: appear)
    }

    private var progressBlock: some View {
        VStack(spacing: 14) {
            // Custom track
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(AppColors.nightElevated.opacity(0.9))
                        .frame(height: 6)

                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [AppColors.accentTealDim, AppColors.accentTeal, AppColors.accentViolet],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: max(8, geo.size.width * progress.clamped01), height: 6)
                        .shadow(color: AppColors.accentTeal.opacity(0.55), radius: 6, y: 0)
                        .animation(.easeInOut(duration: 0.35), value: progress)
                }
            }
            .frame(height: 6)

            Text(status)
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundStyle(AppColors.textSecondary)
                .contentTransition(.numericText())
                .animation(.easeInOut(duration: 0.25), value: status)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 18)
        }
        .opacity(appear ? 1 : 0)
        .animation(.easeOut(duration: 0.5).delay(0.2), value: appear)
    }

    private var footer: some View {
        HStack(spacing: 8) {
            Image(systemName: "bolt.car.fill")
                .font(.system(size: 12, weight: .semibold))
            Text("KarpiLabs")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .tracking(1.2)
        }
        .foregroundStyle(AppColors.textMuted)
        .opacity(appear ? 0.9 : 0)
        .animation(.easeOut(duration: 0.5).delay(0.3), value: appear)
    }
}

private extension Double {
    var clamped01: Double { min(1, max(0, self)) }
}

#Preview {
    SplashView(status: "Scanning your library…", progress: 0.42)
}
