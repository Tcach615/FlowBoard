# FlowBoard — Implementation Plan and Status

Status legend: **done and verified** · **implemented, not device-verified** · **not started**

## Phase 0 — Repository audit — done

At the start of this work the repository contained only `README.md` plus the APK build
infrastructure added earlier (`.github/workflows/build-apk.yml`, `.devcontainer/`, and the
`build-apk` skill). There was no Android project, no Gradle wrapper and no app module.

Decision: keep that infrastructure (it now builds this project) and create the Android project
around it: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/`.

Assumptions made while doing this:

- Native Kotlin + Android SDK, as requested; no cross-platform layer.
- `compileSdk`/`targetSdk` 35, `minSdk` 24 (covers Android 7.0+, keeps the IME API surface modern).
- No AndroidX/Compose/Room. The app needs no support library, and the dependency set stays small:
  JUnit and `org.json` for tests only.
- Language of the UI and vocabulary: English.

## Phase 1 — Functional Android IME — implemented, not device-verified

- `FlowBoardImeService` (`InputMethodService`) declared with `android.view.InputMethod` filter,
  `BIND_INPUT_METHOD` permission and `res/xml/method.xml` subtype, so Android lists it as a keyboard.
- Text insertion via `InputConnection` through `InputConnectionTarget`; composing region used for
  the current token; backspace and space implemented.
- `onCreateInputView`, `onStartInput`, `onStartInputView`, `onFinishInputView`,
  `onUpdateSelection`, `onEvaluateFullscreenMode` handled.
- Registration is verified at framework level by `ImeRegistrationTest` (Robolectric): Android's
  `PackageManager` resolves the service for `android.view.InputMethod`, sees the
  `BIND_INPUT_METHOD` permission and the `android.view.im` metadata, and the service instantiates
  and returns a `KeyboardView` from `onCreateInputView`.
- **Not verified:** enabling the IME, selecting it, and typing into another app require a device or
  emulator. This environment has no usable emulator: `/dev/kvm` exists but is not accessible to the
  build user, and software emulation is not viable here.

## Phase 2 — Dual-finger gesture prototype — done and verified (engine)

- `GestureEngine`: zone-based finger roles, independent pointer tracking, sticky direction capture,
  sync window, rejection states, commit lock, extra-finger and out-of-zone handling.
- Practice surface: `ChordPadView` + onboarding step 2 shows the detected chord live.
- Unit tests cover all 16 chords, reversed pointer order, mid-gesture timing, window expiry,
  duplicate-commit prevention, third finger, out-of-zone and cancellation.

## Phase 3 — Candidate mapping — done and verified

- `ChordBoard` maps ranked candidates to the 16 chords with slot stability.
- Tests cover full/partial fills, stability, duplicate collapse, and one-candidate-per-chord.

## Phase 4 — Contextual prediction — done and verified (engine)

- `Vocabulary` (frequency + bigram), `VocabularyParser`, `TextContext`, `PredictionEngine` with
  explicit weights and personalization hook.
- Tests cover prefix matching, context re-ranking, unknown prefix, empty vocabulary, determinism,
  personalization promotion and the limit.
- Numbers: candidate generation is a handful of map lookups; no benchmark was recorded because
  there is no device to measure on. Do not treat "fast" as measured.

## Phase 5 — Editing and recovery — done and verified (engine)

- Backspace, undo (rolls back exactly what was committed), replace-with-alternative, complete
  token, space, punctuation, clear composing on editor change.
- Tests cover each path, including undo after a commit and after a plain insertion.

## Phase 6 — Onboarding, settings, polish — implemented, not device-verified

- Onboarding: welcome → all four directions per thumb → all sixteen chords → enable/select
  keyboard. Practice uses the real engine.
- Settings: activation buttons, sync window, dead zone, candidate labels, light theme, haptics,
  language placeholder, personalization, privacy text, typing statistics, tutorial replay.
- Rendering: two pads with 4 candidate tiles each, live anchoring between pads, composing preview,
  status/rejection messages, private-mode indicator.

## Phase 7 — Validation and delivery — partially done

Verified here:

- `./gradlew clean testDebugUnitTest assembleDebug lintDebug` succeeds; all tests pass
  (50 JVM engine tests + 2 Robolectric IME registration tests) and lint reports no errors.
- `aapt2 dump badging` on the produced APK: package, version, SDK levels.
- APK manifest inspected for the IME service, permission, intent filter and subtype.

Not verified here (no device, no usable emulator):

- Enabling FlowBoard in Settings → Languages & input → Keyboards.
- Selecting it as the current input method and typing in another app.
- Password-field behaviour end-to-end.
- Screenshots of the running keyboard.
- Gesture latency and duplicate-selection rates on hardware.

## Next phase

1. Install the debug APK on a device/emulator and complete the Phase 7 device checks.
2. Add instrumentation tests for the IME service (`InputMethodService` lifecycle, editor switches).
3. Expand the vocabulary and add real syllable data; then measure candidate latency on device.
4. Persist personalization counts and expose a "clear personal data" action.
