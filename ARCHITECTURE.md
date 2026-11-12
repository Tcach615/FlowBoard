# FlowBoard — Architecture

FlowBoard is a native Android input method (`InputMethodService`) that replaces letter hunting
with **Adaptive Dual-Chord Input (ADCI)**: two thumbs, four diagonal directions each, sixteen
chords, one candidate per chord.

## Interaction model

- Two circular pads. The left thumb only ever owns the left pad, the right thumb the right pad.
  Role is decided by the zone a pointer lands in, never by the order Android reports pointers.
- Each pad has four directions: `NW`, `NE`, `SW`, `SE`. Straight horizontal/vertical movement is
  ambiguous between two directions, so it is reported as *no direction* rather than guessed.
- A chord is `Chord(left, right)`; there are exactly 16 (`Chord.ALL`).
- Candidates are assigned to chords by the `ChordBoard`. The left pad shows the four candidates
  for `(direction, anchorRight)`, the right pad the four for `(anchorLeft, direction)`. The anchor
  is the other thumb's current (or last committed) direction, so all sixteen chords are reachable
  and visible without a 16-cell grid.
- The user commits by lifting a thumb once both directions are known. Nothing is inserted by
  prediction alone.

## Modules

```
app/src/main/java/com/flowboard/keyboard/
├── gesture/      Direction, Chord, ZoneLayout, GestureEngine   (pure Kotlin, JVM tested)
├── decode/       ChordBoard   (stable candidate → chord assignment, pure Kotlin)
├── predict/      TextContext, Vocabulary, VocabularyParser, PredictionEngine (pure Kotlin)
├── input/        TextTarget, InputController                  (pure Kotlin over an interface)
├── model/        Candidate, CandidateKind
├── ime/          FlowBoardImeService, InputConnectionTarget   (Android)
├── ui/           KeyboardView, ZoneRenderer, ChordPadView     (Android, Canvas)
├── settings/     FlowBoardPreferences, SettingsActivity
└── onboarding/   OnboardingActivity
```

Everything with logic worth testing is Android-free and covered by `app/src/test`. Only the
IME wiring, Canvas rendering and Activities touch the framework.

## Data flow

```
MotionEvent → GestureEngine → Chord → ChordBoard.candidateFor(chord)
                                          │
                    InputController.insert(candidate) → InputConnectionTarget → editor
                                          │
                    TextContext (committed words + current prefix)
                                          │
                    PredictionEngine.predict(context) → ranked candidates
                                          │
                    ChordBoard.update(candidates) → next mapping (stable slots)
```

`KeyboardView` only draws `snapshot` (gesture state), the current mapping and the composing
token. The service owns every engine instance and decides what is inserted.

## Gesture state machine

`IDLE → TRACKING → ARMED → COMMITTED`, with `REJECTED` for invalid gestures.

- Directions are captured on first reach and stay captured, so fingers may move at different speeds.
- `syncWindowMs` (default 240 ms, configurable 80–600) is how long a half-finished chord waits for
  the second direction. If it never arrives the gesture is rejected — never guessed.
- After a commit the engine locks until every pointer is released, which prevents duplicate
  insertions when a thumb lingers.
- A third finger is ignored; a pointer leaving its pad cancels the gesture; `ACTION_CANCEL`
  rejects it.

## Prediction

Frequency + bigram + prefix model over a bundled JSON vocabulary, with an explicit scoring function:

```
score = contextW · P(candidate | previous word)
      + prefixW  · prefixMatch
      + personalW · personalizationBoost
      − interactionW · interactionCost
```

Defaults: `context=0.9, prefix=1.2, personal=1.5, interaction=0.10` (`ScoreWeights`).
Bigrams back off to unigrams, so unseen pairs still score sensibly. Personalization is a
session-scoped count of chosen candidates, used only when enabled and never in password fields.

This is **not** a language model and is not described as one. The `PredictionEngine` API is the
seam where a better model can be dropped in.

## Candidate stability

`ChordBoard.update` first keeps any candidate that is still in the ranked list on its previous
chord, then fills the freed slots in rank order. One bad prediction therefore does not move every
candidate, which is what makes chords learnable.

## Editing and recovery

- `⌫` deletes a character from the composing token, or one character from the editor.
- `↺` undo: deletes exactly what the last insertion committed (recorded in the undo entry) and
  restores the composing token and linguistic context.
- `⇄` alternatives: same rollback, but keeps the current chord mapping so the user can pick another
  chord instead of re-learning new positions.
- `✓` completes the current token without adding a space.
- `␣` commits the token with a space. Spaces are never inserted implicitly by a candidate.

Undo of already-committed text deleted by `⌫` is not supported (the deleted character is not
recoverable through `InputConnection`); this is documented as a limitation.

## Privacy

- No `INTERNET` permission, no analytics SDK, no network calls anywhere in the codebase.
- Typed text stays in memory (`TextContext`, max 32 words) and is cleared on editor change and
  when a password field is detected.
- Password/sensitive fields (`TYPE_TEXT_VARIATION_PASSWORD`, `..._WEB_PASSWORD`,
  `TYPE_NUMBER_VARIATION_PASSWORD`, `TYPE_TEXT_FLAG_NO_SUGGESTIONS`) use a stateless predictor,
  record no statistics and clear the context.
- Only settings and a chord/correction counter are persisted, in the app's private storage.

## Build and tooling

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17, `compileSdk`/`targetSdk` 35, `minSdk` 24.
- `.github/workflows/build-apk.yml` builds the debug APK on push to `main` and on demand.
- `.devcontainer/` pins the same JDK/SDK for local and Codespaces builds.

## Known limitations

- No emulator or physical device was available: `/dev/kvm` exists but is not accessible to the
  build user, so an accelerated emulator cannot run here. IME activation, real `InputConnection`
  insertion into other apps and on-screen rendering are **not** device-verified, and no screenshots
  of the running keyboard exist. Registration is verified at framework level with Robolectric. See
  `IMPLEMENTATION_PLAN.md` for exactly what was and was not tested.
- Only an English vocabulary is bundled (~110 words, 12 fragments, 35 bigrams). It is a starter
  dataset, not a general-purpose lexicon.
- The prediction model has no morphological or syllable segmentation: "syllable" candidates come
  from the bundled fragment list.
- Personalization is per keyboard session; it is not persisted across process restarts.
