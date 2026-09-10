//
//  DuplicatesScreen.swift
//  Simple MP3
//

import SwiftUI

struct DuplicatesScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.appPalette) private var palette
    @State private var groups: [DuplicateDetector.Group] = []
    @State private var loading = true

    var body: some View {
        Group {
            if loading {
                ProgressView("Looking for matching rips…")
                    .foregroundStyle(palette.textSecondary)
            } else if groups.isEmpty {
                Text("No duplicate songs found. Matches use title, artist, and duration — extras can be hidden or merged into playlists. Files are never deleted.")
                    .foregroundStyle(palette.textSecondary)
                    .padding()
            } else {
                List {
                    Text("\(groups.count) group\(groups.count == 1 ? "" : "s") · hide extras or merge playlist membership. Nothing is deleted from disk.")
                        .font(.caption)
                        .foregroundStyle(palette.textSecondary)
                        .listRowBackground(Color.clear)

                    ForEach(groups) { group in
                        Section {
                            ForEach(group.tracks) { track in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(track.title)
                                        .foregroundStyle(palette.textPrimary)
                                    Text(
                                        (track.id == group.keepId ? "Keep · " : "Extra · ")
                                            + (track.folderPath.isEmpty ? "library" : track.folderPath)
                                            + (track.playCount > 0 ? " · \(track.playCount) plays" : "")
                                    )
                                    .font(.caption)
                                    .foregroundStyle(track.id == group.keepId ? palette.accent : palette.textMuted)
                                }
                                .listRowBackground(Color.clear)
                            }
                            HStack {
                                Button("Hide extras", role: .destructive) {
                                    Task {
                                        await app.repository.hideDuplicateExtras(group)
                                        await reload()
                                    }
                                }
                                Spacer()
                                Button("Merge into keep") {
                                    Task {
                                        await app.repository.mergeDuplicateGroup(group)
                                        await reload()
                                    }
                                }
                                .foregroundStyle(palette.accent)
                            }
                            .listRowBackground(Color.clear)
                        } header: {
                            Text(group.keeper.title)
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("Duplicates")
        .task { await reload() }
    }

    private func reload() async {
        loading = true
        groups = await app.repository.duplicateGroups()
        loading = false
    }
}
