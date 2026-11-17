# FlowBoard

A native Android keyboard that writes English with a stenographic alphabet — two thumbs, sixteen
chords, one syllable core per chord.

Version 1.1.0 implements **FSL-1**, the language specified in `docs/`. The keyboard is offline,
needs no network permission, and inserts nothing until you commit a translation.

```text
Left thumb = onset family        Right thumb = vowel family
NW LAB (p b m f v)               NW F  (ɪ ɛ æ)
NE COR (t d n s z θ ð)           NE A  (ə ʌ ɑ ɔ ɜ)
SW DOR (k g ŋ w h)               SW U  (u ʊ oʊ aʊ)
SE SIB (ʃ tʃ dʒ ʒ j, zero onset) SE ∅  (bare consonant: coda or cluster)

COR·A                → the
DOR·F  COR·∅         → cat      (k ɪ + t)
COR·∅ COR·∅ COR·F COR·∅ → street
```

Stroke several chords to spell a word; the dictionary's readings appear above the pads and you pick
one. Ambiguity is normal (FSL-1 codes phoneme *classes*), so the board shows up to four ordered
readings, `⇄` cycles through the rest, and prediction only *suggests* the next chord with a ring.
Nothing is auto-committed and no chord is ever remapped or hidden.

## Status

| item | state |
| --- | --- |
| FSL-1 language, dictionary, translator | implemented (`app/src/main/java/com/flowboard/keyboard/steno`) |
| Contextual prediction (highlight-only) | implemented, offline |
| APK | `dist/flowboard-1.1.0-debug.apk` (`com.flowboard.keyboard` 1.1.0, minSdk 24, targetSdk 35) |
| Automated tests | 103 passing (`./gradlew testDebugUnitTest`) |
| Physical device | **not tested** — see `docs/implementation_report.md` §12 and `MANUAL_QA.md` |

Details: [`docs/implementation_report.md`](docs/implementation_report.md) (what was built,
measurements, limitations) and [`docs/implementation_readiness.md`](docs/implementation_readiness.md)
(how the open design questions were resolved).

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). Python 3 is only needed
to regenerate the bundled models.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`; the shipped copy is
`dist/flowboard-1.1.0-debug.apk`.

`testDebugUnitTest` runs the language, dictionary, prediction and held-out evaluation tests plus the
headless end-to-end suite (`HeadlessTypingTest`), which drives the real IME service and views with
synthetic two-thumb gestures and writes into a real `EditText` through an `InputConnection`.
Rendered screens go to `build/reports/headless/`.

Regenerating the models (downloads CMUdict, Norvig `count_1w` and five Gutenberg novels into
`tools/cache/`; *Sherlock Holmes* is deliberately held out for evaluation):

```bash
python3 tools/build_assets.py --download
```

On demand from GitHub Actions:

```bash
gh workflow run build-apk.yml --ref main
gh run watch "$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status
gh run download "$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')" -D dist
```

## Install and enable

```bash
adb install -r dist/flowboard-1.1.0-debug.apk
```

Then on the device:

1. Settings → System → Languages & input → On-screen keyboard → Manage keyboards → enable
   **FlowBoard**.
2. Tap a text field and switch the input method to FlowBoard (keyboard switcher in the navigation
   bar), or use **Choose input method** in FlowBoard's settings.
3. Open the app's launcher entry to replay the tutorial at any time.

## Using it

- Place one thumb on each pad, drag both to a corner, lift either thumb to commit the chord. A chord
  without a direction on both hands, or one that misses the sync window, is rejected rather than
  guessed.
- The pads always show the alphabet, never candidates. Turn labels off under *Alphabet labels on the
  pads* once the layout is memorised.
- The bar above the pads shows the sketch you are building and the top reading; the strip shows up to
  four readings. Tap a chip to commit it.
- Toolbar: `⌫` backspace · `␣` commit + space · `✓` commit without space · `↺` undo · `⇄` next reading.
- Rings around a tile mark chords the model expects next (*Highlight likely next chords*). They are a
  hint; every chord stays available.

Editing behaves in linguistic terms: `⌫` removes the last stroke before it deletes committed text,
`↺` removes the last committed word *and restores its strokes* so you can pick another reading, and
changing the cursor away from a half-written sketch discards it.

## Repository layout

- `app/src/main/java/com/flowboard/keyboard/steno` — FSL-1 alphabet, engine, dictionary, language
  model, predictor, translation, session
- `.../gesture` — direction/chord model and the two-finger engine (unchanged from 1.0)
- `.../ui`, `.../ime`, `.../settings`, `.../onboarding` — presentation, IME service, settings
- `.../input`, `.../predict`, `.../decode` — the 1.0 fragment pipeline, kept for its tests and
  fallbacks; the IME itself runs on `steno/`
- `app/src/main/assets` — `steno_en.tsvz` (20 000-word lexicon) and `lm_en.tsvz` (trigram model)
- `app/src/test` — language, prediction, evaluation and headless end-to-end tests
- `tools/build_assets.py` — model generation

## Design documents

The language and prediction design, now implemented, with the implementation records alongside:

- [`docs/flowboard_language_design.md`](docs/flowboard_language_design.md) — the 16-chord inventory,
  composition rules, briefs, dictionary and ambiguity model
- [`docs/context_prediction_architecture.md`](docs/context_prediction_architecture.md) — ranking,
  algorithms and fallbacks
- [`docs/input_language_examples.md`](docs/input_language_examples.md) — generated stroke sequences,
  all asserted in tests
- [`docs/architecture_proposal.md`](docs/architecture_proposal.md) — components, interfaces, migration
- [`docs/evaluation_plan.md`](docs/evaluation_plan.md) — metrics and study design
- [`docs/decision_record.md`](docs/decision_record.md) — the recommendation and open questions
- [`docs/stenographic_research.md`](docs/stenographic_research.md) — the evidence base
- [`docs/implementation_readiness.md`](docs/implementation_readiness.md) — resolutions of the open
  questions
- [`docs/implementation_report.md`](docs/implementation_report.md) — what was built, measured and left
  undone

## Privacy

No `INTERNET` permission, no analytics, no cloud or LLM service — the APK declares no permissions at
all. The dictionary and language model ship inside the app. Settings and a typing counter are stored
locally; password fields keep no context and are excluded from statistics and personalisation.
