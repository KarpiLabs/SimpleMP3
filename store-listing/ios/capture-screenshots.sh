#!/usr/bin/env bash
# Build Simple MP3, seed demo data, and capture real Simulator screenshots
# for App Store (iPhone + iPad).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$ROOT/ios"
OUT="$ROOT/store-listing/ios"
RAW_IPHONE="$OUT/raw/iphone"
RAW_IPAD="$OUT/raw/ipad"
BUNDLE_ID="io.karpilabs.Simple-MP3"
SCHEME="Simple MP3"
PROJECT="$IOS/Simple MP3.xcodeproj"

IPHONE_NAME="iPhone 17 Pro Max"
IPAD_NAME="iPad Pro 13-inch (M5)"

# Marketing set (matches Android story + iOS CarPlay)
SCENES=(home playlists nowplaying drive carplay jellyfin)

log() { printf '→ %s\n' "$*"; }

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 1; }
}

require xcodebuild
require xcrun
require python3

mkdir -p "$RAW_IPHONE" "$RAW_IPAD"

udid_for() {
  local name="$1"
  # Prefer booted match, else any available device with this name.
  xcrun simctl list devices available | python3 -c "
import sys
name = sys.argv[1]
lines = sys.stdin.read().splitlines()
boot = None
anyd = None
for line in lines:
    if name in line and '(' in line and ')' in line:
        # e.g. iPhone 17 Pro Max (UUID) (Shutdown)
        parts = line.strip()
        import re
        m = re.search(r'\(([0-9A-Fa-f-]{36})\)', parts)
        if not m:
            continue
        udid = m.group(1)
        if 'Booted' in parts:
            boot = udid
        anyd = udid
print(boot or anyd or '')
" "$name"
}

boot_device() {
  local udid="$1"
  local state
  state="$(xcrun simctl list devices | grep "$udid" | head -1 || true)"
  if echo "$state" | grep -q Booted; then
    log "Already booted $udid"
  else
    log "Booting $udid"
    xcrun simctl boot "$udid" || true
    xcrun simctl bootstatus "$udid" -b
  fi
  # Ensure Simulator.app is open so status bar / chrome look correct
  open -a Simulator --args -CurrentDeviceUDID "$udid" >/dev/null 2>&1 || true
}

build_for() {
  local udid="$1"
  local dest="platform=iOS Simulator,id=$udid"
  log "Building for $udid"
  xcodebuild \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration Debug \
    -destination "$dest" \
    -derivedDataPath "${DERIVED_DATA:-/tmp/SimpleMP3-DD}" \
    build \
    CODE_SIGNING_ALLOWED=NO \
    2>&1 | python3 -c '
import sys
for line in sys.stdin:
    if any(k in line for k in ("error:", "warning:", "BUILD SUCCEEDED", "BUILD FAILED", "** ")):
        sys.stdout.write(line)
sys.stdout.flush()
'
}

app_path() {
  find "${DERIVED_DATA:-/tmp/SimpleMP3-DD}/Build/Products" -name "Simple MP3.app" -type d | head -1
}

install_and_launch() {
  local udid="$1"
  local scene="$2"
  local app
  app="$(app_path)"
  if [[ -z "$app" || ! -d "$app" ]]; then
    echo "App not found after build" >&2
    exit 1
  fi
  log "Install $app → $udid"
  xcrun simctl uninstall "$udid" "$BUNDLE_ID" >/dev/null 2>&1 || true
  xcrun simctl install "$udid" "$app"
  # Fresh defaults / dark mode
  xcrun simctl spawn "$udid" defaults write "$BUNDLE_ID" AppleLanguages -array en
  log "Launch scene=$scene"
  xcrun simctl launch "$udid" "$BUNDLE_ID" \
    -ScreenshotDemo \
    -ScreenshotScene "$scene" \
    >/dev/null
}

wait_for_ui() {
  local seconds="${1:-3.5}"
  sleep "$seconds"
}

capture() {
  local udid="$1"
  local out="$2"
  log "Screenshot → $out"
  xcrun simctl io "$udid" screenshot --type=png "$out"
  # Normalize PNG (strip alpha issues for App Store)
  sips -s format png "$out" --out "$out" >/dev/null
}

terminate() {
  local udid="$1"
  xcrun simctl terminate "$udid" "$BUNDLE_ID" >/dev/null 2>&1 || true
}

capture_device() {
  local name="$1"
  local outdir="$2"
  local udid
  udid="$(udid_for "$name")"
  if [[ -z "$udid" ]]; then
    echo "No simulator found for: $name" >&2
    exit 1
  fi
  log "Device: $name ($udid)"
  boot_device "$udid"
  build_for "$udid"
  mkdir -p "$outdir"

  local i=1
  for scene in "${SCENES[@]}"; do
    terminate "$udid"
    install_and_launch "$udid" "$scene"
    # Splash + seed + cover presentation (iPad is slower)
    local wait=3.5
    if [[ "$name" == *iPad* ]]; then wait=7.5; fi
    if [[ "$scene" == "nowplaying" || "$scene" == "jellyfin" || "$scene" == "carplay" ]]; then
      wait=$(python3 -c "print($wait + 1.5)")
    fi
    wait_for_ui "$wait"
    local num
    num="$(printf '%02d' "$i")"
    capture "$udid" "$outdir/${num}-${scene}.png"
    terminate "$udid"
    i=$((i + 1))
  done
}

log "Capturing iPhone screenshots ($IPHONE_NAME)"
capture_device "$IPHONE_NAME" "$RAW_IPHONE"

log "Capturing iPad screenshots ($IPAD_NAME)"
capture_device "$IPAD_NAME" "$RAW_IPAD"

log "Raw captures written:"
ls -la "$RAW_IPHONE" "$RAW_IPAD"
echo ""
echo "Next: store-listing/ios/render.sh  # marketing frames"
