# START HERE — Abhinav

This package is the complete AI Photographer project. Everything needed is
inside: source code, tests, architecture docs, the on-device test plan, the CI
workflow, the working instructions for Claude Code, and the prompt to paste.

---

## Step 1 — Put the folder somewhere permanent

Unzip it. Move the `ai-photographer` folder to:

```
C:\Users\gaura\projects\ai-photographer
```

If a folder is already there from the earlier transfer, **delete it first** and
replace it with this one. This package is newer and complete — the old one was
missing the CI workflow.

Do not work out of Downloads. Gradle creates large build folders and you will
lose track of which copy is real.

## Step 2 — Open Claude Code in that folder

Open a terminal (PowerShell or Command Prompt) and run:

```
cd C:\Users\gaura\projects\ai-photographer
claude
```

## Step 3 — Paste the prompt

Open `PROMPT.txt`, copy the whole thing, paste it into Claude Code, press Enter.

That is it. It will read the project, initialise git, compile, and work through
the queue — stopping to report at each block.

---

## What you will need ready

| | |
|---|---|
| **Your Android phone** | USB cable, USB debugging switched on (Settings → Developer options). Block 1 onwards needs it. |
| **About 10 minutes of patience** | The very first Gradle run downloads dependencies. 5–8 minutes of apparent nothing. Normal. |
| **Your eyes, at Blocks 3 and 4** | Two checks only you can make: does the tilt hint point the right way, and can you get the app to stop complaining about a shot you think is good. Your judgement is the specification there. |

## What is in the box

```
ai-photographer/
├── START_HERE.md          ← you are reading this
├── PROMPT.txt             ← copy this into Claude Code
├── CLAUDE.md              ← Claude Code reads this automatically, every session
├── NEXT_STEPS.md          ← the ordered work queue, 8 blocks
├── README.md              ← what the product is
├── docs/
│   ├── ARCHITECTURE.md        why the code is shaped this way
│   └── ON_DEVICE_TEST_PLAN.md the 49-step phone checklist
├── intelligence/          the photography brain — pure Kotlin, 119 tests
├── app/                   Android layer — CameraX, ML Kit, Compose UI
└── .github/workflows/     CI: runs tests, builds an APK on every push
```

## The honest status

- **The photography brain works.** 119 unit tests pass — exposure maths checked
  against the sunny-16 rule, composition scoring, scene classification, pose
  geometry, the enhancement pipeline.
- **The Android half has never run.** It was written in a cloud workspace with
  no Android SDK and no camera. Expect compile errors on the first build. That
  is the known starting point, not a failure.

Claude Code can compile, install to your phone and read crash logs. That is the
whole reason for moving there.
