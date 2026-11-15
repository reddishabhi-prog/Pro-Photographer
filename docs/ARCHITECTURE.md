# Architecture

## The one decision everything follows from

A camera app cannot be tested without a camera — so the camera was pushed as
far out of the way as possible.

Every judgement about a photograph lives in `:intelligence`, a plain Kotlin
module with no Android dependency at all. The Android module is glue: it gets
frames from the camera, hands them to the engine, and draws the answer.

```
┌──────────────────────────────── :app (Android) ────────────────────────────────┐
│                                                                               │
│  MainActivity ──► CameraScreen (Compose)                                      │
│                     │                                                         │
│                     ├─ PreviewView (live feed)                                │
│                     ├─ GuidanceOverlay  (grid, pose outline, level, target)   │
│                     ├─ HintStrip        (the coaching, in words)              │
│                     ├─ TopReadout       (scene, light, ISO, shutter)          │
│                     └─ ProControls      (manual dials)                        │
│                     │                                                         │
│                 CameraViewModel ◄── state ── GuidanceStabilizer               │
│                     │                                                         │
│       ┌─────────────┼───────────────┬──────────────────┐                      │
│       ▼             ▼               ▼                  ▼                      │
│  CameraController  FrameAnalyzer  TiltSensor      PhotoProcessor              │
│  (CameraX +        (per frame)    (accelerometer) (enhance + save)            │
│   Camera2 interop)      │                                                     │
│       │                 ├─ MlKitDetectors (pose, face, labels)                │
│       │                 └─ LumaSampler    (brightness and colour)             │
│       │                                                                       │
└───────┼───────────────────────────────────────────────────────────────────────┘
        │  FrameInput                                    Guidance
        ▼                                                   ▲
┌───────────────────────── :intelligence (pure Kotlin) ──────┼──────────────────┐
│                                                            │                  │
│                        PhotoIntelligenceEngine ────────────┘                  │
│                                 │                                            │
│   ┌──────────────┬──────────────┼──────────────┬──────────────┐               │
│   ▼              ▼              ▼              ▼              ▼               │
│ SceneClassifier LightingAnalyzer CompositionAnalyzer AngleAdvisor HintBuilder  │
│                        │                                                      │
│                  SettingsAdvisor ──► ExposureMath                             │
│                                                                               │
│   PoseLibrary   PersonEstimator   SubjectMotionTracker   GuidanceStabilizer    │
│   imaging/LumaSampler   imaging/ImageEnhancer                                 │
│   overlay/PoseOverlayGeometry    camera/ManualRanges                          │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## The frame loop

Runs on a single analysis thread, roughly ten to fifteen times a second.

1. **CameraX** hands over a YUV frame at 640×480. Small on purpose: the vision
   models downscale anyway, and every extra pixel is paid for on every frame.
2. **MlKitDetectors** runs pose and face detection. Scene labelling runs on
   every tenth frame only — what you are photographing changes slowly. Each
   model call has a 400 ms timeout so one slow frame cannot stall the
   viewfinder.
3. **LumaSampler** reads the Y plane into a 6×6 map of regional brightness, a
   histogram, clipping counts, and average colour from the chroma planes. It
   samples one pixel in sixteen and rotates into upright coordinates, so "the
   top of the frame" means what the user sees at the top.
4. **PersonEstimator** assembles the people. The pose model tracks one person,
   so the main subject gets a real landmark-derived body box and everyone else
   gets one estimated from their face.
5. **SubjectMotionTracker** compares the subject's centre with the previous
   frame to decide whether to freeze the action.
6. **PhotoIntelligenceEngine** turns all of that into a `Guidance`.
7. **GuidanceStabilizer** holds new advice for three consecutive frames before
   showing it, so the screen does not flicker — except critical warnings, which
   pass straight through.
8. The ViewModel publishes state, and pushes settings to the camera on a 400 ms
   timer rather than every frame.

---

## Why exposure is derived, not guessed

The engine never judges brightness from pixel values, because auto-exposure has
already normalised those away: a dim room and a bright beach both come back as
mid-grey.

Instead, a Camera2 capture callback on the preview reports the ISO and shutter
speed auto-exposure actually chose, and the scene's absolute light level comes
out of the standard relationship:

```
EV₁₀₀ = log₂(N² / t) − log₂(ISO / 100)
```

Bright sun lands near 14, a lit room near 5, a night street near 1. That is a
real measurement, and it is what makes the recommendations meaningful rather
than decorative. `ExposureMathTest` checks it against the sunny-16 rule.

Settings are then chosen in the order a photographer uses:

1. What shutter speed does this subject demand? (Portrait 1/125, pet 1/250,
   moving subject 1/500, landscape as slow as handholding allows.)
2. What ISO exposes correctly at that speed?
3. If that ISO is punishing, give the shutter speed back down toward the
   handheld floor and try again.

---

## AUTO and PRO

The boundary is enforced in one place, `CameraController.applyRecommendation`:

- **AUTO** — the engine's recommendation is applied to the hardware.
- **PRO, nothing locked** — the user's exposure compensation is respected, the
  engine still advises.
- **PRO, something locked** — locked values win. Unlocked controls are filled
  from the engine's recommendation, because Camera2 requires that switching
  auto-exposure off supplies *both* ISO and shutter; leaving one unset is
  undefined behaviour that looks to the user like a broken slider.

`ManualRanges` builds every dial from what the sensor reports, so a control is
never offered that the hardware will silently ignore.

---

## Capture and enhancement

1. JPEG off the sensor, decoded and rotated upright using the device's physical
   orientation, so a photo taken with the phone sideways is saved the right way
   up even though the UI stays portrait.
2. If enhancing, **the original is written first** — once the pixels are
   processed in place, the original is gone.
3. `ImageEnhancer.measure` reads the real file: the viewfinder statistics
   describe a 640×480 preview, and levels for a 12-megapixel photograph have to
   come from the photograph.
4. `planFor` chooses amounts from the scene. Landscapes get clarity and
   saturation; faces get very little of either; night gets almost none, because
   sharpening noise only makes more noise; golden hour is never white balanced
   back to grey.
5. One pass applies white balance, the tone curve and vibrance; a second does
   local contrast via an unsharp mask, damped inside detected face boxes.
6. EXIF records the real ISO, shutter, aperture and focal length.

Restraint is the governing principle: a photograph that looks obviously
processed is worse than one left alone.

---

## Testing

```bash
./gradlew :intelligence:test
```

119 tests. They are written as photographic situations with answers a working
photographer would agree with — "backlit indoor portrait is caught and
corrected", "night photographs are not sharpened into noise", "a terrible frame
never floods the viewfinder with advice" — rather than as assertions about
implementation details.

The ones that earn their keep most:

- `ExposureMathTest` — the maths against known real-world values.
- `PhotoIntelligenceEngineTest` — thirteen complete scenes, end to end.
- `ImageEnhancerTest` — tone curve monotonicity, which if broken would band
  every sky the app ever photographs.
- `LumaSamplerTest` — row padding, pixel stride and rotation, the three things
  that differ between phone models and silently corrupt everything downstream.
- `GuidanceStabilizerTest` — that a one-frame detection wobble never reaches
  the user, and that an urgent warning never waits.

## Deliberate non-choices

**No dependency injection framework.** The object graph is small; constructor
arguments are clearer than annotations, and it is one less annotation processor
to break a build.

**No `INTERNET` permission.** Not a setting — an absence. The app cannot upload
a photograph even if a future bug tried to.

**No Hilt, no Room, no network stack, no analytics.** Nothing is in the
dependency list that a photograph does not need.
