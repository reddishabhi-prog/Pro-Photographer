# AI Photographer — working instructions

Premium Android camera app. Reads the live scene, coaches the shot in plain
language, sets exposure like a photographer would, draws pose outlines, and
enhances the photo after capture. All on-device. Android first, iOS later.

Product Owner: Abhinav. He is not a developer — explain in plain English,
lead with the answer, and never hand back a task list where a result was asked
for.

---

## Read these before changing anything

| File | What it tells you |
|---|---|
| `docs/ARCHITECTURE.md` | Why the code is shaped this way. Read the first section at minimum. |
| `docs/ON_DEVICE_TEST_PLAN.md` | The 49-step checklist that validates a build on a real phone. |
| `NEXT_STEPS.md` | The prioritised work queue. Start here. |

---

## Build and test (Windows)

Run from the project root. Use `gradlew.bat`, not `./gradlew`.

```
gradlew.bat :intelligence:test            # 119 unit tests, the photography brain. Fast. Run this constantly.
gradlew.bat :app:testDebugUnitTest        # app-layer unit tests
gradlew.bat :app:assembleDebug            # builds the APK
gradlew.bat installDebug                  # builds and pushes to the connected phone
```

Phone must have USB debugging on. Check it is seen:

```
adb devices
adb logcat -s AIPhotographer:V AndroidRuntime:E    # app logs + crashes
```

First Gradle run downloads dependencies and takes 5–8 minutes. That is normal,
not a hang.

JDK 17. compileSdk 35, minSdk 26, targetSdk 35. AGP 8.7.3, Kotlin 2.0.21.

---

## The one rule that matters

**All photographic judgement lives in `:intelligence`, a pure Kotlin module
with no Android dependency.** `:app` is thin glue over CameraX and ML Kit.

This exists so the judgement can be unit-tested without a camera, and so it
ports to iOS via Kotlin Multiplatform almost unchanged. It is the most
valuable asset in the repo.

So:

- **Never import an Android class into `:intelligence`.** No `android.*`, no
  `androidx.*`, no `Bitmap`, no ML Kit types. If you need one, define a plain
  data class in `:intelligence` and convert at the `:app` boundary.
- **Any new judgement goes in `:intelligence`, with tests, in the same change.**
  A decision about photographs that lives in a Composable or a ViewModel is in
  the wrong place.
- `:app` may contain: camera plumbing, sensor reading, Compose UI, file I/O.
  Nothing that decides what makes a good photograph.

## Other invariants — do not break these without asking Abhinav

1. **No `INTERNET` permission in the manifest.** Ever. The privacy guarantee is
   that the app physically cannot upload a photograph. It is a product feature,
   not an oversight. Any library needing network access is rejected.
2. **The original photo is always kept.** Enhancement is non-destructive; both
   files are saved to `Pictures/AI Photographer`.
3. **PRO mode never overrides a control the user has locked**, and never offers
   a control the hardware does not report. Locking one control must leave the
   others on auto.
4. **At most three hints on screen**, most urgent first, stabilised against
   detector jitter via `GuidanceStabilizer`.
5. **The app must be able to go quiet.** On a genuinely good frame the hints
   clear and "Looks good — take it" appears. An app that always finds fault is
   unusable.

---

## What is verified and what is not

**Verified — 119 passing unit tests in `:intelligence`:** scene classification,
lighting analysis, composition scoring, the exposure maths (checked against the
sunny-16 rule and real indoor/night values), the pose library, frame sampling,
the enhancement pipeline, overlay geometry, control clamping.

**Never executed on hardware:** the whole `:app` module. Camera binding, ML Kit
wiring, the accelerometer, Compose UI, file saving. It was written in a cloud
workspace with no Android SDK and no camera.

Treat `:app` as unproven code. Expect the first real build to surface compile
errors and runtime crashes, and fix them from actual logcat output rather than
reasoning about what might be wrong.

---

## How to work

- **Compile before claiming anything works.** The previous workspace could not,
  which is why `:app` is in this state. You can. Run the build.
- **Say plainly what you have and have not run.** Never describe untested code
  as working.
- When fixing a device bug, reproduce it from logcat or a screenshot first, fix
  the root cause in the right module, add a test if the bug was a judgement
  error, then rebuild and tell Abhinav what to re-check.
- Comments explain *why*, not *what*. Match the existing voice in
  `:intelligence` — direct, photographic reasoning, no ceremony.
- Keep commits focused. Conventional prefixes (`fix:`, `feat:`, `test:`).

## Git

Single commit of history (`d102617`) was created in the cloud workspace and did
not survive the transfer; this folder is files only. Initialise once:

```
git init && git add -A && git commit -m "AI Photographer: initial import"
```

`.github/workflows/android.yml` is in place and runs both test suites and
uploads a debug APK on every push. It needs a GitHub remote to be useful — the
private `ai-photographer` repo is empty and unpushed. Worth doing: it gives
Abhinav a downloadable APK without touching Android Studio.
