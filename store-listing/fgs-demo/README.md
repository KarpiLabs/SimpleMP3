# Foreground service demo video — Media Playback

Recorded on the Pixel emulator (`emulator-5554`, Pixel-class AOSP image).

## Files

| File | Use |
|------|-----|
| `fgs_media_playback_demo.mp4` | Clean screen recording (no overlays) |
| `fgs_media_playback_demo_captioned.mp4` | Same recording with step labels for reviewers |

**Recommended for Play Console:** the **captioned** file.

## What the video shows

1. Open Simple MP3 with a scanned local library  
2. User taps a track → playback starts (Media3 `PlaybackService` / `mediaPlayback` FGS)  
3. User leaves the app (Home) while audio continues  
4. Notification shade shows the **media session notification** (pause / seek) — perceptible FGS use  
5. User returns to full Now Playing UI  

## Play Console steps

1. Upload an MP4 to **YouTube as Unlisted** (Play needs a URL it can open)  
2. **Play Console → App content → Foreground service permissions**  
3. Select **Media playback**  
4. Paste the video URL  
5. Suggested description:

> Simple MP3 is a local music player. When the user starts playback, PlaybackService (Media3 MediaLibraryService) runs as a foreground service with type mediaPlayback so audio continues while the app is backgrounded or used with Android Auto. The user sees a system media notification with transport controls. The service stops when playback ends and the session is no longer needed.

## Technical

- Resolution: 720×1280  
- Duration: ~50s  
- Package: `io.karpilabs.simplemp3`  
- Service: `.service.PlaybackService` · `foregroundServiceType="mediaPlayback"`  
