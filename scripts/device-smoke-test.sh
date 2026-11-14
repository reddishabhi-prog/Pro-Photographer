#!/usr/bin/env bash
# End-to-end check on a real Android device or emulator, over adb.
#
#   scripts/device-smoke-test.sh [path/to/app.apk] [output-dir]
#
# Proves, on hardware rather than in unit tests:
#   1. the app installs, opens and survives 15 s of live analysis
#   2. pressing the shutter writes a JPEG to Pictures/AI Photographer
#   3. a phone turned clockwise is told to rotate LEFT (emulator only:
#      it needs `adb emu` to fake the accelerometer)
# Screenshots, the saved photos and logcat land in the output directory.
set -uo pipefail

APK="${1:-app/build/outputs/apk/debug/app-x86_64-debug.apk}"
OUT="${2:-device-test}"
PKG="com.aiphotographer.debug"
ALBUM="/sdcard/Pictures/AI Photographer"
mkdir -p "$OUT"
FAIL=0
pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; FAIL=1; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

# Text and buttons Compose exposes to accessibility, one dump per call.
ui_dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml; }

adb wait-for-device
adb shell rm -rf "\"$ALBUM\"" 2>/dev/null
adb logcat -c
adb install -r -g "$APK" >/dev/null && pass "install" || { fail "install"; exit 1; }

# Level and upright, so the tilt step starts from a known state.
adb emu sensor set acceleration 0:9.81:0 >/dev/null 2>&1 || true

adb shell am start -W -n "$PKG/com.aiphotographer.MainActivity" >/dev/null
sleep 15
shot 01-viewfinder
if adb shell pidof "$PKG" >/dev/null; then pass "app running after 15 s"; else fail "app died on launch"; fi

# --- Shutter ---
BOUNDS=$(ui_dump | grep -o 'content-desc="Take photo"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1)
if [ -z "$BOUNDS" ]; then
  fail "shutter button not found on screen"
else
  read -r X1 Y1 X2 Y2 <<<"$(echo "$BOUNDS" | tr -c '0-9' ' ')"
  adb shell input tap $(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 ))
  sleep 10
  shot 02-after-shutter
  COUNT=$(adb shell ls "\"$ALBUM\"" 2>/dev/null | grep -c '\.jpg' || true)
  if [ "${COUNT:-0}" -ge 1 ]; then
    pass "shutter saved $COUNT photo file(s)"
    adb shell ls "\"$ALBUM\"" | tr -d '\r' | grep '\.jpg' | while read -r f; do
      adb pull "$ALBUM/$f" "$OUT/$f" >/dev/null
    done
  else
    fail "shutter pressed but no photo in $ALBUM"
  fi
fi

# --- Tilt direction (emulator only) ---
# Phone turned 12 degrees clockwise: "up" swings to the screen's left edge.
if adb emu sensor set acceleration -2.04:9.60:0 >/dev/null 2>&1; then
  sleep 4
  shot 03-tilted-clockwise
  TEXT=$(ui_dump | grep -o 'text="[^"]*"' | tr '\n' ' ')
  echo "      on screen: $TEXT"
  if echo "$TEXT" | grep -q 'rotate it left'; then pass "clockwise tilt says: rotate it left"
  else fail "clockwise tilt did not say 'rotate it left'"; fi
  adb emu sensor set acceleration 0:9.81:0 >/dev/null 2>&1
  sleep 4
  shot 04-level-again
else
  echo "SKIP  tilt check (not an emulator)"
fi

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  fail "crash in logcat:"; grep -A12 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -30
else
  pass "no crashes in logcat"
fi
grep -E "CameraController|PhotoProcessor|FrameAnalyzer" "$OUT/logcat.txt" | grep -E " [EW] " | head -20

[ "$FAIL" -eq 0 ] && echo "ALL DEVICE CHECKS PASSED" || echo "DEVICE CHECKS FAILED"
exit "$FAIL"
