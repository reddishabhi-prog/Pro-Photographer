# AI Photographer

An Android camera that behaves like a photographer standing next to you: it
reads the scene, tells you what is wrong with the shot while you can still fix
it, sets the camera the way a photographer would, and finishes the photograph
afterwards.

Everything runs on the phone. There is no `INTERNET` permission in the
manifest, so the app physically cannot upload a photograph.

---

## Status: builds, not yet device-tested

Be clear-eyed about what this is right now.

| Part | State |
|---|---|
| Photography intelligence (scene, light, composition, poses, exposure) | **Written and tested** — 119 unit tests, all passing |
| Image enhancement maths | **Written and tested** |
| Frame sampling, overlay geometry, manual-control clamping | **Written and tested** |
| Camera layer, ML Kit wiring, Compose UI | **Written, never run** |
| Behaviour on a real camera | **Unverified** |

The pure-Kotlin core was developed against a reference implementation and has
a real test suite. The Android layer above it has never been executed, because
it was built in an environment with no Android SDK and no camera. It needs a
first run on a device, and `docs/ON_DEVICE_TEST_PLAN.md` is the checklist for
that.

---

## Build and run

```bash
# In Android Studio: File > Open > this folder, then Run.
# Or from the command line:
./gradlew :app:assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Requirements: Android Studio (Ladybug or newer), JDK 17, a device running
Android 8.0 (API 26) or later. **Use a real phone, not the emulator** — the
whole app is about what the camera sees.

Run the tests, which need no device:

```bash
./gradlew :intelligence:test
```

---

## How it is put together

Two modules, split on a deliberate line.

```
intelligence/   pure Kotlin, no Android. Every decision about a photograph.
app/            Android. Camera, ML models, screen. As thin as it can be.
```

Everything that could be made testable was pushed down into `intelligence`:
scene classification, lighting analysis, composition scoring, the pose
library, exposure maths, frame sampling, the enhancement pipeline, overlay
geometry, and the clamping of manual controls to what the hardware allows.
That is why there is a real test suite for a camera app written without a
camera.

`docs/ARCHITECTURE.md` goes through it properly.

---

## What it does

**Reads the scene.** Portrait, close-up, full body, group, landscape,
architecture, food, pet, night, document. People take priority over everything
else: if someone is in the frame, this is a picture of them.

**Reads the light.** Backlit, harsh sun, flat, golden hour, low light — and
which direction it is coming from. Backlight is the single most common way an
indoor portrait is ruined, so it is called out first and loudest.

**Judges the frame.** Rule of thirds, headroom, clean crops, a level horizon,
looking room for a subject facing out of frame, and whether the subject is
large enough to be the subject at all.

**Coaches, briefly.** Never more than three hints at once, most urgent first,
in plain words: *"Step back — feet or hands are cut off"*, not *"subject
bounding box exceeds frame bounds"*. Advice has to hold steady for several
frames before it appears, so the screen does not flicker — except urgent
warnings, which appear at once.

**Shows a pose to copy.** A dashed outline of where to stand and what shape to
make, with the subject's live skeleton drawn faintly underneath so they can see
how far they have to move. Group shots get words instead of an outline, because
a skeleton drawn over four people hides the thing being composed.

**Sets the camera like a photographer.** It derives the scene's true brightness
from what auto-exposure chose, picks the shutter speed the subject demands,
works out the ISO that exposes correctly at that speed, then trades shutter
back down if the ISO would wreck the image. Verified against the sunny-16 rule
and real indoor and night values.

**Finishes the photograph.** Auto levels, shadow lift, highlight recovery,
white balance, vibrance weighted towards the duller colours, and local contrast
held back on faces so skin does not turn crunchy. The untouched original is
saved alongside, always.

### Two modes

**AUTO** — the app decides and applies everything.

**PRO** — manual ISO, shutter, focus, white balance, exposure compensation and
zoom. Two rules hold: no control is offered that the hardware cannot actually
do, and locking one control does not surrender the others. Lock the shutter
speed and the app will still choose your ISO. Advice keeps coming, but never
moves a control you have taken charge of.

---

## Known limitations

Written down rather than discovered later.

1. **RAW capture is not implemented.** The app detects whether the sensor
   supports RAW and reports it, but CameraX's `ImageCapture` has no DNG output
   path, so the toggle is inert. Doing it properly means a parallel Camera2
   session — real work, deliberately not faked.
2. **Pose landmarks are single-person.** The on-device pose model tracks one
   person. Everyone else in a group has their body estimated from their face
   using human proportions (`PersonEstimator`). Group *composition* advice is
   sound; group *pose outlines* are not offered.
3. **The horizon roll sign is unverified.** Which way "tilt left" points
   depends on the accelerometer's axes in practice. It is one sign flip in
   `TiltSensor` if it reads backwards, and the test plan checks it first.
4. **Enhancement is CPU-bound.** A 12-megapixel photograph takes roughly one to
   three seconds, and briefly needs about 100 MB. `largeHeap` is on, and
   running out of memory falls back to saving the unenhanced photograph rather
   than crashing.
5. **HDR depends on the vendor.** It goes through CameraX Extensions, which
   many phones do not implement. When unavailable the control is disabled
   rather than silently doing nothing.
6. **Portrait-locked.** The UI is fixed in portrait; photographs are saved
   with the correct orientation from the device sensor, so turning the phone
   sideways gives a correctly-oriented landscape photo with a portrait UI.
7. **No gallery browser yet.** Photographs go to `Pictures/AI Photographer`
   and the system gallery shows them.

---

## Licence

Not yet chosen.
