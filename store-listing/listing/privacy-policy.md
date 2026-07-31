# Privacy Policy for Simple MP3

**Last updated:** 2026-07-30  
**App:** Simple MP3 (`io.karpilabs.simplemp3`)  
**Developer:** Karpi Labs *(replace with your legal name)*

## Overview

Simple MP3 is a local music player. We designed it so your music and playlists stay on your device. This policy explains what the app accesses and why.

## Data we collect

**We do not operate a Simple MP3 account system and we do not sell personal data.**

### Data stored only on your device
- Music library metadata (titles, artists, albums, durations) indexed from files on your phone  
- Playlists, liked songs, recently played, and playback resume position  
- Optional Jellyfin server address, username, and access token (stored securely in app preferences on device)  
- Downloaded offline audio and artwork files (app private storage)

### Data sent over the network
Only when you use **Jellyfin Sync**:
- Your device connects to **the Jellyfin server URL you enter** (your server or LAN)  
- Credentials and API tokens are used solely to authenticate and download media from that server  
- We do not receive your Jellyfin password or media on developer-owned servers  

No advertising SDKs, no analytics SDKs, and no third-party tracking are bundled for ad purposes in the core app.

## Permissions

| Permission | Why |
|------------|-----|
| Music and audio / storage (as applicable) | Scan and play local audio files |
| Notifications | Media playback controls and download progress |
| Internet / network state | Optional Jellyfin sync and LAN discovery |
| Wi‑Fi multicast | Discover Jellyfin servers on your local network |
| Foreground service (media / data sync) | Background playback and offline downloads |

## Android Auto

When connected to Android Auto, media metadata and browse structure are provided to the car interface through the Android media session APIs so you can control playback safely while driving. This data is handled by the Android / vehicle system, not uploaded to Simple MP3 servers.

## Children

The app is suitable for a general audience. We do not knowingly collect personal information from children.

## Data retention & deletion

All app data lives on your device. Uninstalling Simple MP3 removes app-private data (including Jellyfin offline downloads stored in the app sandbox). System media files you own elsewhere on the device are not deleted by uninstall.

## Your choices

- Do not connect Jellyfin if you want zero network use after install (aside from system updates).  
- Use **Wi‑Fi only** downloads to avoid cellular data for sync.  
- Revoke permissions in system Settings at any time.

## Changes

We may update this policy when features change. The “Last updated” date will be revised accordingly.

## Contact

Questions about privacy: **support@example.com** *(replace before publishing)*

---

*This document is a template. Have it reviewed for your jurisdiction before publishing.*
