# On-device test plan

The code has never run on a phone. This is the shortest path from "it builds"
to "it works", in the order that finds the most breakage soonest.

Use a real phone. The emulator's fake camera will pass tests that mean nothing.

**How to report back:** the step number, what you saw, and a screenshot or
photo if it is visual. "Step 4, the pose outline is off to the left and half
off screen" is enough to fix something. "The overlay looks wrong" is not.

---

## Stage 1 — Does it start? (5 minutes)

| # | Do this | Expect |
|---|---|---|
| 1 | Install and open the app | Permission screen explaining why the camera is needed |
| 2 | Allow camera | Live preview, full screen, no black bars |
| 3 | Look at the top of the screen | A scene chip (e.g. `AUTO`, `PORTRAIT`) and an ISO / SHUTTER readout |
| 4 | Cover the lens with your hand, then uncover it | ISO should climb sharply when covered and fall when uncovered |

**If step 4 fails**, the Camera2 capture callback is not reporting, and every
exposure decision downstream is running on the provisional default. Tell me —
nothing else is worth testing until it works.

---

## Stage 2 — Does it understand the frame? (10 minutes)

| # | Do this | Expect |
|---|---|---|
| 5 | Point at a person, head and shoulders | Scene chip reads `PORTRAIT` or `CLOSE-UP` |
| 6 | Have them step back so you see their whole body | Chip changes to `FULL BODY` |
| 7 | Point at three or more people | Chip reads `GROUP`, hint about closing gaps and staggering heights |
| 8 | Point at a plate of food | Chip reads `FOOD`, hint suggesting a flat-lay |
| 9 | Point at a page of text held flat | Chip reads `DOCUMENT` |
| 10 | Point out of a window at a view | Chip reads `LANDSCAPE` |

Scene labels come from a general-purpose model, so expect misses on unusual
subjects. Note which ones are wrong and what they should have been.

---

## Stage 3 — Is the advice right? (15 minutes) — the important stage

| # | Do this | Expect |
|---|---|---|
| 11 | **Tilt the phone clockwise about 15°** | Hint: "Straighten up — tilt **left**" |
| 12 | Tilt it anticlockwise instead | Hint should now say "tilt **right**" |
| 13 | Point at a person with a bright window behind them | Hint: light is behind them; the face should visibly brighten |
| 14 | Frame someone with a lot of empty space above their head | Hint: too much space above the head |
| 15 | Frame someone full-body with their feet cut off at the bottom | Hint: step back, feet or hands cut off |
| 16 | Frame a person tiny in the middle of the frame | Hint: get closer |
| 17 | Frame a well-composed portrait: off-centre, eyes high, good light | Hints go quiet, "Looks good — take it" appears, crosshair turns green |
| 18 | Walk someone briskly across the frame | Shutter in the readout should jump to 1/500 or faster |
| 19 | Go into a dim room | `LOW LIGHT` chip, warning about darkness, ISO in the thousands |

**Step 11 is the one most likely to be wrong.** If it says "tilt left" when you
have tilted it left, the accelerometer sign is inverted — tell me and it is a
one-line fix in `TiltSensor`. Also check the level line in the centre: it
should turn red and tilt when off level, and become a green dot when level.

**Step 17 matters as much as the failures.** An app that always finds something
to complain about is unusable. If you cannot get it to go quiet on a genuinely
good shot, the thresholds are too strict.

---

## Stage 4 — The pose guide (10 minutes)

| # | Do this | Expect |
|---|---|---|
| 20 | Point at one person, full body | A dashed amber outline of a standing pose, roughly where they should stand |
| 21 | Look at the outline's position and size | It should sit off-centre, be upright, and fit on screen |
| 22 | Tap the refresh icon | A different pose outline |
| 23 | Watch the faint white skeleton | It should track the actual person's limbs |
| 24 | Switch to the front camera | The outline should mirror with the preview, not point the wrong way |
| 25 | Point at a group | No skeleton outline — words only |

Things to look for: an outline floating off screen, drawn upside down, or
wildly the wrong size. The geometry is unit-tested but has never been seen.

---

## Stage 5 — Taking photographs (10 minutes)

| # | Do this | Expect |
|---|---|---|
| 26 | Press the shutter | Spinner, then a short line saying what the enhancement did |
| 27 | Open the system gallery | Two files in `Pictures/AI Photographer`: the photo and `..._original` |
| 28 | Compare them | The enhanced one should be *slightly* better — not obviously processed |
| 29 | Photograph a backlit face | The enhanced version should show the face much more clearly |
| 30 | Photograph a person's face up close | Skin should **not** look crunchy or over-sharpened |
| 31 | Photograph a landscape | Should look punchier than the original |
| 32 | Check the photo's details in the gallery | ISO and shutter speed recorded |
| 33 | Turn the phone sideways and take a photo | Saved the right way up, not rotated |
| 34 | Take ten photos in a row quickly | No crash, no out-of-memory |

**Step 28 and step 30 are judgement calls, and your opinion is the
specification.** If the enhancement is too strong, say so and which part —
contrast, colour, or sharpening — and I will pull it back.

---

## Stage 6 — Pro mode (10 minutes)

| # | Do this | Expect |
|---|---|---|
| 35 | Tap PRO | Control chips appear: ISO, SHUTTER, FOCUS, WB, EV, ZOOM |
| 36 | Note which read `n/a` | Those are unsupported by your hardware — expected, tell me which |
| 37 | Tap ISO, pick the highest value | Preview should visibly brighten and get noisier |
| 38 | Tap SHUTTER, pick a fast speed | Preview should visibly darken |
| 39 | Lock the shutter but leave ISO on AUTO | ISO should move on its own as you point at light and dark |
| 40 | Tap WB, pick BULB | Preview should go noticeably blue |
| 41 | Drag the ZOOM slider | Smooth zoom, ratio readout updating |
| 42 | Tap FOCUS and drag it | Focus should visibly change |
| 43 | Switch back to AUTO | All controls should return to automatic |

**Step 39 is the point of Pro mode** — locking one control must not surrender
the others. If locking the shutter also freezes the ISO, that is a bug.

---

## Stage 7 — Robustness (5 minutes)

| # | Do this | Expect |
|---|---|---|
| 44 | Switch front/back repeatedly, fast | No crash, preview always comes back |
| 45 | Background the app and return | Preview resumes |
| 46 | Take a photo, then immediately background the app | Photo still saves |
| 47 | Toggle HDR if available | Preview rebinds and comes back |
| 48 | Deny camera permission, reopen | Explanation screen with a route to Settings |
| 49 | Leave it running 5 minutes on one scene | No obvious heat or battery collapse; hints still updating |

---

## Also worth telling me

- **Anything that flickers.** The stabilizer should prevent it; if advice
  flashes between two states, the threshold needs raising.
- **Anything slow.** Specifically: lag between moving the phone and the hints
  catching up, and how long the shutter takes to free up after a shot.
- **Advice you disagree with.** You know what a good photograph looks like. If
  the app says something a photographer would not, that is a bug in the
  engine's judgement, and the judgement is all in one testable place.
- **Your phone model and Android version**, so I know what the hardware
  reported.
