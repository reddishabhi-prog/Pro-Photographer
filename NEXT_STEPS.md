# Next steps — the work queue

Do these in order. Each one unblocks the next. Stop and report after each
numbered block rather than running to the end silently.

---

## 0. Make it compile — before anything else

`:app` has never been compiled. Nothing below is worth attempting until it
builds.

```
gradlew.bat :intelligence:test
gradlew.bat :app:assembleDebug
```

**Expect compile errors.** The ViewModel, CameraController and the Compose
layer were written without a compiler. Fix them one at a time; do not refactor
while fixing.

**Done when:** both commands pass and `app/build/outputs/apk/debug/` has an APK.

---

## 1. Get it onto the phone

```
adb devices
gradlew.bat installDebug
adb logcat -s AIPhotographer:V AndroidRuntime:E
```

**Done when:** the app opens, the permission screen appears, and allowing the
camera gives a live full-screen preview (Stage 1, steps 1–2 of the test plan).

If it crashes on launch, the stack trace in logcat is the whole answer. Work
from it, not from guesswork.

---

## 2. Stage 1, step 4 — is the camera actually reporting?

Cover the lens, uncover it. ISO in the top readout must climb sharply and fall
back.

**This is the single most important check in the project.** Exposure is derived
from real Camera2 capture results via EV100. If the capture callback is not
firing, every exposure decision downstream is running on a provisional default
and the app is guessing — which is the one thing it was built not to do.

**Done when:** ISO visibly tracks the light. If it does not, fix
`CameraController` / the Camera2 interop capture callback before moving on.

---

## 3. Stage 3, step 11 — the accelerometer sign

Tilt the phone clockwise ~15°. The hint must read **"Straighten up — tilt
left"**.

If it says "tilt left" when you tilted left, the sign is inverted in
`TiltSensor`. One-line fix. Everything about the horizon and the level
indicator is wrong until this is confirmed.

Also check the centre level line: red and tilted when off level, green dot when
level.

**Done when:** both tilt directions give the correct instruction.

---

## 4. Stage 3, step 17 — can it go quiet?

Frame a genuinely good portrait: subject off-centre, eyes high in the frame,
decent light. The hints must clear, "Looks good — take it" must appear, and the
crosshair must turn green.

If you cannot make it go quiet on a shot Abhinav considers good, **the
thresholds in `CompositionAnalyzer` are too strict and his judgement is the
specification.** Loosen them, add a test fixture capturing the frame he
approved, and re-run `:intelligence:test`.

**Done when:** a good shot produces silence, and a bad shot still produces the
right complaint.

---

## 5. The rest of the test plan

Work through `docs/ON_DEVICE_TEST_PLAN.md` Stages 2, 4, 5, 6, 7 — scene
recognition, pose outlines, capture and enhancement, PRO mode, robustness.

Two judgement calls there need Abhinav's eye, not a test:

- **Step 28 / 30** — is the enhancement too strong? Especially skin: faces must
  not look crunchy or over-sharpened.
- **Step 39** — locking the shutter must leave ISO free to move. If locking one
  control freezes the others, PRO mode is pointless.

**Done when:** every stage either passes or has a logged, understood failure.

---

## 6. Push to GitHub and turn on CI

The private `ai-photographer` repo exists and is empty.
`.github/workflows/android.yml` is already in place and runs both test suites
and uploads a debug APK on every push.

```
git init && git add -A && git commit -m "AI Photographer: initial import"
git remote add origin <the private repo URL>
git push -u origin main
```

**Why it is worth the ten minutes:** Abhinav gets a downloadable APK from his
phone after every change, without opening Android Studio. It shortens the test
loop more than any code change on this list.

---

## 7. RAW capture — the one unbuilt spec item

The app detects whether the sensor supports RAW but the toggle is inert. It was
left honest rather than faked.

CameraX has no DNG output path, so doing it properly means a parallel Camera2
session writing DNG via `DngCreator`, running alongside the CameraX preview.
That is a real piece of work, not a flag.

**Do not start this until blocks 0–5 are done.** A RAW file from an app whose
exposure engine is unverified is worth nothing.

---

## Deferred — not now

- **iOS.** The point of the `:intelligence` module is that it ports via Kotlin
  Multiplatform. Do not start until the Android app is validated on hardware.
- **Paid tier.** Decision stands: free for now, structured so billing can be
  added later. Do not build billing scaffolding.
- **Play Store release.** Needs a signing key, store listing and a privacy
  policy. The no-INTERNET-permission guarantee is the strongest line in that
  listing — worth leading with.
