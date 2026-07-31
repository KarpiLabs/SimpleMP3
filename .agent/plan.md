# Project Plan: SimpleMP3 — AAA local player + Android Auto

## Project Brief

Features:
* Local Audio Library Management: Automatically scans and organizes MP3 files from device storage with support for metadata retrieval and album art display.
* Seamless Android Auto Integration: Full MediaLibraryService browse tree (Playlists first, Albums, Artists, Songs, Recently Played) with steering-wheel / car-display control.
* Spotify-style Playlist Management: Create, rename, reorder, Liked Songs, Recently Played — same playlists surface on Android Auto.
* Modern Playback Engine & Controls: Media3 ExoPlayer + MediaSession, mini player, full Now Playing sheet, shuffle/repeat, lock-screen & notification controls.
* Car-night Premium UI: Deep black + electric teal Material 3 theme tuned for night driving and Android Auto head units.

High-Level Technical Stack:
* Language: Kotlin
* UI Framework: Jetpack Compose with Material Design 3
* Media Core: Jetpack Media3 (ExoPlayer & MediaLibraryService)
* Dependency Injection: Hilt (KSP)
* Local Storage: Room for tracks + playlists
* Image Loading: Coil

## Implementation Steps

### Task_1_Core_Foundation_Media3
- **Status:** COMPLETED

### Task_2_Library_Scanner_Repository
- **Status:** COMPLETED

### Task_3_UI_Android_Auto_Theming
- **Status:** COMPLETED

### Task_4_App_Icon_Final_Run
- **Status:** COMPLETED
- Adaptive icon (night + teal note), debug build successful
