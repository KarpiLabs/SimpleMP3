# Simple MP3 — Store packages

- **Android (Play Store):** assets in this folder (`screenshots/`, `graphics/`, `listing/`, …)
- **iOS (App Store):** see [`ios/`](ios/) — real Simulator captures + marketing frames

## Quick map

```
store-listing/
├── screenshots/          # Android phone (1080×1920) — device mock + marketing text
├── graphics/             # Feature graphic 1024×500
├── icons/                # Hi-res 512×512 icon (+ round)
├── listing/              # Title, descriptions, privacy, contact
├── html/                 # Android source templates (edit & re-render)
├── render.sh             # Rebuild Android PNGs via Chrome headless
├── PLAY_STORE_CHECKLIST.md
└── ios/                  # App Store screenshots (iPhone + iPad) + icon
    ├── README.md
    ├── capture-screenshots.sh
    └── render.sh
```

## Copy-paste listing

| Field | File | Limit |
|-------|------|-------|
| Title | `listing/title.txt` | 30 |
| Short description | `listing/short-description.txt` | 80 |
| Full description | `listing/full-description.txt` | 4000 |
| What’s new | `listing/whats-new.txt` | — |
| Privacy policy | Host `listing/privacy-policy.html` | HTTPS URL required |

## Regenerate images

```bash
cd store-listing
./render.sh
```

Requires Google Chrome at the default macOS path.

## Before publish

1. Replace emails/names in `listing/contact.txt` and privacy policy  
2. Host privacy policy publicly  
3. Upload icons, feature graphic, screenshots  
4. Build a **signed release AAB** in Android Studio  
5. Follow `PLAY_STORE_CHECKLIST.md`  
