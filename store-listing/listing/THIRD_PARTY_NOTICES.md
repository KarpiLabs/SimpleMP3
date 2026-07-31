# Third-party notices

## FFmpeg / FFmpegKit

This app bundles **FFmpeg** via **FFmpegKit** (Android package
`com.moizhassan.ffmpeg:ffmpeg-kit-16kb`, a 16 KB page-size rebuild of the
retired [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit) project).

FFmpeg and FFmpegKit are licensed under the
[GNU Lesser General Public License v3.0 (LGPL-3.0)](https://www.gnu.org/licenses/lgpl-3.0.txt)
(and, for some optional codecs, related terms in upstream FFmpeg).

- Source for the packaging used here: https://github.com/moizhassankh/ffmpeg-kit-android-16KB  
- Upstream FFmpeg: https://ffmpeg.org/  
- Original FFmpegKit: https://github.com/arthenica/ffmpeg-kit  

LGPL compliance notes for this app:

- Native libraries ship as separate shared objects (`.so`) inside the APK.
- Object/source availability follows the LGPL for the versions we redistribute;
  contact the app author if you need the corresponding sources for the bundled build.

**MP3 patents** have expired in major jurisdictions; using libmp3lame for personal
encoding is generally fine. That is separate from **YouTube’s Terms of Service**,
which still restrict downloading content you do not own or have rights to.
Use the YouTube → MP3 feature only for content you are allowed to copy.

## NewPipeExtractor

Used to resolve YouTube stream URLs and metadata. See
[TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
for license (GPL-3.0 with linking exceptions as published by NewPipe).
