# Simple MP3 (iOS)

Local music player for **iPhone** with playlists, Jellyfin offline sync, Quick Connect LAN import, and **Apple CarPlay** — the iOS counterpart to [Simple MP3 for Android](https://github.com/).

## Features

| Feature | iOS implementation |
|--------|---------------------|
| **Local library** | Apple Music / Media Library + Files under `Documents/Media` |
| **Playlists** | Liked Songs, Recently Played, custom lists, system lists for Jellyfin / YouTube / LAN |
| **Apple CarPlay** | Audio app browse tree (Continue, Liked, Playlists, Offline, Albums, Artists, Songs, Recent) |
| **Jellyfin offline** | Sign in, browse server, stream or download for offline / CarPlay |
| **YouTube → audio** | Metadata lookup + import audio files into YouTube Downloads (App Store–safe) |
| **Quick Connect** | LAN HTTP portal to upload audio from another device |
| **Drive Mode** | Large car-friendly controls; auto on CarPlay connect |
| **Car-night UI** | Deep violet base, electric teal accent (same brand as Android) |

## Requirements

- Xcode 16+ (project targets current iOS SDK)
- Apple Developer team with **CarPlay Audio App** entitlement assigned to the App ID (`io.karpilabs.Simple-MP3`)
- Physical device recommended for Media Library + real head-unit CarPlay (Simulator supports CarPlay window)

## CarPlay (enabled)

| Piece | Location |
|--------|----------|
| Entitlement | `Simple MP3/Simple_MP3.entitlements` → `com.apple.developer.carplay-audio` |
| Scene config | `Info.plist` → `CPTemplateApplicationSceneSessionRoleApplication` |
| Delegate | `CarPlay/CarPlaySceneDelegate.swift` |
| Playback | `PlaybackManager` remote commands + Now Playing info |
| Drive Mode | Auto-enables on CarPlay connect (Settings toggle) |

## Open & run

```bash
open "Simple MP3.xcodeproj"
```

1. Select the **Simple MP3** scheme and your team under **Signing & Capabilities**.
2. Confirm **CarPlay** appears under capabilities and the App ID provisioning profile includes **CarPlay Audio** (assigned in the Developer portal).
3. Run on a device or simulator. Grant **Media Library** access when prompted.
4. **Simulator CarPlay:** I/O → External Displays → CarPlay.  
   **Device:** plug into a CarPlay head unit / wireless CarPlay.

## Architecture

```
Simple MP3/
  Models/          Track, Playlist, preferences types
  Data/            LibraryStore, MusicRepository, MediaLibraryScanner, AppModel
  Player/          AVPlayer + Now Playing / remote commands
  CarPlay/         CPTemplateApplicationSceneDelegate browse UI
  Jellyfin/        REST client + offline download
  YouTube/         Import + oEmbed title lookup
  QuickConnect/    NWListener LAN upload portal
  UI/              SwiftUI screens (Home, Library, Playlists, Tools, Settings)
```

Playback is shared between the phone UI and CarPlay via `AppModel.shared` / `PlaybackManager`.

## Notes

- **CarPlay Audio** entitlement is in the project; the App ID must keep the capability enabled in the Apple Developer portal and provisioning profiles must be regenerated if signing fails.
- **YouTube**: in-process stream extraction is not used (App Store policy). Import downloaded audio via Files / Share into Tools → YouTube.

## App Store screenshots

Marketing + clean device captures live under [`../store-listing/ios/`](../store-listing/ios/).

```bash
cd ../store-listing/ios
./capture-screenshots.sh   # Simulator → raw PNGs (iPhone + iPad)
./render.sh                # Optional marketing frames
```

Launch with `-ScreenshotDemo -ScreenshotScene home` to seed demo data for captures.
- **Cleartext Jellyfin** (`http://` on LAN) is allowed via ATS local-networking exceptions in `Info.plist`.

## License

Copyright 2026 KarpiLabs LLC.

Licensed under the Apache License, Version 2.0.
