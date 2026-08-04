# Simple MP3

Local music player with playlists, Jellyfin offline sync, and Android Auto / CarPlay.

This repo hosts native apps per platform:

- [`android/`](android/) — Kotlin / Jetpack Compose app (see below)
- [`ios/`](ios/) — Swift / SwiftUI app (in progress, see [`ios/README.md`](ios/README.md))

<p align="center">
  <img src="store-listing/screenshots/01-home.png" alt="Home screen" width="280" />
  &nbsp;
  <img src="store-listing/screenshots/03-nowplaying.png" alt="Now Playing" width="280" />
</p>

## Features

- **Local library** — scan and play audio already on your device (no streaming account)
- **Playlists** — create, reorder, Liked Songs, Recently Played
- **Android Auto** — full media browse tree and car controls via Media3
- **Jellyfin offline sync** — connect to your server, download for offline / Auto
- **YouTube → audio** — download and convert for offline listening
- **Car-night UI** — dark Material 3 theme with teal accents

## Screenshots

| Home | Playlists | Jellyfin |
|:----:|:---------:|:--------:|
| ![Home](store-listing/screenshots/01-home.png) | ![Playlists](store-listing/screenshots/02-playlists.png) | ![Jellyfin](store-listing/screenshots/05-jellyfin.png) |

More assets live under [`store-listing/`](store-listing/).

## Requirements

- Android Studio (Ladybug / recent stable recommended)
- JDK 17+
- Android device or emulator, **minSdk 29** (Android 10)

## Build (Android)

Using the Makefile (recommended, run from repo root):

```bash
make help          # list all targets
make build         # debug APK
make test          # unit tests
make lint          # Android Lint
make check         # lint + unit tests
make format        # format Kotlin with ktlint (auto-downloaded once)
make install-run   # install debug APK and launch on a device
make release       # release APK
make aab           # release App Bundle
```

Or Gradle directly:

```bash
cd android && ./gradlew assembleDebug
```

Install the debug APK from `android/app/build/outputs/apk/debug/`, or open `android/` in Android Studio and run the **app** configuration.

Release builds should be signed with your own keystore (do not commit signing keys or `.aab` / `.apk` files).

## Stack

- Kotlin · Jetpack Compose · Material 3
- Media3 (ExoPlayer + MediaLibraryService)
- Room · Hilt · DataStore · WorkManager
- OkHttp / Retrofit (Jellyfin)
- NewPipeExtractor + FFmpegKit (optional YouTube download)

## License

```
Copyright 2026 KarpiLabs LLC.

Licensed under the Apache License, Version 2.0.
See the LICENSE file for details.
```

Third-party notices for bundled libraries are in [`store-listing/listing/THIRD_PARTY_NOTICES.md`](store-listing/listing/THIRD_PARTY_NOTICES.md).
