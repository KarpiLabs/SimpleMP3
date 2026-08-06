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
- Apple Developer team with **CarPlay Audio** capability (for device / App Store CarPlay)
- Physical device recommended for Media Library + CarPlay Simulator

## Open & run

```bash
open "Simple MP3.xcodeproj"
```

1. Select the **Simple MP3** scheme and your team under Signing.
2. Enable the **CarPlay** capability in the developer portal for the App ID (`io.karpilabs.Simple-MP3`) if you need CarPlay on a real head unit. The project already includes `com.apple.developer.carplay-audio`.
3. Run on a device or simulator. Grant **Media Library** access when prompted.
4. For CarPlay: **I/O → External Displays → CarPlay** in the Simulator, or connect a CarPlay-capable system.

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

- **CarPlay entitlement** requires Apple approval for distribution; development builds work with a properly configured App ID.
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
