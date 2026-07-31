# Simple MP3 — Play Store package

Everything under `store-listing/` is ready to upload to **Google Play Console**.

## Quick map

```
store-listing/
├── screenshots/          # 5× phone (1080×1920) — device mock + marketing text
├── graphics/             # Feature graphic 1024×500
├── icons/                # Hi-res 512×512 icon (+ round)
├── listing/              # Title, descriptions, privacy, contact
├── html/                 # Source templates (edit & re-render)
├── render.sh             # Rebuild all PNGs via Chrome headless
└── PLAY_STORE_CHECKLIST.md
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
