# FlowBoard
exploring a new way to type

FlowBoard is a native Android keyboard implementing **Adaptive Dual-Chord Input (ADCI)**.
Two thumbs, four directions each, sixteen chords, one contextual candidate per chord. Nothing is
inserted unless you commit a chord, and nothing leaves the device.

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). A pinned container is
in `.devcontainer/`; CI builds the same way.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

`testDebugUnitTest` also runs the headless end-to-end suite (`HeadlessTypingTest`,
`HeadlessScreensTest`), which drives the real IME service and views with synthetic two-thumb
gestures and writes rendered screens to `build/reports/headless/`. What still needs a real device
is listed in [MANUAL_QA.md](MANUAL_QA.md).

On demand from GitHub Actions:

```bash
gh workflow run build-apk.yml --ref main
gh run watch "$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status
gh run download "$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')" -D dist
```

## Design documents

The proposed stenographic input language and its prediction layer are specified (not implemented) in
`docs/`:

- [`docs/stenographic_research.md`](docs/stenographic_research.md) — stenotype/Plover theory, English
  phonology, chording and disambiguation evidence, with sources
- [`docs/flowboard_language_design.md`](docs/flowboard_language_design.md) — the 16-chord inventory,
  composition rules, briefs, dictionary and ambiguity model
- [`docs/input_language_examples.md`](docs/input_language_examples.md) — 41 generated, consistent
  stroke sequences
- [`docs/context_prediction_architecture.md`](docs/context_prediction_architecture.md) — ranking,
  algorithms and fallbacks
- [`docs/architecture_proposal.md`](docs/architecture_proposal.md) — components, interfaces and
  migration phases
- [`docs/evaluation_plan.md`](docs/evaluation_plan.md) — metrics, estimates and study design
- [`docs/decision_record.md`](docs/decision_record.md) — the recommendation and its open questions

## Install and enable

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then on the device:

1. Settings → System → Languages & input → On-screen keyboard → Manage keyboards → enable
   **FlowBoard**.
2. Tap a text field and switch the input method to FlowBoard (keyboard switcher in the navigation
   bar), or use **Choose input method** in FlowBoard's settings.
3. Open the app's launcher entry to replay the tutorial at any time.

## Using it

- Place one thumb on each pad, drag both to a corner, lift one thumb to commit.
- Left pad tiles are the candidates for the right thumb's current direction, and vice versa, so
  all sixteen chords stay visible: the corners are `NW NE SW SE` on both pads.
- Toolbar: `⌫` backspace · `␣` space · `✓` finish word · `↺` undo · `⇄` alternatives.
- A chord that never gets both directions inside the sync window is rejected, not guessed.

## Repository layout

- `app/src/main/java/com/flowboard/keyboard/gesture` — direction/chord model and the two-finger engine
- `.../predict` — local vocabulary, context tracking and the ranking engine
- `.../decode` — candidate → chord assignment
- `.../input` — composing/undo controller
- `.../ime`, `.../ui`, `.../settings`, `.../onboarding` — Android keyboard, rendering, settings
- `app/src/test` — JVM unit tests for every engine above
- `.github/workflows/build-apk.yml`, `.devcontainer/` — reproducible builds
- `ARCHITECTURE.md`, `IMPLEMENTATION_PLAN.md` — design and verified/unverified status

## Privacy

No `INTERNET` permission, no analytics, no cloud service. Settings and a typing counter are stored
locally; password fields are excluded from prediction history and statistics.
