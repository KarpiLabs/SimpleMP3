#!/usr/bin/env bash
# Render Play Store marketing assets from HTML using Chrome headless.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
HTML="$ROOT/html"
SHOTS="$ROOT/screenshots"
GFX="$ROOT/graphics"
ICONS="$ROOT/icons"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

if [[ ! -x "$CHROME" ]]; then
  echo "Google Chrome not found at $CHROME" >&2
  exit 1
fi

mkdir -p "$SHOTS" "$GFX" "$ICONS"

render() {
  local html_file="$1"
  local out="$2"
  local w="$3"
  local h="$4"
  local abs
  abs="$(cd "$(dirname "$html_file")" && pwd)/$(basename "$html_file")"
  echo "→ $out (${w}x${h})"
  "$CHROME" \
    --headless=new \
    --disable-gpu \
    --hide-scrollbars \
    --force-device-scale-factor=1 \
    --default-background-color=00000000 \
    --window-size="${w},${h}" \
    --screenshot="$out" \
    "file://${abs}" \
    2>/dev/null
}

# Phone screenshots — Play Store friendly portrait
render "$HTML/screenshot-01-home.html"       "$SHOTS/01-home.png"       1080 1920
render "$HTML/screenshot-02-playlists.html"  "$SHOTS/02-playlists.png"  1080 1920
render "$HTML/screenshot-03-nowplaying.html" "$SHOTS/03-nowplaying.png" 1080 1920
render "$HTML/screenshot-04-auto.html"       "$SHOTS/04-android-auto.png" 1080 1920
render "$HTML/screenshot-05-jellyfin.html"   "$SHOTS/05-jellyfin.png"   1080 1920

# Feature graphic (required for Play Store)
render "$HTML/feature-graphic.html" "$GFX/feature-graphic-1024x500.png" 1024 500

# Hi-res icon (Play Console upload)
render "$HTML/icon-512.html"       "$ICONS/ic_launcher-512.png"       512 512
render "$HTML/icon-512-round.html" "$ICONS/ic_launcher-512-round.png" 512 512

echo ""
echo "Done. Assets written under store-listing/"
ls -la "$SHOTS" "$GFX" "$ICONS"
