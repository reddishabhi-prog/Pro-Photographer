#!/usr/bin/env bash
# End-to-end check on a real Android device or emulator, over adb.
#
#   scripts/device-smoke-test.sh [path/to/app.apk] [output-dir]
#
# Proves, on hardware rather than in unit tests:
#   0. first run: the intro slides, picking photo types, the welcome tip
#   1. the app installs, opens and survives 15 s of live analysis
#   2. pressing the shutter writes a JPEG to Pictures/ShotMuse
#   3. a phone turned clockwise is told to rotate LEFT (emulator only:
#      it needs `adb emu` to fake the accelerometer)
# Screenshots, the saved photos and logcat land in the output directory.
set -uo pipefail

APK="${1:-app/build/outputs/apk/debug/app-x86_64-debug.apk}"
OUT="${2:-device-test}"
PKG="com.aiphotographer.debug"
ALBUM="/sdcard/Pictures/ShotMuse"
mkdir -p "$OUT"
FAIL=0
pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; FAIL=1; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

# Text and buttons Compose exposes to accessibility, one dump per call.
ui_dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml; }

# Tap the element whose accessibility label is exactly $1. Fails if absent.
tap_desc() {
  local b
  b=$(ui_dump | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1)
  [ -z "$b" ] && return 1
  read -r X1 Y1 X2 Y2 <<<"$(echo "$b" | tr -c '0-9' ' ')"
  adb shell input tap $(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 ))
}

adb wait-for-device
adb shell rm -rf "\"$ALBUM\"" 2>/dev/null
adb logcat -c
adb install -r -g "$APK" >/dev/null && pass "install" || { fail "install"; exit 1; }
# Start as a brand-new user so the first-run flow is tested too. Only this
# debug build's own data is wiped.
adb shell pm clear "$PKG" >/dev/null
adb shell pm grant "$PKG" android.permission.CAMERA 2>/dev/null

# Level and upright, so the tilt step starts from a known state.
adb emu sensor set acceleration 0:9.81:0 >/dev/null 2>&1 || true

adb shell am start -W -n "$PKG/com.aiphotographer.MainActivity" >/dev/null
sleep 4

# --- First run ---
ONB=1
for n in 1 2 3; do
  shot "00-intro-$n"
  tap_desc "Next" || { ONB=0; break; }
  sleep 1.5
done
shot 00-intro-4-pick
tap_desc "Food" && tap_desc "People" || ONB=0
sleep 1
shot 00-intro-4-picked
tap_desc "Start shooting" || ONB=0
if [ "$ONB" -eq 1 ]; then pass "intro slides and photo-type picks"; else fail "could not walk the intro"; fi
sleep 5
TIP=$(ui_dump | grep -o 'text="[^"]*"' | tr '\n' ' ')
if echo "$TIP" | grep -q 'For food'; then pass "welcome tip follows first pick (food)"
else fail "welcome tip missing: $TIP"; fi

sleep 11
shot 01-viewfinder
if adb shell pidof "$PKG" >/dev/null; then pass "app running after 15 s"; else fail "app died on launch"; fi

# --- Live settings panel ---
HUD=$(ui_dump | grep -o 'text="[^"]*"' | tr '\n' ' ')
if echo "$HUD" | grep -q 'text="ISO"' && echo "$HUD" | grep -q 'text="SHUTTER"'; then
  pass "live settings panel shows ISO and shutter"
else
  fail "live settings panel missing: $HUD"
fi

# --- Poses: open the library, pick one, see it suggested ---
if tap_desc "Pose ideas"; then
  sleep 2; shot 01b-pose-library
  if tap_desc "Weight on the back foot"; then
    sleep 2; shot 01c-pose-picked
    if ui_dump | grep -q 'Your pose · Weight on the back foot'; then pass "pose picked from the library is suggested"
    else fail "picked pose not shown"; fi
  else
    fail "pose library did not open"
  fi
else
  fail "no 'Pose ideas' button"
fi

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
