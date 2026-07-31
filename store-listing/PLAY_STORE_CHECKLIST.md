# Google Play publishing checklist — Simple MP3

## Assets in this folder

| Path | Spec | Use |
|------|------|-----|
| `icons/ic_launcher-512.png` | 512×512 PNG | **Hi-res icon** in Play Console |
| `icons/ic_launcher-512-round.png` | 512×512 | Optional / adaptive preview |
| `graphics/feature-graphic-1024x500.png` | 1024×500 | **Feature graphic** (required) |
| `screenshots/01-home.png` | 1080×1920 | Phone screenshot 1 |
| `screenshots/02-playlists.png` | 1080×1920 | Phone screenshot 2 |
| `screenshots/03-nowplaying.png` | 1080×1920 | Phone screenshot 3 |
| `screenshots/04-android-auto.png` | 1080×1920 | Phone screenshot 4 |
| `screenshots/05-jellyfin.png` | 1080×1920 | Phone screenshot 5 |
| `listing/*` | text | Titles & descriptions |
| `listing/privacy-policy.html` | HTML | Host publicly; paste URL in Console |

### Re-render assets
```bash
cd store-listing && chmod +x render.sh && ./render.sh
```

## Play Console steps

1. **Create app** → name `Simple MP3`, free/paid, declarations  
2. **Store listing**
   - Title ← `listing/title.txt` (≤30 chars)
   - Short description ← `listing/short-description.txt` (≤80 chars)
   - Full description ← `listing/full-description.txt` (≤4000)
   - App icon 512 + feature graphic + ≥2 phone screenshots  
3. **Privacy policy** — host `privacy-policy.html` (GitHub Pages, your site, etc.) and add URL  
4. **App content** — ratings questionnaire, target audience, data safety form  
5. **Data safety** (honest answers for this app):
   - Data collected: optionally **none** shared with developer; Jellyfin is user-to-their-server  
   - Data stored on device; not sold  
   - Permissions: music files, notifications, network (optional features)  
6. **Release**
   - Build **signed AAB** (not debug APK)
   - `versionCode` / `versionName` in `app/build.gradle.kts`
   - Internal testing → closed → production  

## Sign a release AAB (Android Studio)

1. **Build → Generate Signed Bundle / APK → Android App Bundle**  
2. Create/use a keystore (back it up offline!)  
3. Release build type  
4. Upload the `.aab` under **Production** or **Testing**

Or configure signing in Gradle (do not commit keystore passwords).

## Data safety short answers (draft)

| Question | Suggested answer |
|----------|------------------|
| Does app collect personal data? | No personal data collected by developer servers |
| Is data shared? | No |
| Is data encrypted in transit? | Yes when using HTTPS Jellyfin URLs |
| Can users request deletion? | Uninstall removes on-device app data |

## Android Auto note

Media apps do not need a separate Auto APK. Ensure store listing mentions Android Auto and that you tested with **Unknown sources** (sideload) or a Play track for production Auto visibility.

## Before you click Publish

- [ ] Replace `support@example.com` and developer name everywhere  
- [ ] Host privacy policy over HTTPS  
- [ ] Signed **release** AAB  
- [ ] Screenshots reviewed on a phone preview  
- [ ] Content rating completed  
- [ ] Target API / policy compliance for current Play requirements  

## Optional next assets

- 7" / 10" tablet screenshots (if you declare tablet support)  
- Promo video (YouTube URL)  
- Localized descriptions (es, de, fr, …)  
